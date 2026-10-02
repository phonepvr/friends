package com.phonepvr.friends.data.contacts

import java.text.Normalizer

/**
 * Matching rules for the contacts search box (#29). A contact matches when the
 * query is found in its name, in any of its phone numbers, or in its "extra" text
 * (organisation, nickname, notes, email addresses, address, website).
 *
 * Text is compared case- and accent-insensitively ("jose" finds "José"). A query
 * only counts as a phone-number search when it looks like a number (digits plus
 * the usual separators): typing "gmail2" must not match every contact whose number
 * happens to contain a 2.
 */
object ContactSearch {

    /** Lower-cases [text] and strips accents, so the two sides compare equal. */
    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase()

    /**
     * @param extraText the contact's other searchable fields, already passed through
     *   [fold] (it is built once per search session, not per keystroke); null when
     *   they haven't been loaded yet.
     */
    fun matches(
        query: String,
        displayName: String,
        phoneNumbers: List<String>,
        extraText: String?,
    ): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        val folded = fold(trimmed)
        if (fold(displayName).contains(folded)) return true
        if (isPhoneQuery(trimmed)) {
            val digits = trimmed.filter { it.isDigit() }
            if (phoneNumbers.any { number -> number.filter { it.isDigit() }.contains(digits) }) {
                return true
            }
        }
        return extraText?.contains(folded) == true
    }

    private fun isPhoneQuery(query: String): Boolean =
        query.any { it.isDigit() } && query.all { it.isDigit() || it in PHONE_SEPARATORS }

    private const val PHONE_SEPARATORS = " +-().#*"
    private val COMBINING_MARKS = Regex("\\p{M}+")
}
