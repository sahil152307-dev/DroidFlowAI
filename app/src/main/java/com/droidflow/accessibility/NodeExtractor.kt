package com.droidflow.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.droidflow.models.UIElement
import com.droidflow.models.UIState

/**
 * A captured screen: the compact UIState (what the planner sees) plus the
 * live node references (what the executor acts on), valid for this session.
 * Nodes are never persisted — every action cycle re-snapshots.
 */
class ScreenSnapshot(
    val state: UIState,
    val nodes: Map<String, AccessibilityNodeInfo>,
    val capturedAtMs: Long,
    val isSelf: Boolean
)

/**
 * Converts the raw accessibility hierarchy into the compact internal
 * representation of TRD §6, so the planner never sees platform objects.
 *
 * Only "interesting" nodes are kept (clickable / editable / checkable /
 * text- or description-bearing), bounded by MAX_NODES to keep prompts small.
 */
object NodeExtractor {

    private const val MAX_NODES = 140
    private const val MAX_DEPTH = 30

    fun extract(service: AccessibilityService): ScreenSnapshot? {
        val root = freshRoot(service) ?: return null
        val pkg = root.packageName?.toString() ?: ""
        val elements = ArrayList<UIElement>(64)
        val nodes = HashMap<String, AccessibilityNodeInfo>(64)

        var count = 0

        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (count >= MAX_NODES || depth > MAX_DEPTH) return
            val hint = getHint(node)
            val interesting = node.isClickable || node.isEditable || node.isCheckable ||
                !node.text.isNullOrBlank() ||
                !node.contentDescription.isNullOrBlank() ||
                !hint.isNullOrBlank()
            if (interesting) {
                val id = "node_%02d".format(++count)
                elements.add(toElement(id, node))
                nodes[id] = node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { walk(it, depth + 1) }
            }
        }

        walk(root, 0)

        val screen = ScreenStateDetector.guess(pkg, elements)
        val state = UIState(
            packageName = pkg,
            screen = screen,
            elements = elements,
            timestamp = System.currentTimeMillis()
        )
        return ScreenSnapshot(
            state = state,
            nodes = nodes,
            capturedAtMs = System.currentTimeMillis(),
            isSelf = pkg == service.packageName
        )
    }

    // ---------- helpers ----------

    private fun getHint(node: AccessibilityNodeInfo): CharSequence? {
        return if (Build.VERSION.SDK_INT >= 28) node.hintText else null
    }

    /**
     * Best available root: prefer the active interactive window (API 30+),
     * fall back to rootInActiveWindow. System surfaces yield null.
     */
    private fun freshRoot(service: AccessibilityService): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= 30) {
            val windows: List<AccessibilityWindowInfo> = service.windows
            val candidate = windows
                .filter { it.root != null && !DroidFlowAccessibilityService.isSystemNoise(it.root.packageName?.toString()) }
                .sortedWith(
                    compareByDescending<AccessibilityWindowInfo> { it.isActive }
                        .thenByDescending { it.isFocused }
                )
                .firstOrNull()
            candidate?.let { return it.root }
        }
        val root = service.rootInActiveWindow ?: return null
        return if (DroidFlowAccessibilityService.isSystemNoise(root.packageName?.toString())) {
            null
        } else {
            root
        }
    }

    private fun toElement(id: String, node: AccessibilityNodeInfo): UIElement {
        val r = Rect()
        node.getBoundsInScreen(r)
        return UIElement(
            id = id,
            type = classify(node),
            text = node.text?.toString()?.take(80),
            contentDescription = node.contentDescription?.toString()?.take(80),
            hint = getHint(node)?.toString()?.take(60),
            clickable = node.isClickable,
            editable = node.isEditable,
            enabled = node.isEnabled,
            bounds = listOf(r.left, r.top, r.right, r.bottom)
        )
    }

    /** Semantic type classification — works for both View and Compose trees. */
    private fun classify(node: AccessibilityNodeInfo): String {
        val cls = node.className?.toString() ?: ""
        return when {
            node.isEditable -> "edit_text"
            node.isCheckable -> if (cls.contains("RadioButton")) "radio" else "checkbox"
            cls.contains("Button") -> "button"
            node.isClickable &&
                (!node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()) -> "button"
            node.isClickable -> "clickable"
            !node.text.isNullOrBlank() -> "text"
            !node.contentDescription.isNullOrBlank() -> "image"
            else -> "container"
        }
    }
}
