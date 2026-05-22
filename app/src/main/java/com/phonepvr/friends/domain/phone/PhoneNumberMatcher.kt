package com.phonepvr.friends.domain.phone

/**
 * Pure-Kotlin phone-number matching used to link call-log entries to tracked
 * people. Matching compares only the trailing digits, so formatting and
 * country codes do not matter. No Android dependencies — JVM-unit-testable.
 */
object PhoneNumberMatcher {

    private const val MATCH_DIGIT_COUNT = 7

    /** Digits only: strips +, spaces, dashes, parentheses and other symbols. */
    fun normalize(raw: String): String = raw.filter { it.isDigit() }

    /**
     * The trailing [MATCH_DIGIT_COUNT] digits used to compare two numbers, or
     * null when there are too few digits (short codes, withheld numbers).
     */
    fun matchKey(raw: String): String? {
        val digits = normalize(raw)
        return if (digits.length >= MATCH_DIGIT_COUNT) {
            digits.takeLast(MATCH_DIGIT_COUNT)
        } else {
            null
        }
    }

    /** True when two numbers plausibly belong to the same line. */
    fun matches(first: String, second: String): Boolean {
        val keyFirst = matchKey(first) ?: return false
        val keySecond = matchKey(second) ?: return false
        return keyFirst == keySecond
    }

    /** Stable identifier for one call, so a re-scan never queues it twice. */
    fun callDedupKey(rawNumber: String, timestampMillis: Long): String {
        val digits = normalize(rawNumber).ifEmpty { "unknown" }
        return "$digits@$timestampMillis"
    }
}
