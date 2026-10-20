package com.phonepvr.friends.data.reachout

/**
 * The number chat apps need. WhatsApp's wa.me links and Signal's signal.me links
 * only work with the full international number (country code included); a local
 * number such as "098765 43210" opens the wrong chat or none. Pure so it can be
 * unit-tested; the platform's E.164 formatter is passed in.
 */
object ChatNumber {

    /**
     * International digits, without '+', for [raw] as typed or saved:
     * - "+44 20 7946 0018" or "0044 20 7946 0018" -> "442079460018";
     * - a local number gets the country code of [countryIso] (the SIM's country)
     *   via [toE164], e.g. "098765 43210" in IN -> "919876543210";
     * - anything [toE164] can't place (no country known, or a number already
     *   written with its country code but no '+') is kept as typed, digits only;
     * - null when there are no digits at all.
     */
    fun internationalDigits(
        raw: String,
        countryIso: String?,
        toE164: (number: String, countryIso: String) -> String?,
    ): String? {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it in '0'..'9' }
        if (digits.isEmpty()) return null
        if (trimmed.startsWith("+")) return digits
        if (digits.startsWith("00") && digits.length > 4) return digits.drop(2)
        val iso = countryIso?.trim()?.uppercase()?.takeIf { it.length == 2 }
        if (iso != null) {
            toE164(trimmed, iso)
                ?.filter { it in '0'..'9' }
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        return digits
    }
}
