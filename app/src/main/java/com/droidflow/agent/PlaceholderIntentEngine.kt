package com.droidflow.agent

import com.droidflow.models.GoalType
import com.droidflow.models.RiskLevel
import com.droidflow.models.Task

/**
 * Phase-1 placeholder intent engine: keyword/pattern based parsing.
 * Understands English plus a few Hindi/Hinglish cues.
 *
 * Replaced by the Gemini-backed IntentEngine in Phase 6 — every screen that
 * consumes its output already speaks the structured Task contract, so the
 * swap will be invisible to the UI.
 */
object PlaceholderIntentEngine {

    fun parse(raw: String): Task? {
        val s = raw.trim().lowercase()
        if (s.isEmpty()) return null
        val id = "task_" + System.currentTimeMillis().toString().takeLast(6)

        return when {
            hasAny(s, "compare", "cheaper", "sasta", "tularna", "kitna sasta") ->
                heroTask(id, s)

            hasAny(s, "price", "cost", "daam", "keemat", "kitna", "find", "dhundo", "dhoondo") ->
                shoppingTask(id, s)

            hasAny(s, "fill", "form", "register", "registration", "bhare", "bharna", "bharo") ->
                formTask(id, s)

            else -> null
        }
    }

    // ---------- Use Case A: cross-app cab price comparison ----------

    private fun heroTask(id: String, s: String): Task {
        val apps = buildList {
            if (hasAny(s, "ridenow", "uber", "app a", "pehla")) add("RideNow")
            if (hasAny(s, "swiftride", "ola", "app b", "dusra")) add("SwiftRide")
            if (isEmpty()) { add("RideNow"); add("SwiftRide") }
        }
        val destination = extractAfter(s, "to the ", "to ", "till ", "for ")
            ?.cleanDestination() ?: "Airport"

        return Task(
            taskId = id,
            goal = "compare_cab_prices",
            goalType = GoalType.COMPARE_PRICES,
            apps = apps,
            entities = mapOf("destination" to destination),
            constraints = listOf("compare_same_ride_class"),
            finalAction = "open_cheaper_option",
            riskLevel = RiskLevel.MEDIUM,
            requiresConfirmation = true,
            stopConditions = listOf("before_booking", "before_payment")
        )
    }

    // ---------- Use Case B: information retrieval ----------

    private fun shoppingTask(id: String, s: String): Task {
        val query = extractQuery(s, listOf("price of ", "cost of ", "daam ", "price for "))
        return Task(
            taskId = id,
            goal = "find_price",
            goalType = GoalType.FIND_PRICE,
            apps = listOf("MockMart"),
            entities = mapOf("query" to (query ?: "wireless headphones")),
            finalAction = "report_price",
            riskLevel = RiskLevel.LOW,
            requiresConfirmation = false,
            stopConditions = listOf("before_add_to_cart", "before_purchase")
        )
    }

    // ---------- Use Case C: form navigation ----------

    private fun formTask(id: String, s: String): Task {
        return Task(
            taskId = id,
            goal = "fill_registration_form",
            goalType = GoalType.FILL_FORM,
            apps = listOf("QuickForm"),
            entities = mapOf(
                "name" to "Alex",
                "email" to "alex@example.com"
            ),
            finalAction = "fill_fields_then_pause_before_submit",
            riskLevel = RiskLevel.MEDIUM,
            requiresConfirmation = true,
            stopConditions = listOf("before_submit")
        )
    }

    // ---------- helpers ----------

    private fun hasAny(s: String, vararg keys: String): Boolean =
        keys.any { s.contains(it) }

    private fun extractAfter(s: String, vararg prefixes: String): String? {
        for (p in prefixes) {
            val i = s.indexOf(p)
            if (i >= 0) {
                return s.substring(i + p.length)
                    .substringBefore(" on ")
                    .substringBefore(" in ")
                    .substringBefore(" app")
                    .trim()
                    .replaceFirstChar { it.uppercase() }
            }
        }
        return null
    }

    private fun String.cleanDestination(): String {
        val t = trim()
        if (t.isEmpty()) return "Airport"
        return t.replaceFirstChar { it.uppercase() }
    }

    private fun extractQuery(s: String, markers: List<String>): String? {
        for (m in markers) {
            val i = s.indexOf(m)
            if (i >= 0) {
                var q = s.substring(i + m.length)
                    .substringBefore(" on ")
                    .substringBefore(" in ")
                    .substringBefore(" app")
                    .trim()
                if (q.startsWith("the ")) q = q.removePrefix("the ")
                if (q.startsWith("my ")) q = q.removePrefix("my ")
                if (q.isNotBlank()) return q.replaceFirstChar { it.uppercase() }
            }
        }
        return null
    }
}
