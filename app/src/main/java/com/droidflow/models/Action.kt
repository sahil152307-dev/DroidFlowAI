package com.droidflow.models

import kotlinx.serialization.Serializable

/**
 * Standardized agent actions (TRD §13).
 * The LLM planner only ever PROPOSES one of these; the schema validator,
 * target resolver and safety gate decide whether it executes.
 */
@Serializable
enum class ActionType { CLICK, TYPE, SCROLL, SWIPE, BACK, OPEN_APP, WAIT, READ, ASK_USER, STOP }

@Serializable
data class AgentAction(
    val type: ActionType,
    val targetId: String? = null,
    val targetText: String? = null,
    val targetHint: String? = null,
    val text: String? = null,
    val direction: String? = null,
    val packageToOpen: String? = null,
    val durationMs: Long = 0L,
    val reason: String? = null,
    val expectedResult: String? = null,
    val confidence: Double = 1.0
)
