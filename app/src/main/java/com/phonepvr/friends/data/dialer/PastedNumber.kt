package com.phonepvr.friends.data.dialer

/**
 * Turns text copied from anywhere (a chat message, an email signature, a web page,
 * a `tel:` link) into something the dialpad can dial. Pure so it can be unit-tested.
 *
 * Rules, matching what the stock dialer's number field accepts:
 * - Takes the first phone-number-like run of characters; anything around it
 *   ("Call me at …", "ext. 89") is ignored. A run is digits plus `+ * #`, held
 *   together by the separators people format numbers with (spaces, dashes, dots,
 *   brackets, slashes).
 * - Keeps `+` only at the start, and keeps `*` and `#` so USSD / service codes
 *   (`*123#`) paste intact. Separators are dropped.
 * - Accepts any script's digits (e.g. Devanagari ९८७६, Arabic-Indic ٩٨٧٦, full-width
 *   ９８７６) and converts them to 0-9.
 * - Skips date-looking runs (12/05/2024, 12.05.24) and anything with fewer than 3
 *   digits unless it is a `*`/`#` code; rejects runs longer than [MAX_LENGTH].
 * - Decodes a `tel:` URI (`tel:*123%23` → `*123#`).
 */
object PastedNumber {

    /** Longer than any real number or USSD code; beyond this it's not a number. */
    const val MAX_LENGTH = 40

    private const val MIN_DIGITS = 3

    private val DATE = Regex("""\d{1,2}[./-]\d{1,2}[./-]\d{2,4}""")

    fun extract(text: CharSequence?): String? {
        if (text.isNullOrBlank()) return null
        var s = text.toString().trim()
        if (s.startsWith("tel:", ignoreCase = true)) s = percentDecode(s.substring(4))
        return runs(normalize(s)).firstNotNullOfOrNull(::accept)
    }

    /** Converts every script's digits and full-width + * # to ASCII. */
    private fun normalize(s: String): String = buildString(s.length) {
        for (c in s) {
            append(
                when {
                    c in '0'..'9' -> c
                    Character.isDigit(c) -> '0' + Character.digit(c, 10)
                    c == '＋' -> '+'
                    c == '＊' -> '*'
                    c == '＃' -> '#'
                    else -> c
                },
            )
        }
    }

    /** Splits [s] into candidate runs of dialable characters and separators. */
    private fun runs(s: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        fun flush() {
            val run = current.toString().trimEnd { isSeparator(it) }
            if (run.isNotEmpty()) out += run
            current.clear()
        }
        for (c in s) {
            when {
                // A '+' after digits starts a new number ("+1 555 … +44 20 …").
                c == '+' && current.any { it.isDigit() } -> {
                    flush()
                    current.append(c)
                }
                isDialable(c) -> current.append(c)
                isSeparator(c) && current.isNotEmpty() -> current.append(c)
                else -> flush()
            }
        }
        flush()
        return out
    }

    private fun accept(run: String): String? {
        if (DATE.matches(run)) return null
        val number = buildString {
            for (c in run) {
                when {
                    c == '+' -> if (isEmpty()) append(c)
                    c.isDigit() || c == '*' || c == '#' -> append(c)
                }
            }
        }
        val digits = number.count { it.isDigit() }
        val isCode = number.any { it == '*' || it == '#' }
        return when {
            number.length > MAX_LENGTH -> null
            digits >= MIN_DIGITS -> number
            isCode && digits >= 1 -> number
            else -> null
        }
    }

    private fun isDialable(c: Char) = c.isDigit() || c == '+' || c == '*' || c == '#'

    private fun isSeparator(c: Char) = c == ' ' || c == '-' || c == '.' || c == '(' ||
        c == ')' || c == '/' || c == ' ' || c == ' ' || c == ' ' ||
        c == ' ' || c == '‐' || c == '‑' || c == '‒' || c == '–'

    /** Minimal %XX decoding; unlike URLDecoder it leaves '+' alone. */
    private fun percentDecode(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val code = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (code != null) {
                    out.append(code.toChar())
                    i += 3
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }
}
