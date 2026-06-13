package com.phonepvr.friends.data.contacts

/**
 * Best-effort vCard 3.0 reader. Accepts the dialect produced by every major
 * contacts app (Google, Samsung, Fossify, Bondwidth itself) and reads back
 * the fields [ContactWriter] knows how to persist: display name, phones,
 * emails, organization, notes, birthday. Anything more exotic (addresses,
 * structured names beyond family/given, PHOTO blobs, X-* extensions) is
 * skipped — round-tripping it would mean expanding the rest of the
 * contacts surface and v1 keeps the import scope tight.
 *
 * Pure Kotlin, no Android dependencies — unit-testable on the JVM.
 */
data class ParsedVCard(
    val displayName: String,
    val phones: List<String>,
    val emails: List<String>,
    val organization: String?,
    val notes: String?,
    val birthday: ContactDate?,
)

object VCardParser {

    /**
     * Parses [text] into one ParsedVCard per BEGIN:VCARD / END:VCARD block.
     * Cards missing a usable display name are dropped: ContactWriter
     * requires it and a nameless card is rarely meaningful.
     */
    fun parse(text: String): List<ParsedVCard> {
        val normalised = unfoldLines(text)
        val cards = mutableListOf<ParsedVCard>()
        var inCard = false
        var card = MutableCard()
        for (raw in normalised.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.equals("BEGIN:VCARD", ignoreCase = true) -> {
                    inCard = true
                    card = MutableCard()
                }
                line.equals("END:VCARD", ignoreCase = true) -> {
                    if (inCard) card.toParsed()?.let { cards.add(it) }
                    inCard = false
                }
                inCard -> readProperty(line, card)
            }
        }
        return cards
    }

    /**
     * vCard "folds" long lines by inserting CRLF + space mid-line. Collapse
     * any folded continuation back onto one line before parsing.
     */
    private fun unfoldLines(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val next = text.getOrNull(i + 1)
            if (c == '\n' && (next == ' ' || next == '\t')) {
                i += 2
                continue
            }
            if (c == '\r' && next == '\n' && text.getOrNull(i + 2).let { it == ' ' || it == '\t' }) {
                i += 3
                continue
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun readProperty(line: String, card: MutableCard) {
        val colon = line.indexOf(':')
        if (colon < 0) return
        val head = line.substring(0, colon)
        val value = unescape(line.substring(colon + 1).trim())
        // Property name + params split on ';'. The name is the first chunk;
        // remaining chunks are PARAM=VALUE (or bare TYPE values).
        val params = head.split(';')
        // Strip a "group." prefix some exporters add (e.g. "item1.TEL").
        val name = params[0].substringAfter('.').uppercase()
        when (name) {
            "FN" -> card.fn = value.takeIf { it.isNotBlank() }
            "N" -> card.n = value.takeIf { it.isNotBlank() }
            "TEL" -> value.takeIf { it.isNotBlank() }?.let { card.phones += it }
            "EMAIL" -> value.takeIf { it.isNotBlank() }?.let { card.emails += it }
            "ORG" -> {
                // ORG is structured (Org;Department;Unit). Take the first
                // non-empty segment — that's what every UI uses for "company".
                card.org = value.split(';').firstOrNull { it.isNotBlank() }
            }
            "NOTE" -> card.note = value.takeIf { it.isNotBlank() }
            "BDAY" -> card.birthday = parseBirthday(value)
        }
    }

    /**
     * vCard escapes \\, \,, \;, \n inside property values. Reverse them in
     * one pass; everything else is taken literally.
     */
    private fun unescape(value: String): String {
        if ('\\' !in value) return value
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val n = value[i + 1]) {
                    '\\' -> sb.append('\\')
                    ',' -> sb.append(',')
                    ';' -> sb.append(';')
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /**
     * BDAY shapes seen in the wild:
     *   1985-04-22       (vCard 3.0, full date)
     *   --04-22          (vCard 3.0, year unknown)
     *   19850422         (vCard 2.1 compact)
     *   --0422           (vCard 2.1, year unknown)
     *   1985-04-22T...   (vCard 4.0, sometimes with timezone) — strip after T
     */
    private fun parseBirthday(value: String): ContactDate? {
        val trimmed = value.substringBefore('T').trim()
        if (trimmed.isEmpty()) return null
        return try {
            when {
                trimmed.startsWith("--") -> {
                    val rest = trimmed.removePrefix("--").replace("-", "")
                    if (rest.length < 4) return null
                    ContactDate(
                        month = rest.substring(0, 2).toInt(),
                        day = rest.substring(2, 4).toInt(),
                        year = null,
                    )
                }
                else -> {
                    val rest = trimmed.replace("-", "")
                    if (rest.length < 8) return null
                    ContactDate(
                        month = rest.substring(4, 6).toInt(),
                        day = rest.substring(6, 8).toInt(),
                        year = rest.substring(0, 4).toInt(),
                    )
                }
            }
        } catch (e: NumberFormatException) {
            null
        }
    }

    private class MutableCard {
        var fn: String? = null
        var n: String? = null
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        var org: String? = null
        var note: String? = null
        var birthday: ContactDate? = null

        fun toParsed(): ParsedVCard? {
            val name = fn ?: nameFromStructuredN(n)
            if (name.isNullOrBlank()) return null
            return ParsedVCard(
                displayName = name.trim(),
                phones = phones.distinct(),
                emails = emails.distinct(),
                organization = org,
                notes = note,
                birthday = birthday,
            )
        }

        /**
         * N is Family;Given;Middle;Prefix;Suffix. Reassemble in the
         * Given Middle Family order most exporters expect when FN is absent.
         */
        private fun nameFromStructuredN(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split(';')
            val family = parts.getOrNull(0).orEmpty().trim()
            val given = parts.getOrNull(1).orEmpty().trim()
            val middle = parts.getOrNull(2).orEmpty().trim()
            return listOf(given, middle, family).filter { it.isNotEmpty() }
                .joinToString(" ")
                .ifBlank { null }
        }
    }
}
