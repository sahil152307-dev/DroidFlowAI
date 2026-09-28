package com.droidflow.execution

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.droidflow.accessibility.DroidFlowAccessibilityService
import kotlinx.coroutines.delay

/** A launchable app known to the agent. */
data class TargetApp(
    val label: String,
    val pkg: String,
    val activity: String? = null
) {
    val isDemo: Boolean get() = activity != null
}

/**
 * App launch + foreground readiness (TRD §16).
 * Only whitelisted apps can be opened; demo apps are launched by explicit
 * component so the correct mock app always comes up.
 */
object AppController {

    val KNOWN = listOf(
        TargetApp("RideNow", "com.droidflow.demoapps", "com.droidflow.demoapps.cab.RideNowActivity"),
        TargetApp("SwiftRide", "com.droidflow.demoapps", "com.droidflow.demoapps.cab.SwiftRideActivity"),
        TargetApp("MockMart", "com.droidflow.demoapps", "com.droidflow.demoapps.mart.MockMartActivity"),
        TargetApp("QuickForm", "com.droidflow.demoapps", "com.droidflow.demoapps.form.QuickFormActivity"),
        // Real apps, used only for the optional read-only bonus demo:
        TargetApp("Uber", "com.ubercab"),
        TargetApp("Ola", "com.olacabs.customer")
    )

    fun resolve(name: String?): TargetApp? {
        if (name.isNullOrBlank()) return null
        val n = name.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        if (n.isEmpty()) return null
        return KNOWN.firstOrNull { it.label.lowercase().replace(Regex("[^a-z0-9]"), "") == n }
            ?: KNOWN.firstOrNull {
                val l = it.label.lowercase().replace(Regex("[^a-z0-9]"), "")
                l.contains(n) || n.contains(l)
            }
    }

    fun labelForPackage(pkg: String?): String? =
        KNOWN.firstOrNull { it.pkg == pkg }?.label

    /** Demo apps share one package — the foreground ACTIVITY identifies them. */
    fun labelForCurrentWindow(): String? {
        val activity = DroidFlowAccessibilityService.currentActivity.value
        KNOWN.firstOrNull { it.activity != null && it.activity == activity }?.let { return it.label }
        return labelForPackage(DroidFlowAccessibilityService.foreground.value)
    }

    /**
     * SINGLE_TOP keeps the existing task state alive when we re-open an app
     * mid-task (e.g. returning to the cheaper cab app) — the demo apps keep
     * their navigation state instead of being recreated.
     */
    fun launchIntent(context: Context, app: TargetApp): Intent? {
        return if (app.activity != null) {
            Intent().apply {
                component = ComponentName(app.pkg, app.activity)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        } else {
            context.packageManager.getLaunchIntentForPackage(app.pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Waits until the target app is actually in the foreground.
     * Demo apps are verified by activity class (they share a package);
     * real apps by package name.
     */
    suspend fun awaitForeground(app: TargetApp, timeoutMs: Long = 6000): Boolean {
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            if (isAppInForeground(app)) return true
            delay(180)
        }
        return isAppInForeground(app)
    }

    fun isAppInForeground(app: TargetApp): Boolean {
        if (app.activity != null) {
            return DroidFlowAccessibilityService.currentActivity.value == app.activity
        }
        return DroidFlowAccessibilityService.foreground.value == app.pkg
    }
}
