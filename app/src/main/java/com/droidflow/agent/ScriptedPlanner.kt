package com.droidflow.agent

import com.droidflow.accessibility.ScreenSnapshot
import com.droidflow.models.ActionType
import com.droidflow.models.AgentAction
import com.droidflow.models.ExecutionStatus
import com.droidflow.models.GoalType

/**
 * The deterministic scripted planner (TRD §33, Phase 5).
 *
 * It drives the SAME Observe → Plan → Act → Verify loop as the AI planner,
 * but its decisions are a hand-verified state machine — the guaranteed
 * demo backbone. It reacts to the live screen (popup recovery, app
 * restarts, layout shifts) instead of blind replaying coordinates.
 *
 * Fallback hierarchy: AI planner fails → this planner takes over.
 * No AI key configured → this planner runs from the start.
 */
class ScriptedPlanner(private val task: com.droidflow.models.Task) : Planner {

    private var stage = 0
    private var pending = false // a stage action was planned, waiting for its result
    private var stageAttempt = 0 // failed attempts at the CURRENT stage → try alternate wording
    private val memory = mutableMapOf<String, String>()

    private val appA: String get() = task.apps.getOrElse(0) { "RideNow" }
    private val appB: String get() = task.apps.getOrElse(1) { "SwiftRide" }
    private val dest: String get() = task.entities["destination"] ?: "Airport"
    private val rideClass: String get() = task.entities["ride_class"] ?: "Sedan"
    private val query: String get() = task.entities["query"] ?: "wireless headphones"

    override suspend fun nextStep(ctx: PlanContext): AgentAction? {
        val snap = ctx.snap

        // ---- advance / retry bookkeeping ----
        val lastOk = ctx.lastResult?.status == ExecutionStatus.SUCCESS
        val wasPopupDismiss = ctx.lastAction?.type == ActionType.CLICK &&
                ctx.lastAction?.targetText?.equals("later", ignoreCase = true) == true
        if (pending && lastOk && !wasPopupDismiss) {
            ingest(ctx)
            stage++
            pending = false
            stageAttempt = 0
        } else if (ctx.lastResult != null && !lastOk) {
            pending = false // failed → replan the same stage with alternate wording
            if (!wasPopupDismiss) stageAttempt++
        }

        // ---- popup recovery (works with the demo perturbation sheet) ----
        if (snap != null && hasPopup(snap) && stage >= 2) {
            return click("later", "Unexpected popup detected — dismissing it")
        }

        // ---- goal state machines ----
        return when (task.goalType) {
            GoalType.COMPARE_PRICES -> heroStep(ctx)
            GoalType.FIND_PRICE -> priceStep(ctx)
            GoalType.FILL_FORM -> formStep(ctx)
            GoalType.UNKNOWN -> AgentAction(
                type = ActionType.STOP,
                reason = "safety_unsupported_task",
                expectedResult = "This prototype automates cab comparison, price lookup and form filling. " +
                    "Add a Gemini API key in Settings to attempt free-form tasks with the AI planner."
            )
        }
    }

    // ================= Use Case A: cross-app cab price comparison =================

    private fun heroStep(ctx: PlanContext): AgentAction? {
        val screen = ctx.snap?.state?.screen ?: "Unknown"

        // Robustness: if the cab activity got recreated while backgrounded,
        // re-drive the search from the home screen.
        if (stage >= 9 && screen == "CabHome") stage = 1

        return when (stage) {
            0 -> openApp(appA, "Open the first cab app")
            1 -> type(destHintQuery(appA), dest, "Enter destination '$dest'")
            2 -> click(searchQuery(), "Search for rides")
            3 -> read(rideClass, "Read the $rideClass fare on $appA")
            4 -> openApp(appB, "Open the second cab app")
            5 -> type(destHintQuery(appB), dest, "Enter destination '$dest'")
            6 -> click(searchQuery(), "Search for rides")
            7 -> read(rideClass, "Read the $rideClass fare on $appB")

            8 -> {
                val fa = memory["fare_$appA"]?.toIntOrNull()
                val fb = memory["fare_$appB"]?.toIntOrNull()
                if (fa == null || fb == null) {
                    // one of the reads failed for good — retry the missing one
                    stage = if (fa == null) 3 else 7
                    pending = false
                    nextStepResume(ctx)
                } else {
                    val winner = if (fa <= fb) appA else appB
                    val save = kotlin.math.abs(fb - fa)
                    memory["winner"] = winner
                    openApp(winner, "$winner is ₹$save cheaper — reopening it to continue")
                }
            }

            9 -> click(rideClass, "Select the $rideClass ride on the cheaper app")
            10 -> click(bookQuery(), "Open the booking screen")
            11 -> click("pay", "Complete the booking") // safety gate stops BEFORE this executes
            12 -> AgentAction(
                type = ActionType.STOP,
                reason = "done_booked",
                expectedResult = "Ride booked in the demo app after your explicit confirmations."
            )
            else -> stopDone("compare_finished")
        }
    }

    // ================= Use Case B: price lookup =================

