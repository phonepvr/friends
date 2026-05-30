package com.phonepvr.friends.data.incall

import com.phonepvr.friends.data.contacts.ContactsReader
import com.phonepvr.friends.data.db.dao.PersonDao
import com.phonepvr.friends.data.db.dao.PhoneNumberDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a call's number to who's behind it, used by both the in-call
 * header and the ongoing-call notification so they always agree. A bonded
 * person's name wins (that's how the user thinks of them); otherwise it
 * falls back to the system address book via PhoneLookup, which matches any
 * saved contact regardless of bonding.
 */
@Singleton
class CallerIdentityResolver @Inject constructor(
    private val phoneNumberDao: PhoneNumberDao,
    private val personDao: PersonDao,
    private val contactsReader: ContactsReader,
) {
    data class Identity(
        val displayName: String?,
        /** Bonded person's locally-saved photo (preferred for bonds). */
        val photoRelativePath: String?,
        /** System contact photo content URI (fallback for any contact). */
        val photoUri: String?,
        val personId: Long?,
    ) {
        companion object {
            val EMPTY = Identity(null, null, null, null)
        }
    }

    suspend fun resolve(number: String): Identity = withContext(Dispatchers.IO) {
        if (number.isBlank()) return@withContext Identity.EMPTY
        val digits = number.filter(Char::isDigit)
        val bonded = if (digits.length >= 7) {
            phoneNumberDao.findOneByNumberSuffix(digits.takeLast(9))
                ?.personId
                ?.let { personDao.getById(it) }
                ?.takeIf { !it.isArchived }
        } else {
            null
        }
        val lookup = runCatching { contactsReader.lookupByNumber(number) }.getOrNull()
        Identity(
            displayName = bonded?.displayName ?: lookup?.displayName,
            photoRelativePath = bonded?.photoRelativePath,
            photoUri = lookup?.photoUri,
            personId = bonded?.id,
        )
    }
}
