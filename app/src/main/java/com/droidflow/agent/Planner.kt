package com.droidflow.agent

import com.droidflow.accessibility.ScreenSnapshot
import com.droidflow.models.AgentAction
import com.droidflow.models.ExecutionResult

/** Everything a planner needs to decide the next action. */
data class PlanContext(
    val task: com.droidflow.models.Task,
    val memory: Map<String, String>,
    val snap: ScreenSnapshot?,
    val lastAction: AgentAction?,
    val lastResult: ExecutionResult?,
    val stepIndex: Int,
    val history: List<String>
)

/**
 * A planner proposes exactly ONE action per cycle (TRD §12).
 * The engine owns validation, target resolution, the safety gate,
 * execution and verification — the planner only decides.
 */
interface Planner {
    suspend fun nextStep(ctx: PlanContext): AgentAction?
}
