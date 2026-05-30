package com.phonepvr.friends.data.contacts

/**
 * Builds a vCard 3.0 string from a contact. Pure Kotlin (no Android deps),
 * so the escaping rules are unit-testable. The on-device share flow writes
 * the result to a cache file and hands it to whichever app the user picks —
 * Bondwidth never sends it anywhere itself.
 */
object VCardBuilder {

    fun build(details: ContactDetails): String = buildString {
        appendLine("BEGIN:VCARD")
        appendLine("VERSION:3.0")
        appendLine("FN:${escape(details.displayName)}")
        // N (structured name) is required by some importers; best-effort
        // split on the first space into family;given.
        val parts = details.displayName.trim().split(" ", limit = 2)
        val given = parts.getOrNull(0).orEmpty()
        val family = parts.getOrNull(1).orEmpty()
        appendLine("N:${escape(family)};${escape(given)};;;")
        details.phoneNumbers.forEach { number ->
            appendLine("TEL;TYPE=CELL:${escape(number)}")
        }
        details.emails.forEach { email ->
            appendLine("EMAIL;TYPE=INTERNET:${escape(email)}")
        }
        details.organization?.takeIf { it.isNotBlank() }?.let {
            appendLine("ORG:${escape(it)}")
        }
        details.birthday?.let { bday ->
            // vCard BDAY in --MM-DD form when the year is unknown.
            val date = if (bday.year != null) {
                "%04d-%02d-%02d".format(bday.year, bday.month, bday.day)
            } else {
                "--%02d-%02d".format(bday.month, bday.day)
            }
            appendLine("BDAY:$date")
        }
        details.notes?.takeIf { it.isNotBlank() }?.let {
            appendLine("NOTE:${escape(it)}")
        }
        append("END:VCARD")
    }

    /** Escapes the characters vCard 3.0 reserves in property values. */
    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")

    /** A filesystem-safe filename for the shared .vcf. */
    fun fileName(displayName: String): String {
        val safe = displayName.trim()
            .replace(Regex("[^A-Za-z0-9 _-]"), "")
            .replace(" ", "_")
            .ifBlank { "contact" }
        return "$safe.vcf"
    }
}
