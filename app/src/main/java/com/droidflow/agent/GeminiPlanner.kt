package com.droidflow.agent

import com.droidflow.models.ActionType
import com.droidflow.models.AgentAction
import com.droidflow.safety.SensitiveDataFilter
import kotlinx.serialization.json.Json

/**
 * The AI planner (TRD §28-30). Sends the compact screen representation +
 * task + short history to Gemini and expects ONE validated action back.
 *
 * Degradation ladder (never crashes the demo):
 *   Gemini reply invalid → retry once → fall back to ScriptedPlanner.
 */
class GeminiPlanner(
    private val client: GeminiClient,
    private val task: com.droidflow.models.Task,
    private val fallback: Planner
) : Planner {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var hardFailures = 0

    override suspend fun nextStep(ctx: PlanContext): AgentAction? {
        if (hardFailures >= 2 || !client.isConfigured) return fallback.nextStep(ctx)

        val prompt = buildPrompt(ctx)
        val reply = client.generate(PLANNER_SYSTEM, prompt, temperature = 0.15)
        val text = reply.getOrNull() ?: run {
            hardFailures++
            return fallback.nextStep(ctx)
        }

        val action = parseAndValidate(text)
        if (action == null) {
            // one corrective retry, then permanent fallback
            val retry = client.generate(
                PLANNER_SYSTEM,
                prompt + "\n\nYour previous reply was not valid JSON for the schema. Reply again with ONE valid action object only.",
                temperature = 0.0
            ).getOrNull()?.let { parseAndValidate(it) }
            if (retry == null) {
                hardFailures++
                return fallback.nextStep(ctx)
            }
            return retry
        }
        return action
    }

    // ---------- prompt building ----------

    private fun buildPrompt(ctx: PlanContext): String {
        val sb = StringBuilder()
        sb.append("TASK: ").append(ctx.task.toString()).append('\n')
        if (ctx.memory.isNotEmpty()) sb.append("MEMORY: ").append(ctx.memory).append('\n')
        if (ctx.history.isNotEmpty()) {
            sb.append("RECENT HISTORY:\n")
            ctx.history.takeLast(6).forEach { sb.append("  - ").append(it).append('\n') }
        }
        val snap = ctx.snap
        sb.append("SCREEN package=").append(snap?.state?.packageName ?: "?")
            .append(" screen=").append(snap?.state?.screen ?: "?").append('\n')
        sb.append("ELEMENTS:\n")
        snap?.state?.elements?.take(80)?.forEach { el ->
            val parts = listOfNotNull(
                el.text?.let { "text='${SensitiveDataFilter.redact(it.take(48))}'" },
                el.contentDescription?.let { "desc='${it.take(48)}'" },
                el.hint?.let { "hint='${it.take(32)}'" },
                "type=${el.type}",
                if (el.clickable) "clickable" else null,
                if (el.editable) "editable" else null
            ).joinToString(" ")
            sb.append("  [${el.id}] $parts\n")
        }
        sb.append("Decide the single next action.")
        return sb.toString()
    }

    // ---------- response validation (TRD §45: schema before execution) ----------

    private fun parseAndValidate(text: String): AgentAction? {
        val obj = extractJsonObject(text) ?: return null
        val action = runCatching { json.decodeFromString<AgentAction>(obj) }.getOrNull() ?: return null
        return validate(action)
    }

    private fun validate(a: AgentAction): AgentAction? {
        val ok = when (a.type) {
            ActionType.CLICK, ActionType.READ -> !a.targetText.isNullOrBlank()
            ActionType.TYPE -> !a.targetText.isNullOrBlank() && !a.text.isNullOrBlank()
            ActionType.OPEN_APP -> !a.packageToOpen.isNullOrBlank()
            ActionType.ASK_USER, ActionType.STOP, ActionType.BACK,
            ActionType.WAIT, ActionType.SCROLL, ActionType.SWIPE -> true
        }
        if (!ok) return null
        return a.copy(confidence = a.confidence.coerceIn(0.0, 1.0))
    }

    private val PLANNER_SYSTEM = """
You are DroidFlow's action planner. You control an Android phone ONLY through these actions:
CLICK(targetText) · TYPE(targetText, text) · SCROLL(direction) · SWIPE(direction) · BACK() ·
OPEN_APP(packageToOpen) · WAIT(durationMs) · READ(targetText) · ASK_USER(reason) · STOP(reason, expectedResult).

Reply with ONE JSON object exactly matching:
{"type":"CLICK","targetText":null,"text":null,"direction":null,"packageToOpen":null,"durationMs":0,
 "reason":"why","expectedResult":"what should change","confidence":0.9}

HARD RULES:
1. targetText MUST be copied from text/desc/hint values visible in the ELEMENTS section.
2. Never invent coordinates or element ids.
3. Never TYPE into password/OTP/PIN fields — use ASK_USER or STOP instead.
4. Known apps: RideNow, SwiftRide, MockMart, QuickForm; real: Uber (com.ubercab), Ola (com.olacabs.customer).
5. Use READ to extract prices/fares; use STOP with a clear expectedResult when the goal is achieved
   or when a safety boundary (payment/submit) must not be crossed.
6. Keep "reason" under 20 words. confidence = 0..1.
""".trimIndent()
}
