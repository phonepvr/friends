package com.phonepvr.friends.data.dialer

/**
 * Recognises what's typed on the dialpad when it isn't a phone number.
 *
 * - A USSD or MMI code (`*100#`, `*#06#`, `#31#…`) contains `*` or `#`. It must be
 *   dialled exactly as typed: never swapped for a contact whose number or name
 *   happens to match its digits (issue #71).
 * - A service code (`*#*#4636#*#*`) isn't dialled at all. Phone apps hand the
 *   digits between the markers to Android, which opens the matching hidden menu.
 */
object DialCodes {

    private val SERVICE_CODE = Regex("""^\*#\*#([0-9A-Za-z]+)#\*#\*$""")

    /** True when [input] is a USSD/MMI or service code rather than a number. */
    fun isCode(input: String): Boolean = input.any { it == '*' || it == '#' }

    /** The digits of a `*#*#…#*#*` service code, or null if [input] isn't one. */
    fun serviceCode(input: String): String? =
        SERVICE_CODE.matchEntire(input.trim())?.groupValues?.get(1)
}
