package com.droidflow.models

import kotlinx.serialization.Serializable

/**
 * Compact representation of the current screen (TRD §6).
 * Converted from the raw AccessibilityNodeInfo hierarchy before it
 * reaches the planner, so the model never sees raw platform objects.
 */
@Serializable
data class UIElement(
    val id: String,
    val type: String,
    val text: String? = null,
    val contentDescription: String? = null,
    val hint: String? = null,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val enabled: Boolean = true,
    val bounds: List<Int> = emptyList()
)

@Serializable
data class UIState(
    val packageName: String = "",
    val screen: String = "",
    val elements: List<UIElement> = emptyList(),
    val timestamp: Long = 0L
)
