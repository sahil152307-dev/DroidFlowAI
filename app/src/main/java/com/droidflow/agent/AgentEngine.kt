package com.droidflow.agent

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.droidflow.DroidFlowApp
import com.droidflow.MainActivity
import com.droidflow.accessibility.DroidFlowAccessibilityService
import com.droidflow.accessibility.ScreenSnapshot
import com.droidflow.data.SettingsStore
import com.droidflow.execution.ActionExecutor
import com.droidflow.execution.AppController
import com.droidflow.logging.AgentLogger
import com.droidflow.logging.EventKind
import com.droidflow.models.ActionType
import com.droidflow.models.AgentAction
import com.droidflow.models.ExecutionResult
import com.droidflow.models.ExecutionStatus
import com.droidflow.models.FareEntry
import com.droidflow.models.GoalType
import com.droidflow.models.OutcomeStatus
import com.droidflow.models.RiskLevel
import com.droidflow.models.Task
import com.droidflow.models.TaskMetrics
import com.droidflow.models.TaskOutcome
import com.droidflow.safety.SafetyGate
import com.droidflow.safety.SafetyVerdict
import com.droidflow.safety.SensitiveDataFilter
import com.droidflow.ui.AgentPhase
import com.droidflow.ui.ExecutionUiState
import com.droidflow.ui.SimFare
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The real agent orchestrator (TRD §10-12).
 *
 * LLM → structured action → schema validation → semantic target resolution
 * → safety gate → execution → verification → replan. The model NEVER
 * touches the OS directly.
 *
 * Application-scoped: the loop keeps running while DroidFlow is in the
 * background driving other apps, and pulls itself to the foreground when
 * a safety confirmation is needed or the task finishes.
 */
object AgentEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val logger by lazy { AgentLogger(DroidFlowApp.instance) }

    var ui by mutableStateOf(ExecutionUiState())
        private set

    var pendingTask by mutableStateOf<Task?>(null)
        private set

    var outcome by mutableStateOf<TaskOutcome?>(null)
        private set

    var finished by mutableStateOf(false)
        private set

    private var runJob: Job? = null
    private var startMs = 0L
    private var confirmGate: CompletableDeferred<Boolean>? = null

    // ---- per-run state ----
    private val memory = mutableMapOf<String, String>()
    private val history = mutableListOf<String>()
    private val fares = mutableListOf<SimFare>()
    private var planner: Planner? = null
    private var lastAction: AgentAction? = null
    private var lastResult: ExecutionResult? = null

    // ================= public API (facade used by AgentViewModel) =================

    fun prepareTask(task: Task) {
        pendingTask = task
    }

    fun startTask(task: Task, context: Context) {
        runJob?.cancel()
        memory.clear(); history.clear(); fares.clear()
        lastAction = null; lastResult = null
        outcome = null; finished = false
        startMs = System.currentTimeMillis()
        ui = ExecutionUiState(task = task)
        logger.beginTask(task.taskId)
        logger.log(EventKind.INFO, "Task received: ${task.goal}")

        runJob = scope.launch {
            val settings = loadSettings()
            runLoop(task, settings)
        }
    }

    fun confirmBoundary() {
        confirmGate?.complete(true)
    }

    fun declineBoundary() {
        confirmGate?.complete(false)
    }

    fun cancelTask() {
        logger.log(EventKind.WARN, "Task cancelled by user")
        confirmGate?.complete(false)
        runJob?.cancel()
    }

    fun reset() {
        runJob?.cancel()
        confirmGate?.complete(false)
        logger.clear()
        outcome = null; finished = false; pendingTask = null
        ui = ExecutionUiState()
        memory.clear(); history.clear(); fares.clear()
    }

    // ================= the loop =================

    private class ResolvedSettings(
        val apiKey: String,
        val plannerMode: String, // auto | ai | scripted
        val confHigh: Double,
        val confLow: Double,
        val maxSteps: Int
    )

    private suspend fun loadSettings(): ResolvedSettings {
        val store = SettingsStore(DroidFlowApp.instance)
        return ResolvedSettings(
            apiKey = store.geminiApiKey.first(),
            plannerMode = store.plannerMode.first(),
            confHigh = store.confidenceHigh.first().toDouble(),
            confLow = store.confidenceLow.first().toDouble(),
            maxSteps = store.maxSteps.first()
        )
    }

    private suspend fun runLoop(task: Task, s: ResolvedSettings) {
        try {
            if (!DroidFlowAccessibilityService.isRunning()) {
                logger.log(EventKind.ERROR, "Accessibility service is OFF — cannot automate. " +
                        "Enable DroidFlow in Settings → Accessibility.")
                finish(task, OutcomeStatus.FAILED, "Accessibility service is off.")
                return
            }

            planner = createPlanner(task, s)
            logger.log(EventKind.INFO, when (s.plannerMode) {
                "ai" -> "Planner: AI-first (Gemini) with deterministic fallback"
                "scripted" -> "Planner: deterministic script"
                else -> "Planner: hybrid (scripted for known goals, AI for free-form)"
            })

            val executor = ActionExecutor(DroidFlowApp.instance)
            var step = 0
            var fails = 0

            while (step < s.maxSteps) {
                // ---------- 1. OBSERVE ----------
                setPhase(AgentPhase.OBSERVE, "Reading the current screen…")
                val snap = observeWithRetry()
                    ?: run {
                        logger.log(EventKind.ERROR, "No window content (secure screen or display off?)")
                        finish(task, OutcomeStatus.FAILED, "Could not read the screen.")
                        return
                    }
                logger.log(
                    EventKind.INFO,
                    "Observe: ${snap.state.packageName} · ${snap.state.elements.size} elements · screen=${snap.state.screen}"
                )

                // ---------- 2. PLAN ----------
                setPhase(AgentPhase.PLAN, "Deciding the next action…")
                val action = try {
                    planner?.nextStep(
                        PlanContext(task, memory, snap, lastAction, lastResult, step, history)
                    )
                } catch (t: Throwable) {
                    logger.log(EventKind.ERROR, "Planner error: ${t.message}")
                    null
                }
                if (action == null) {
                    finish(task, OutcomeStatus.FAILED, "The planner could not continue.")
                    return
                }

                // ---------- 3. STOP / ASK_USER ----------
                if (action.type == ActionType.STOP) {
                    logger.log(EventKind.INFO, "Planner stop: ${action.reason}")
                    val note = action.expectedResult
                        ?: "Task finished by the planner (${action.reason})."
                    val status = if (action.reason?.startsWith("safety") == true) OutcomeStatus.SAFETY_STOP
                    else OutcomeStatus.COMPLETED
                    finish(task, status, note)
                    return
                }
                if (action.type == ActionType.ASK_USER) {
                    val proceed = pauseForConfirm(
                        "Agent needs your input",
                        action.reason ?: "Should I continue?",
                        task
                    )
                    if (!proceed) {
                        finish(task, OutcomeStatus.CANCELLED, "User stopped the agent.")
                        return
                    }
                    continue
                }

                // ---------- 4. SAFETY GATE (before resolution — dangerous
                // actions are stopped even if their target can't be found) ----------
                val decision = SafetyGate.evaluate(
                    action,
                    action.targetText,
                    task
                )
                if (decision.verdict == SafetyVerdict.DENY) {
                    logger.log(EventKind.SAFETY, "BLOCKED: ${decision.explanation}")
                    setPhase(AgentPhase.SAFETY, decision.explanation)
                    finish(task, OutcomeStatus.SAFETY_STOP, decision.explanation)
                    return
                }
                if (decision.verdict == SafetyVerdict.CONFIRM) {
                    logger.log(EventKind.SAFETY, "Confirmation required: ${decision.explanation}")
                    val ok = pauseForConfirm(
                        confirmTitle(action),
                        decision.explanation,
                        task
                    )
                    if (!ok) {
                        logger.log(EventKind.SAFETY, "User declined — no risky action taken")
                        finish(
                            task, OutcomeStatus.SAFETY_STOP,
                            "Stopped at a safety boundary. Nothing irreversible was done."
                        )
                        return
                    }
                    logger.log(EventKind.SAFETY, "User confirmed — proceeding")
                }

                // ---------- 5. VALIDATE + RESOLVE ----------
                setPhase(AgentPhase.PLAN, describe(action))
                val resolved = executor.resolve(action, snap)
                if (needsTarget(action) && (resolved == null || resolved.score < s.confLow)) {
                    val best = resolved?.let { " (best score %.2f)".format(it.score) } ?: ""
                    logger.log(
                        EventKind.WARN,
                        "Target '${action.targetText ?: "?"}' not resolved confidently$best — re-observing"
                    )
                    fails++
                    lastAction = action
                    lastResult = ExecutionResult(ExecutionStatus.FAILURE, "TARGET_NOT_FOUND")
                    if (fails >= 3) {
                        finish(task, OutcomeStatus.FAILED,
                            "Could not find '${action.targetText}' on the screen.")
                        return
                    }
                    delay(500)
                    continue
                }
                if (resolved != null && resolved.score < s.confHigh) {
                    logger.log(
                        EventKind.WARN,
                        "Match confidence %.2f below %.2f — proceeding with care"
                            .format(resolved.score, s.confHigh)
                    )
                }

                // ---------- 6. ACT ----------
                setPhase(AgentPhase.ACT, describe(action))
                val result = executor.execute(action, snap, resolved)
                val desc = describe(action) + " → ${result.status.name}"
                history.add(desc)
                logger.log(EventKind.ACTION, desc)

                // ---------- 7. VERIFY ----------
                setPhase(AgentPhase.VERIFY, "Verifying the outcome…")
                val verified = VerificationEngine.verify(action, result, snap)
                if (verified && result.status == ExecutionStatus.SUCCESS) {
                    fails = 0
                    val note = verifyNote(action, result)
                    logger.log(EventKind.VERIFY, "Verified: $note")
                    setPhase(AgentPhase.VERIFIED, note)
                    collectRead(result)
                } else {
                    fails++
                    logger.log(
                        EventKind.WARN,
                        "Not verified (${result.reason ?: result.status.name}) — re-planning"
                    )
                    if (fails >= 3) {
                        finish(task, OutcomeStatus.FAILED,
                            "An action failed repeatedly: ${result.reason ?: result.detail ?: "unknown"}.")
                        return
                    }
                }

                lastAction = action
                lastResult = result
                step++
                delay(400) // demo pacing
            }
            finish(task, OutcomeStatus.FAILED,
                "The task exceeded its step budget (${s.maxSteps}) and was stopped.")
        } catch (c: CancellationException) {
            finish(task, OutcomeStatus.CANCELLED, "Task cancelled by user.")
        } catch (t: Throwable) {
            logger.log(EventKind.ERROR, "Engine error: ${t.message}")
            finish(task, OutcomeStatus.FAILED, "Unexpected error: ${t.message}")
        }
    }

    // ================= verification =================

    private object VerificationEngine {
        suspend fun verify(
            action: AgentAction,
            result: ExecutionResult,
            before: ScreenSnapshot?
        ): Boolean = when (action.type) {
            ActionType.OPEN_APP -> result.status == ExecutionStatus.SUCCESS
            ActionType.TYPE -> {
                result.status == ExecutionStatus.SUCCESS && poll(3200) { snap ->
                    val t = action.text ?: ""
                    t.isBlank() || snap.state.elements.any {
                        it.editable && (it.text ?: "").contains(t, ignoreCase = true)
                    }
                }
            }
            ActionType.CLICK -> {
                result.status == ExecutionStatus.SUCCESS && poll(6000) { snap ->
                    signature(snap) != signature(before) ||
                            snap.state.screen != before?.state?.screen
                }
            }
            ActionType.READ -> result.extracted.containsKey("price")
            else -> result.status == ExecutionStatus.SUCCESS
        }

        private suspend fun poll(timeoutMs: Long, cond: (ScreenSnapshot) -> Boolean): Boolean {
            val start = android.os.SystemClock.elapsedRealtime()
            while (android.os.SystemClock.elapsedRealtime() - start < timeoutMs) {
                delay(350)
                val s = DroidFlowAccessibilityService.instance?.snapshot() ?: continue
                if (cond(s)) return true
            }
            return false
        }

        private fun signature(s: ScreenSnapshot?): String? =
            s?.state?.elements?.joinToString("|") { (it.text ?: "-") + "/" + (it.contentDescription ?: "-") }
    }

    // ================= helpers =================

    private fun createPlanner(task: Task, s: ResolvedSettings): Planner {
        val scripted = ScriptedPlanner(task)
        return when {
            s.plannerMode == "scripted" -> scripted
            s.plannerMode == "ai" && s.apiKey.isNotBlank() ->
                GeminiPlanner(GeminiClient(s.apiKey), task, scripted)
            s.apiKey.isNotBlank() && task.goalType == GoalType.UNKNOWN ->
                GeminiPlanner(GeminiClient(s.apiKey), task, scripted)
            else -> scripted
        }
    }

    private suspend fun observeWithRetry(): ScreenSnapshot? {
        repeat(3) {
            val snap = DroidFlowAccessibilityService.instance?.snapshot()
            if (snap != null && snap.state.elements.isNotEmpty()) return snap
            delay(300)
        }
        return DroidFlowAccessibilityService.instance?.snapshot()
    }

    private fun needsTarget(action: AgentAction): Boolean = when (action.type) {
        ActionType.CLICK, ActionType.TYPE, ActionType.READ -> true
        else -> false
    }

    private fun collectRead(result: ExecutionResult) {
        val price = result.extracted["price"] ?: return
        val label = result.extracted["label"] ?: "item"
        val appLabel = AppController.labelForCurrentWindow() ?: "app"
        val fare = SimFare(appLabel, label, price.toIntOrNull() ?: 0)
        fares.add(fare)
        ui = ui.copy(fares = ui.fares + fare)
        logger.log(EventKind.SUCCESS, "Fare detected: ${fare.appLabel} · ${fare.itemLabel} ₹${fare.price}")
    }

    private fun setPhase(phase: AgentPhase, detail: String) {
        ui = ui.copy(phase = phase, detail = detail)
    }

    private suspend fun pauseForConfirm(title: String, body: String, task: Task): Boolean {
        setPhase(AgentPhase.SAFETY, title)
        ui = ui.copy(awaitingConfirmation = true, confirmationTitle = title, confirmationBody = body)
        bringToFront()
        val gate = CompletableDeferred<Boolean>()
        confirmGate = gate
        val ok = try {
            gate.await()
        } catch (e: Exception) {
            false
        }
        confirmGate = null
        ui = ui.copy(awaitingConfirmation = false)
        return ok
    }

    private fun bringToFront() {
        try {
            val ctx = DroidFlowApp.instance
            ctx.startActivity(
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (_: Exception) {
        }
    }

    private fun confirmTitle(action: AgentAction): String = when (action.type) {
        ActionType.CLICK -> "Confirm: ${action.type.name} '${action.targetText}'"
        else -> "Confirm: ${action.type.name}"
    }

    private fun describe(action: AgentAction): String = when (action.type) {
        ActionType.CLICK -> "CLICK '${action.targetText}'"
        ActionType.TYPE -> "TYPE '${SensitiveDataFilter.redact(action.text ?: "")}' into '${action.targetText}'"
        ActionType.READ -> "READ '${action.targetText}'"
        ActionType.OPEN_APP -> "OPEN_APP ${action.packageToOpen}"
        ActionType.SCROLL -> "SCROLL ${action.direction ?: "down"}"
        ActionType.SWIPE -> "SWIPE ${action.direction ?: "up"}"
        ActionType.BACK -> "BACK"
        ActionType.WAIT -> "WAIT ${action.durationMs}ms — ${action.reason ?: ""}"
        ActionType.ASK_USER -> "ASK_USER"
        ActionType.STOP -> "STOP"
    }

    private fun verifyNote(action: AgentAction, result: ExecutionResult): String = when (action.type) {
        ActionType.OPEN_APP -> "${action.packageToOpen} is now in the foreground"
        ActionType.TYPE -> "text accepted by the field"
        ActionType.CLICK -> "screen changed as expected"
        ActionType.READ -> "value extracted: ${result.extracted["price"] ?: "?"}"
        else -> result.detail ?: "ok"
    }

    // ================= outcome =================

    private fun finish(task: Task, status: OutcomeStatus, note: String?) {
        val comparePair = fares.takeIf { task.goalType == GoalType.COMPARE_PRICES && it.size >= 2 }
        val cheaper = comparePair?.minByOrNull { it.price }
        val savings = comparePair?.let { (it.maxOf { p -> p.price }) - (cheaper?.price ?: 0) }

        outcome = TaskOutcome(
            status = status,
            task = task,
            fares = fares.map { FareEntry(it.appLabel, it.itemLabel, it.price) },
            chosenApp = if (task.goalType == GoalType.COMPARE_PRICES) cheaper?.appLabel else null,
            savings = if (task.goalType == GoalType.COMPARE_PRICES) savings else null,
            note = note ?: finalNote(status, task.goalType, task.riskLevel),
            metrics = TaskMetrics(
                events = logger.eventCount,
                actions = logger.actionCount,
                durationMs = System.currentTimeMillis() - startMs
            )
        )
        ui = ui.copy(phase = AgentPhase.DONE, awaitingConfirmation = false)
        finished = true
        bringToFront()
    }

    private fun finalNote(status: OutcomeStatus, goal: GoalType, risk: RiskLevel): String =
        when (status) {
            OutcomeStatus.CANCELLED -> "Task cancelled by user."
            OutcomeStatus.SAFETY_STOP ->
                "DroidFlow stopped at a safety boundary. Nothing irreversible was done."
            OutcomeStatus.FAILED -> "DroidFlow stopped safely instead of guessing."
            OutcomeStatus.COMPLETED -> when (goal) {
                GoalType.COMPARE_PRICES ->
                    "Cheaper option opened. DroidFlow stopped before booking/payment — no purchase was made."
                GoalType.FIND_PRICE ->
                    "Price found and reported. No cart or purchase actions were taken."
                GoalType.FILL_FORM ->
                    "Fields filled and verified. Sensitive fields were skipped."
                GoalType.UNKNOWN -> "Task finished."
            }
        }
}
