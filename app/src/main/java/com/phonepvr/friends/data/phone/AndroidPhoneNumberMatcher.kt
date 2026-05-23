package com.phonepvr.friends.data.phone

import android.telephony.PhoneNumberUtils

/**
 * Strict, locale-aware phone-number comparison. Use as a confirmation step
 * after the cheap suffix prefilter in
 * [com.phonepvr.friends.domain.phone.PhoneNumberMatcher] narrows the candidate
 * set — by itself this is too lenient about country codes for pre-indexing
 * and too expensive to run pairwise across the whole address book.
 *
 * Lives in `data/` rather than `domain/` because it depends on the Android
 * SDK and would otherwise pollute the pure-Kotlin domain module.
 */
object AndroidPhoneNumberMatcher {

    /**
     * True when [a] and [b] are the same phone number under
     * [PhoneNumberUtils.compare]. Returns false for blank inputs rather than
     * relying on unspecified platform behaviour.
     */
    fun strictMatches(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        return PhoneNumberUtils.compare(a, b)
    }
}
