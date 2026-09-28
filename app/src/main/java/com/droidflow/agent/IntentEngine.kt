package com.droidflow.agent

import com.droidflow.models.GoalType
import com.droidflow.models.RiskLevel
import com.droidflow.models.Task
import kotlinx.serialization.json.Json

/**
 * Intent understanding (TRD §29): natural language (English or Hindi /
 * Hinglish) → structured Task contract.
 *
 * Strategy: Gemini when a key is configured, keyword fallback otherwise.
 * Both produce the exact same Task JSON, so the rest of the pipeline never
 * knows which one ran.
 */
object IntentEngine {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun parse(raw: String, apiKey: String): Task? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        if (apiKey.isNotBlank()) {
            val client = GeminiClient(apiKey)
            client.generate(INTENT_SYSTEM, "USER TASK: \"$trimmed\"").getOrNull()?.let { reply ->
                val parsed = extractJsonObject(reply)?.let {
                    runCatching { json.decodeFromString<Task>(it) }.getOrNull()
                }
                if (parsed != null) return enrich(parsed, trimmed)
            }
        }
        return PlaceholderIntentEngine.parse(trimmed)
    }

    /** Guarantees required fields no matter what the model returned. */
    private fun enrich(task: Task, raw: String): Task {
        val id = if (task.taskId.isBlank()) "task_" + System.currentTimeMillis().toString().takeLast(6)
        else task.taskId
        val apps = task.apps.ifEmpty {
            when (task.goalType) {
                GoalType.COMPARE_PRICES -> listOf("RideNow", "SwiftRide")
                GoalType.FIND_PRICE -> listOf("MockMart")
                GoalType.FILL_FORM -> listOf("QuickForm")
                GoalType.UNKNOWN -> emptyList()
            }
        }
        val stop = task.stopConditions.ifEmpty {
            when (task.goalType) {
                GoalType.COMPARE_PRICES -> listOf("before_booking", "before_payment")
                GoalType.FIND_PRICE -> listOf("before_add_to_cart", "before_purchase")
                GoalType.FILL_FORM -> listOf("before_submit")
                GoalType.UNKNOWN -> emptyList()
            }
        }
        return task.copy(
            taskId = id,
            apps = apps,
            goal = task.goal.ifBlank { raw.take(60) },
            stopConditions = stop,
            riskLevel = if (task.goalType == GoalType.UNKNOWN) RiskLevel.MEDIUM else task.riskLevel
        )
    }

    private val INTENT_SYSTEM = """
You convert a user's phone-automation request into ONE JSON object.
Schema (all fields required, use "" or [] when unknown):
{
  "taskId": "task_12345",
  "goal": "short snake_case goal",
  "goalType": "COMPARE_PRICES" | "FIND_PRICE" | "FILL_FORM" | "UNKNOWN",
  "apps": ["RideNow","SwiftRide"],
  "entities": {"destination":"Airport","ride_class":"Sedan","query":"","name":"","email":"","phone":"","city":""},
  "constraints": ["compare_same_ride_class"],
  "finalAction": "open_cheaper_option",
  "riskLevel": "LOW" | "MEDIUM" | "HIGH",
  "requiresConfirmation": true,
  "stopConditions": ["before_payment"]
}
Known demo apps: RideNow and SwiftRide (cab booking), MockMart (shopping), QuickForm (registration form).
Default cab ride class is "Sedan". Tasks may be English, Hindi or Hinglish (e.g. "RideNow aur SwiftRide ka airport ka fare compare karo").
The agent NEVER completes payments: stopConditions must always include the relevant boundary
(before_payment for bookings, before_submit for forms, before_purchase for shopping).
Reply with the JSON object only.
""".trimIndent()
}