    private fun priceStep(ctx: PlanContext): AgentAction? {
        return when (stage) {
            0 -> openApp("MockMart", "Open the shopping app")
            1 -> type("search products", query, "Search for '$query'")
            2 -> read(query, "Read the price of the matching product")
            3 -> AgentAction(
                type = ActionType.STOP,
                reason = "done_price_found",
                expectedResult = "Price found and reported. No cart or purchase actions were taken."
            )
            else -> stopDone("price_finished")
        }
    }

    // ================= Use Case C: form filling =================

    private fun formStep(ctx: PlanContext): AgentAction? {
        return when (stage) {
            0 -> openApp("QuickForm", "Open the registration form")
            1 -> type("full name", task.entities["name"] ?: "Alex Kumar", "Fill the name field")
            2 -> type("email address", task.entities["email"] ?: "alex@example.com", "Fill the email field")
            3 -> {
                val phone = task.entities["phone"]
                if (phone.isNullOrBlank()) { stage++; nextStepResume(ctx) }
                else type("phone number", phone, "Fill the phone field")
            }
            4 -> {
                val city = task.entities["city"]
                if (city.isNullOrBlank()) { stage++; nextStepResume(ctx) }
                else click("city", "Open the city picker")
            }
            5 -> {
                val city = task.entities["city"]
                if (city.isNullOrBlank()) { stage++; nextStepResume(ctx) }
                else click(city, "Pick the city '$city'")
            }
            6 -> {
                pending = true
                AgentAction(
                    type = ActionType.WAIT,
                    durationMs = 600,
                    reason = "Password field detected — sensitive field skipped by design"
                )
            }
            7 -> click(submitQuery(), "Submit the registration")
            8 -> AgentAction(
                type = ActionType.STOP,
                reason = "done_form_submitted",
                expectedResult = "Registration submitted in the demo app after your confirmation."
            )
            else -> stopDone("form_finished")
        }
    }

    // ================= helpers =================

    /** Called when the previous stage succeeded — copy READ results into memory. */
    private fun ingest(ctx: PlanContext) {
        val last = ctx.lastAction ?: return
        if (last.type == ActionType.READ) {
            val price = ctx.lastResult?.extracted?.get("price") ?: return
            val label = ctx.lastResult?.extracted?.get("label") ?: ""
            when (stage) {
                3 -> { memory["fare_$appA"] = price; memory["label_$appA"] = label }
                7 -> { memory["fare_$appB"] = price; memory["label_$appB"] = label }
            }
        }
    }

    private fun nextStepResume(ctx: PlanContext): AgentAction? {
        // re-enter the state machine after a stage skip (no extra pending flag)
        return when (task.goalType) {
            GoalType.COMPARE_PRICES -> heroStep(ctx)
            GoalType.FIND_PRICE -> priceStep(ctx)
            GoalType.FILL_FORM -> formStep(ctx)
            GoalType.UNKNOWN -> stopDone("unknown")
        }
    }

    private fun hasPopup(snap: ScreenSnapshot): Boolean =
        snap.state.elements.any {
            (it.text ?: "").equals("Later", ignoreCase = true) ||
                    (it.text ?: "").contains("rate us", ignoreCase = true)
        }

    /** Destination-field query differs per app — semantic matching proof. */
    private fun destHintQuery(app: String): String =
        if (app.contains("ridenow", ignoreCase = true)) "Where to?" else "drop location"

    /** Search-button wording variants (survives the 'rename button' perturbation). */
    private fun searchQuery(): String {
        val options = listOf("rides", "ride", "your ride", "search", "find")
        return options[minOf(stageAttempt, options.size - 1)]
    }

    private fun bookQuery(): String {
        val options = listOf("book ride", "book", "confirm your ride")
        return options[minOf(stageAttempt, options.size - 1)]
    }

    private fun submitQuery(): String {
        val options = listOf("submit registration", "register", "submit")
        return options[minOf(stageAttempt, options.size - 1)]
    }

    private fun openApp(app: String, why: String): AgentAction {
        pending = true
        return AgentAction(
            type = ActionType.OPEN_APP,
            packageToOpen = app,
            reason = why,
            expectedResult = "foreground=$app",
            confidence = 0.95
        )
    }

    private fun click(target: String, why: String): AgentAction {
        pending = true
        return AgentAction(
            type = ActionType.CLICK,
            targetText = target,
            reason = why,
            confidence = 0.9
        )
    }

    private fun type(target: String, text: String, why: String): AgentAction {
        pending = true
        return AgentAction(
            type = ActionType.TYPE,
            targetText = target,
            text = text,
            reason = why,
            confidence = 0.9
        )
    }

    private fun read(target: String, why: String): AgentAction {
        pending = true
        return AgentAction(
            type = ActionType.READ,
            targetText = target,
            reason = why,
            expectedResult = "price visible",
            confidence = 0.85
        )
    }

    private fun stopDone(reason: String): AgentAction {
        pending = false
        return AgentAction(type = ActionType.STOP, reason = reason)
    }
}
