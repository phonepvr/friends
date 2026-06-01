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

    /**
     * A contact name pre-encoded for fast repeated T9 matching. Built once per
     * contact (not per keystroke) so [rank] is a handful of String ops.
     *
     * For "John Smith": nameDigits = "564676484", wordStarts = [0, 4]
     * (digit-index where each word begins), initials = "57".
     */
    class NameKey(
        val nameDigits: String,
        val wordStarts: List<Int>,
        val initials: String,
    )

    /** Splits on any run of non-letter/non-digit so "Mary-Jane O'Neil" → words. */
    private val wordSeparator = Regex("[^\\p{L}\\p{Nd}]+")

    /** Pre-encodes [name] into a [NameKey] for [rank]. */
    fun nameKey(name: String): NameKey {
        val digits = StringBuilder()
        val starts = ArrayList<Int>()
        val initials = StringBuilder()
        for (word in name.split(wordSeparator)) {
            val wordDigits = toDigits(word)
            if (wordDigits.isEmpty()) continue
            starts.add(digits.length)
            digits.append(wordDigits)
            initials.append(wordDigits.first())
        }
        return NameKey(digits.toString(), starts, initials.toString())
    }

    /** Match-quality buckets for [rank]; lower is a stronger match. */
    const val RANK_NAME_PREFIX = 0
    const val RANK_WORD_PREFIX = 1
    const val RANK_INITIALS = 2
    const val RANK_SUBSTRING = 3

    /**
     * How well [query] (T9 digits) matches the name behind [key], or null for
     * no match. Mirrors how "smart dial" feels:
     *   0 the whole name starts with the query ("5646" → "John …")
     *   1 a later word starts with it ("76484" → "… Smith")
     *   2 the word initials contain it ("57" → "John Smith")
     *   3 the query appears somewhere in the encoded name (loose fallback)
     */
    fun rank(key: NameKey, query: String): Int? {
        if (query.isEmpty()) return null
        var wordPrefix = false
        for (start in key.wordStarts) {
            if (key.nameDigits.startsWith(query, start)) {
                if (start == 0) return RANK_NAME_PREFIX
                wordPrefix = true
            }
        }
        if (wordPrefix) return RANK_WORD_PREFIX
        if (key.initials.contains(query)) return RANK_INITIALS
        if (key.nameDigits.contains(query)) return RANK_SUBSTRING
        return null
    }
}
