package com.droidflow.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path

/**
 * Gesture dispatch (TRD §17): taps, swipes and scrolls via
 * AccessibilityService#dispatchGesture — the same gesture surface used by
 * switch-access users.
 */
class GestureDispatcher(private val service: AccessibilityService) {

    fun tap(x: Float, y: Float): Boolean = dispatch(x, y, x, y, 60)

    fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 280
    ): Boolean = dispatch(startX, startY, endX, endY, durationMs)

    fun scrollDown(): Boolean {
        val m = service.resources.displayMetrics
        return swipe(
            m.widthPixels / 2f, m.heightPixels * 0.70f,
            m.widthPixels / 2f, m.heightPixels * 0.35f,
            350
        )
    }

    fun scrollUp(): Boolean {
        val m = service.resources.displayMetrics
        return swipe(
            m.widthPixels / 2f, m.heightPixels * 0.35f,
            m.widthPixels / 2f, m.heightPixels * 0.70f,
            350
        )
    }

    private fun dispatch(sx: Float, sy: Float, ex: Float, ey: Float, duration: Long): Boolean {
        return try {
            val path = Path().apply { moveTo(sx, sy); lineTo(ex, ey) }
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        } catch (_: Exception) {
            false
        }
    }
}
