package com.droidflow.safety

/**
 * Sensitive-data hygiene (TRD §24-25).
 * Everything that enters the on-device logs OR leaves the device toward
 * the Gemini API is passed through redact() first:
 *  - OTP / PIN / CVV values → fully masked
 *  - card numbers → only last 4 digits survive
 *  - any 7+ digit run that looks like a code → masked
 */
object SensitiveDataFilter {

    private val OTP_LIKE =
        Regex("""(?i)\b(otp|pin|cvv|password|pass)\b[^A-Za-z0-9]{0,12}(\d{3,8})""")
    private val CARD_LIKE = Regex("""\b(?:\d[ -]?){13,19}\b""")
    private val LONG_NUMBER = Regex("""\b\d{7,}\b""")

    fun redact(input: String): String {
        var s = input
        s = s.replace(OTP_LIKE) { m -> m.value.map { if (it.isDigit()) '•' else it }.joinToString("") }
        s = s.replace(CARD_LIKE) { m ->
            val digits = m.value.filter { it.isDigit() }
            "•••• ${digits.takeLast(4)}"
        }
        s = s.replace(LONG_NUMBER) { m -> "•".repeat(m.value.length) }
        return s
    }

    /** True when a string should never even be redacted-and-logged. */
    fun isSensitive(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return SENSITIVE_HINTS.containsMatchIn(text)
    }

    private val SENSITIVE_HINTS =
        Regex("""(?i)(password|otp|cvv|card number|aadhaar|ssn)""")
}
