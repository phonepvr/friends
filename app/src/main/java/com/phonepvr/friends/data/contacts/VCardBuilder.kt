package com.phonepvr.friends.data.contacts

/**
 * Builds a vCard 3.0 contact card (RFC 2426) from a contact. Pure Kotlin (no
 * Android deps), so the format is unit-testable. The share flow writes the result
 * to a cache file and hands it to whichever app the user picks — Bondwidth never
 * sends it anywhere itself. WhatsApp, Telegram, email and the stock Contacts app
 * show a card like this as a contact the receiver saves in one tap.
 */
object VCardBuilder {

    fun build(details: ContactDetails): String {
        val lines = mutableListOf("BEGIN:VCARD", "VERSION:3.0")
        lines += "FN:${escape(details.displayName)}"
        // N (structured name) is required by vCard 3.0 and some importers;
        // best-effort split on the first space into family;given.
        val parts = details.displayName.trim().split(" ", limit = 2)
        val given = parts.getOrNull(0).orEmpty()
        val family = parts.getOrNull(1).orEmpty()
        lines += "N:${escape(family)};${escape(given)};;;"
        if (details.phoneEntries.isNotEmpty()) {
            details.phoneEntries.forEach { phone ->
                val types = listOfNotNull(telType(phone.type), "PREF".takeIf { phone.isPrimary })
                lines += "TEL;TYPE=${types.joinToString(",")}:${escape(phone.number)}"
            }
        } else {
            details.phoneNumbers.forEach { number -> lines += "TEL;TYPE=CELL:${escape(number)}" }
        }
        details.emails.forEach { email -> lines += "EMAIL;TYPE=INTERNET:${escape(email)}" }
        details.organization?.takeIf { it.isNotBlank() }?.let { lines += "ORG:${escape(it)}" }
        details.postalAddress?.takeIf { it.isNotBlank() }?.let {
            // Free-form address: the street part of ADR, plus LABEL for display.
            lines += "ADR:;;${escape(it)};;;;"
        }
        details.website?.takeIf { it.isNotBlank() }?.let { lines += "URL:${escape(it)}" }
        details.birthday?.let { bday ->
            // vCard BDAY in --MM-DD form when the year is unknown.
            lines += "BDAY:" + if (bday.year != null) {
                "%04d-%02d-%02d".format(bday.year, bday.month, bday.day)
            } else {
                "--%02d-%02d".format(bday.month, bday.day)
            }
        }
        details.notes?.takeIf { it.isNotBlank() }?.let { lines += "NOTE:${escape(it)}" }
        lines += "END:VCARD"
        // vCard lines end in CRLF; strict importers (iOS, some carriers'
        // MMS gateways) reject a card with bare LF line breaks.
        return lines.joinToString("\r\n", postfix = "\r\n")
    }

    private fun telType(type: PhoneType): String = when (type) {
        PhoneType.MOBILE -> "CELL"
        PhoneType.HOME -> "HOME"
        PhoneType.WORK -> "WORK"
        PhoneType.MAIN, PhoneType.OTHER, PhoneType.CUSTOM -> "VOICE"
    }

    /** Escapes the characters vCard 3.0 reserves in property values. */
    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")

    /**
     * A filesystem-safe filename for the shared .vcf, named after the person in any
     * script (letters with their combining marks, e.g. Devanagari vowel signs).
     */
    fun fileName(displayName: String): String {
        val safe = displayName.trim()
            .replace(Regex("[^\\p{L}\\p{M}\\p{N} _-]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")
            .ifBlank { "contact" }
        return "$safe.vcf"
    }
}

/**
 * The same contact as plain text, for apps that don't take a card (SMS, notes,
 * any chat): the name, then each number with its label, then email. The receiver
 * can tap a number to call or save it.
 */
object ContactShareText {
    fun build(details: ContactDetails, labelFor: (ContactPhone) -> String): String = buildString {
        append(details.displayName.trim())
        val phones = details.phoneEntries.ifEmpty {
            details.phoneNumbers.map { ContactPhone(dataId = 0, number = it, isPrimary = false) }
        }
        phones.forEach { phone ->
            append('\n')
            val label = labelFor(phone).trim()
            if (label.isNotEmpty()) append(label).append(": ")
            append(phone.number)
        }
        details.emails.forEach { append("\nEmail: ").append(it) }
    }
}
