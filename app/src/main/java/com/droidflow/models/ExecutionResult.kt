package com.droidflow.models

import kotlinx.serialization.Serializable

@Serializable
enum class ExecutionStatus { SUCCESS, FAILURE, MISMATCH, TIMEOUT, BLOCKED }

/** TRD §40 failure states — every failure has a human-readable explanation. */
enum class FailureReason {
    UNKNOWN_UI, LOW_CONFIDENCE, TARGET_NOT_FOUND, ACTION_FAILED, STATE_MISMATCH,
    TIMEOUT, SECURE_SCREEN, HIGH_RISK_ACTION, STEP_LIMIT_REACHED, APP_NOT_AVAILABLE, USER_CANCELLED;

    val humanMessage: String
        get() = when (this) {
            UNKNOWN_UI -> "The current screen is not recognized. DroidFlow stopped instead of guessing."
            LOW_CONFIDENCE -> "Confidence is too low to act safely. DroidFlow needs your input."
            TARGET_NOT_FOUND -> "The expected UI element could not be found on this screen."
            ACTION_FAILED -> "The action could not be completed on the target app."
            STATE_MISMATCH -> "The screen changed unexpectedly. DroidFlow will re-plan."
            TIMEOUT -> "The screen or app took too long to respond."
            SECURE_SCREEN -> "This screen does not permit visual access. DroidFlow cannot safely continue."
            HIGH_RISK_ACTION -> "A high-risk action was detected. Confirmation is required."
            STEP_LIMIT_REACHED -> "The task exceeded its execution budget and was stopped."
            APP_NOT_AVAILABLE -> "The target application is not installed on this device."
            USER_CANCELLED -> "The task was cancelled by the user."
        }
}

@Serializable
data class ExecutionResult(
    val status: ExecutionStatus,
    val reason: String? = null,
    val detail: String? = null,
    val extracted: Map<String, String> = emptyMap()
)

// ---------- Result-screen presentation models ----------

enum class OutcomeStatus { COMPLETED, SAFETY_STOP, CANCELLED, FAILED }

data class FareEntry(
    val appLabel: String,
    val itemLabel: String,
    val price: Int,
    val currency: String = "INR"
)

data class TaskMetrics(
    val events: Int = 0,
    val actions: Int = 0,
    val durationMs: Long = 0L
)

data class TaskOutcome(
    val status: OutcomeStatus,
    val task: Task,
    val fares: List<FareEntry> = emptyList(),
    val chosenApp: String? = null,
    val savings: Int? = null,
    val note: String? = null,
    val metrics: TaskMetrics = TaskMetrics()
)
