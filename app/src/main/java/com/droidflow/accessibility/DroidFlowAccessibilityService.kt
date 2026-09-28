package com.droidflow.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * DroidFlow's primary interaction layer (TRD §5).
 *
 * Phase 2 implementation:
 *  - tracks foreground window changes (TYPE_WINDOW_STATE_CHANGED)
 *  - extracts compact UI snapshots of the active window (NodeExtractor)
 *  - performs node actions: click, set-text, global back/home
 *  - gesture fallback for non-accessibility-friendly targets
 *
 * Safety rules baked in:
 *  - never touches windows flagged as system noise (system UI, IME)
 *  - gesture fallback only when the node's app is actually in the foreground,
 *    so a stale node can never cause a tap on the wrong app
 *  - secure screens simply produce no root node → caller sees "no access"
 */
class DroidFlowAccessibilityService : AccessibilityService() {

    private val gesture = GestureDispatcher(this)

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Required for service.windows (API 30+) — also declared in the xml config.
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        instance = this
        _connected.value = true
        Log.i(TAG, "DroidFlow accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            _foreground.value = pkg
            event.className?.toString()?.let { _activity.value = it }
            _windowEvents.tryEmit(pkg)
        }
        // Content changes are intentionally ignored here: snapshots are pulled
        // on demand so the agent always reasons over fresh state.
    }

    override fun onInterrupt() {
        // No spoken feedback used; nothing to interrupt.
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
            _connected.value = false
        }
    }

    // ---------- Perception API ----------

    /** Fresh snapshot of the active non-system window, or null if unavailable. */
    fun snapshot(): ScreenSnapshot? = NodeExtractor.extract(this)

    // ---------- Action API (the Phase 3 executor wraps these) ----------

    /**
     * Click a node. Prefers performAction(ACTION_CLICK); falls back to a
     * coordinate tap ONLY if the node's own app is foreground — a stale node
     * can never trigger a gesture on a different app's screen.
     */
    fun tapNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        val foreground = _foreground.value
        val nodePkg = node.packageName?.toString()
        if (foreground != null && nodePkg == foreground) {
            val r = Rect()
            node.getBoundsInScreen(r)
            if (r.width() > 2 && r.height() > 2) {
                return gesture.tap(r.exactCenterX(), r.exactCenterY())
            }
        }
        return false
    }

    /** Enter text into an editable node via ACTION_SET_TEXT. */
    fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true
        // Some fields need focus before they accept text.
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Clear an editable field. */
    fun clearText(node: AccessibilityNodeInfo): Boolean = setText(node, "")

    fun globalBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun globalHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    /** Content scrolling (used by SCROLL/SWIPE actions). */
    fun scrollDown(): Boolean = gesture.scrollDown()

    fun scrollUp(): Boolean = gesture.scrollUp()

    companion object {
        private const val TAG = "DroidFlowA11y"

        @Volatile
        var instance: DroidFlowAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected

        private val _foreground = MutableStateFlow<String?>(null)
        val foreground: StateFlow<String?> = _foreground

        /** Foreground activity class (distinguishes the demo apps, which share a package). */
        private val _activity = MutableStateFlow<String?>(null)
        val currentActivity: StateFlow<String?> = _activity

        private val _windowEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
        val windowEvents: SharedFlow<String> = _windowEvents

        /** System surfaces we never observe or act on. */
        fun isSystemNoise(pkg: String?): Boolean {
            if (pkg.isNullOrBlank()) return true
            return pkg == "com.android.systemui" || pkg.contains("inputmethod")
        }
    }
}
