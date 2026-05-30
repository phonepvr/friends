package com.phonepvr.friends.data.dialer

/**
 * T9 digit mapping: letters → keypad digits, the same scheme every phone
 * dialer has used since the late nineties. Punctuation, accents and other
 * non-letter characters that don't map to a digit are dropped; digits in
 * the input pass through unchanged.
 *
 * "Sarah" -> "72724", "+44 7700 123" -> "4477001230" (with the leading
 * `+` dropped and the rest as-is).
 */
object T9 {
    private val digitMap: Map<Char, Char> = buildMap {
        ('a'..'c').forEach { put(it, '2') }
        ('d'..'f').forEach { put(it, '3') }
        ('g'..'i').forEach { put(it, '4') }
        ('j'..'l').forEach { put(it, '5') }
        ('m'..'o').forEach { put(it, '6') }
        ('p'..'s').forEach { put(it, '7') }
        ('t'..'v').forEach { put(it, '8') }
        ('w'..'z').forEach { put(it, '9') }
    }

    /** Encodes [text] to its T9-digit equivalent. */
    fun toDigits(text: String): String = buildString(text.length) {
        for (c in text) {
            val lower = c.lowercaseChar()
            when {
                lower.isDigit() -> append(lower)
                digitMap[lower] != null -> append(digitMap[lower])
                // Drop everything else (whitespace, punctuation, etc.)
            }
        }
    }

    /** Pulls just the digits out of a phone number string. */
    fun digitsOnly(text: String): String = text.filter { it.isDigit() }
}
