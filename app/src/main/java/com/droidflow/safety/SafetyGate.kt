package com.droidflow.safety

import com.droidflow.models.ActionType
import com.droidflow.models.AgentAction
import com.droidflow.models.Task

enum class SafetyVerdict { ALLOW, CONFIRM, DENY }

data class SafetyDecision(val verdict: SafetyVerdict, val explanation: String)

/**
 * The safety gate (TRD §20-23). Every action the planner proposes passes
 * through here BEFORE execution. The LLM never gets the final word on
 * money-sensitive or irreversible actions.
 *
 * Verdicts:
 *  - ALLOW   : low risk, execute immediately
 *  - CONFIRM : one-way / medium-risk action → pause, ask the human
 *  - DENY    : hard boundary (payment stop-condition, sensitive fields)
 */
object SafetyGate {

    private val HIGH_RISK = Regex("""(?i)\b(pay|payment|checkout|place order|transfer)\b""")
    private val MEDIUM_RISK =
        Regex("""(?i)\b(book|submit|purchase|buy now|register|sign in|log in|login|subscribe|delete|remove)\b""")
    private val SENSITIVE_FIELD =
        Regex("""(?i)(password|otp|pin|cvv|card number|sensitive|aadhaar|ssn)""")

    fun evaluate(action: AgentAction, targetText: String?, task: Task): SafetyDecision {
        when (action.type) {
            ActionType.TYPE -> {
                val touched =
                    "${targetText ?: ""} ${action.targetText ?: ""} ${action.targetHint ?: ""}"
                if (SENSITIVE_FIELD.containsMatchIn(touched)) {
                    return SafetyDecision(
                        SafetyVerdict.DENY,
                        "Sensitive field detected (password/OTP/PIN). " +
                            "DroidFlow never types into sensitive fields."
                    )
                }
            }

            ActionType.CLICK -> {
                val t = targetText ?: action.targetText ?: ""
                if (HIGH_RISK.containsMatchIn(t)) {
                    val stopsAtPayment =
                        task.stopConditions.any { it.contains("payment", ignoreCase = true) }
                    if (stopsAtPayment) {
                        return SafetyDecision(
                            SafetyVerdict.DENY,
                            "PAYMENT BOUNDARY — '$t'. Task stop-condition 'before_payment' " +
                                "enforced: the agent hard-stopped before paying."
                        )
                    }
                    return SafetyDecision(
                        SafetyVerdict.CONFIRM,
                        "'$t' is a money action — it needs your explicit confirmation."
                    )
                }
                if (MEDIUM_RISK.containsMatchIn(t)) {
                    return SafetyDecision(
                        SafetyVerdict.CONFIRM,
                        "'$t' is a one-way action (booking/submit) — needs your confirmation."
                    )
                }
            }

            ActionType.OPEN_APP -> {
                // AppController only launches known apps; unknown names fail resolution.
            }

            else -> {
                // READ / WAIT / SCROLL / SWIPE / BACK are non-mutating.
            }
        }
        return SafetyDecision(SafetyVerdict.ALLOW, "Low-risk action allowed.")
    }
}
