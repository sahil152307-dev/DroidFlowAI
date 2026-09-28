package com.droidflow.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.droidflow.agent.AgentEngine
import com.droidflow.logging.AgentLogger
import com.droidflow.models.Task
import com.droidflow.models.TaskOutcome

/** Runtime phases surfaced on the Live Agent View. */
enum class AgentPhase { UNDERSTAND, OBSERVE, PLAN, ACT, VERIFY, VERIFIED, SAFETY, DONE }

data class SimFare(val appLabel: String, val itemLabel: String, val price: Int)

data class ExecutionUiState(
    val task: Task? = null,
    val phase: AgentPhase = AgentPhase.UNDERSTAND,
    val detail: String = "",
    val fares: List<SimFare> = emptyList(),
    val awaitingConfirmation: Boolean = false,
    val confirmationTitle: String = "",
    val confirmationBody: String = ""
)

/**
 * Thin facade over the application-scoped AgentEngine.
 *
 * The real automation loop lives in AgentEngine so it survives DroidFlow
 * going to the background while it drives other apps. This view model only
 * forwards the engine's snapshot state (compose state reads in
 * composition re-compose normally) and user interactions.
 */
class AgentViewModel(application: Application) : AndroidViewModel(application) {

    val logger: AgentLogger get() = AgentEngine.logger

    val ui: ExecutionUiState get() = AgentEngine.ui

    val pendingTask: Task? get() = AgentEngine.pendingTask

    val outcome: TaskOutcome? get() = AgentEngine.outcome

    /** Single redirect flag consumed by the NavHost. */
    val finished: Boolean get() = AgentEngine.finished

    /** Called from Home after the intent engine parsed the input. */
    fun prepareTask(task: Task) = AgentEngine.prepareTask(task)

    /** Called from the Task Confirmation screen — starts the REAL loop. */
    fun startTask(task: Task) = AgentEngine.startTask(task, getApplication())

    /** User tapped Confirm on the safety dialog. */
    fun confirmBoundary() = AgentEngine.confirmBoundary()

    /** User tapped Cancel on the safety dialog. */
    fun declineBoundary() = AgentEngine.declineBoundary()

    /** User tapped Cancel task on the execution screen. */
    fun cancelTask() = AgentEngine.cancelTask()

    fun reset() = AgentEngine.reset()
}
