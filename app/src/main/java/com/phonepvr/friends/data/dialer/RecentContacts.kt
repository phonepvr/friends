package com.phonepvr.friends.data.dialer

import com.phonepvr.friends.data.calllog.DeviceCall
import com.phonepvr.friends.data.contacts.DeviceContact

/**
 * Turns the call log into "people you talked to lately": the saved contacts behind
 * recent calls, most recent first, each listed once. Used to offer a short "Recent"
 * list when picking someone to bond with (#34), so the user doesn't have to scroll
 * the whole address book for the person they just spoke to.
 *
 * Numbers are matched on their last [SUFFIX_DIGITS] digits so a "+44 7700 900123"
 * in the log still finds the contact saved as "07700 900123". Calls from numbers
 * that aren't in the address book (or are hidden/too short to compare) are skipped.
 */
object RecentContacts {

    /** Plenty for a quick-pick list without turning it into a second contact list. */
    const val DEFAULT_LIMIT = 15

    private const val SUFFIX_DIGITS = 9
    private const val MIN_DIGITS = 4

    fun fromCalls(
        calls: List<DeviceCall>,
        contacts: List<DeviceContact>,
        limit: Int = DEFAULT_LIMIT,
    ): List<DeviceContact> {
        if (calls.isEmpty() || contacts.isEmpty() || limit <= 0) return emptyList()

        val byNumber = HashMap<String, DeviceContact>()
        for (contact in contacts) {
            for (number in contact.phoneNumbers) {
                val key = suffix(number) ?: continue
                byNumber.putIfAbsent(key, contact)
            }
        }

        val recent = LinkedHashMap<Long, DeviceContact>()
        for (call in calls.sortedByDescending { it.timestampMillis }) {
            val key = suffix(call.number) ?: continue
            val contact = byNumber[key] ?: continue
            if (recent.putIfAbsent(contact.contactId, contact) == null && recent.size >= limit) break
        }
        return recent.values.toList()
    }

    private fun suffix(number: String): String? =
        T9.digitsOnly(number).takeLast(SUFFIX_DIGITS).takeIf { it.length >= MIN_DIGITS }
}
