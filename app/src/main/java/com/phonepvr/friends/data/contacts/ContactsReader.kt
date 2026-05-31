package com.phonepvr.friends.data.contacts

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceContact(
    val contactId: Long,
    val lookupKey: String,
    val displayName: String,
    /** Raw phone strings as stored on the device. Empty if the contact has none. */
    val phoneNumbers: List<String> = emptyList(),
    /** System contact photo content:// URI, or null when the contact has none. */
    val photoUri: String? = null,
)

data class ContactDate(
    val month: Int,
    val day: Int,
    val year: Int?,
)

/** A number resolved to its address-book identity via PhoneLookup. */
data class NumberIdentity(
    val contactId: Long,
    val lookupKey: String?,
    val displayName: String?,
    val photoUri: String?,
)

data class ContactDetails(
    val lookupKey: String,
    val displayName: String,
    val phoneNumbers: List<String>,
    /** Phone rows with their data IDs + which is the contact's default. */
    val phoneEntries: List<ContactPhone> = emptyList(),
    val emails: List<String> = emptyList(),
    val notes: String? = null,
    val organization: String? = null,
    val birthday: ContactDate?,
    val anniversary: ContactDate?,
    /** System contact photo content:// URI, or null when the contact has none. */
    val photoUri: String? = null,
    /** Per-contact ringtone URI (content://...), or null = system default. */
    val customRingtone: String? = null,
)

/** A single phone number of a contact, with its provider Data row id. */
data class ContactPhone(
    val dataId: Long,
    val number: String,
    val isPrimary: Boolean,
)

/** Read-only access to the device address book via ContactsContract. */
@Singleton
class ContactsReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    fun listContacts(): List<DeviceContact> {
        // One sweep over Phone.CONTENT_URI is cheaper than N+1 per-contact
        // queries when the import screen first loads. We then merge by id.
        val phonesByContact = readPhonesByContactId()
        val contacts = mutableListOf<DeviceContact>()
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
            ),
            null,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val keyColumn = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val nameColumn =
                cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoColumn =
                cursor.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn)?.takeIf { it.isNotBlank() } ?: continue
                val id = cursor.getLong(idColumn)
                contacts.add(
                    DeviceContact(
                        contactId = id,
                        lookupKey = cursor.getString(keyColumn).orEmpty(),
                        displayName = name,
                        phoneNumbers = phonesByContact[id].orEmpty(),
                        photoUri = cursor.getString(photoColumn)?.takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
        return contacts
    }

    private fun readPhonesByContactId(): Map<Long, List<String>> {
        val map = HashMap<Long, MutableList<String>>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn =
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val numberColumn =
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val number = cursor.getString(numberColumn)?.trim()?.takeIf { it.isNotBlank() }
                    ?: continue
                map.getOrPut(cursor.getLong(idColumn)) { mutableListOf() }.add(number)
            }
        }
        return map
    }

    fun readDetails(contactId: Long): ContactDetails? {
        var lookupKey = ""
        var displayName = ""
        var photoUri: String? = null
        var customRingtone: String? = null
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.CUSTOM_RINGTONE,
            ),
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                lookupKey = cursor.getString(0).orEmpty()
                displayName = cursor.getString(1).orEmpty()
                photoUri = cursor.getString(2)?.takeIf { it.isNotBlank() }
                customRingtone = cursor.getString(3)?.takeIf { it.isNotBlank() }
            }
        }
        if (displayName.isBlank()) return null

        val phoneEntries = mutableListOf<ContactPhone>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone._ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
            ),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            val idColumn =
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone._ID)
            val numberColumn =
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val superPrimaryColumn =
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY)
            while (cursor.moveToNext()) {
                val number = cursor.getString(numberColumn)?.trim()?.takeIf { it.isNotBlank() }
                    ?: continue
                phoneEntries.add(
                    ContactPhone(
                        dataId = cursor.getLong(idColumn),
                        number = number,
                        isPrimary = cursor.getInt(superPrimaryColumn) != 0,
                    ),
                )
            }
        }
        // Primary number first so callers that take "the" number (favourites,
        // tap-to-call) honour the user's choice.
        val sortedPhones = phoneEntries.sortedByDescending { it.isPrimary }
        // Then collapse rows that refer to the same number under different
        // types (MOBILE + HOME for the same digits, or the same number with
        // and without a country code). Suffix-match on the last 9 digits
        // matches everywhere else in the app and handles +44 7… vs 07… too.
        val dedupedPhones = sortedPhones.dedupByDigits()
        val phoneNumbers = dedupedPhones.map { it.number }

        var birthday: ContactDate? = null
        var anniversary: ContactDate? = null
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Event.START_DATE,
                ContactsContract.CommonDataKinds.Event.TYPE,
            ),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(
                contactId.toString(),
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ),
            null,
        )?.use { cursor ->
            val dateColumn =
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.START_DATE)
            val typeColumn =
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.TYPE)
            while (cursor.moveToNext()) {
                val parsed = parseContactDate(cursor.getString(dateColumn)) ?: continue
                when (cursor.getInt(typeColumn)) {
                    ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY ->
                        if (birthday == null) birthday = parsed
                    ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY ->
                        if (anniversary == null) anniversary = parsed
                }
            }
        }

        val emails = readSingleColumn(
            uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            column = ContactsContract.CommonDataKinds.Email.ADDRESS,
            contactIdColumn = ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            contactId = contactId,
        )
        val notes = readSingleData(
            contactId = contactId,
            mimeType = ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
            column = ContactsContract.CommonDataKinds.Note.NOTE,
        )
        val organization = readSingleData(
            contactId = contactId,
            mimeType = ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
            column = ContactsContract.CommonDataKinds.Organization.COMPANY,
        )

        return ContactDetails(
            lookupKey = lookupKey,
            displayName = displayName,
            phoneNumbers = phoneNumbers,
            phoneEntries = dedupedPhones,
            emails = emails.distinct(),
            notes = notes,
            organization = organization,
            birthday = birthday,
            anniversary = anniversary,
            photoUri = photoUri,
            customRingtone = customRingtone,
        )
    }

    /**
     * Returns the first raw-contact id aggregated under [contactId], or null
     * if none exists. The writer attaches new Data rows to this raw contact
     * when editing (i.e. it edits the first raw contact rather than spreading
     * changes across all of them, which avoids merge conflicts on accounts
     * that re-sync).
     */
    fun firstRawContactId(contactId: Long): Long? {
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            "${ContactsContract.RawContacts._ID} ASC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }

    /**
     * Resolves a stable lookupKey back to a Contacts._ID. The aggregate id
     * can change when contacts merge; lookupKey is the long-lived handle the
     * person table stores, so reading back a contact starts here.
     */
    fun findContactIdByLookupKey(lookupKey: String): Long? {
        if (lookupKey.isBlank()) return null
        val lookupUri = android.net.Uri.withAppendedPath(
            ContactsContract.Contacts.CONTENT_LOOKUP_URI,
            lookupKey,
        )
        // lookupContact follows the lookup key through any merges and
        // returns the current canonical Contacts URI.
        val canonical = runCatching {
            ContactsContract.Contacts.lookupContact(resolver, lookupUri)
        }.getOrNull() ?: return null
        return runCatching { android.content.ContentUris.parseId(canonical) }.getOrNull()
    }

    private fun readSingleColumn(
        uri: android.net.Uri,
        column: String,
        contactIdColumn: String,
        contactId: Long,
    ): List<String> {
        val list = mutableListOf<String>()
        resolver.query(
            uri,
            arrayOf(column),
            "$contactIdColumn = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            val col = cursor.getColumnIndexOrThrow(column)
            while (cursor.moveToNext()) {
                cursor.getString(col)?.trim()?.takeIf { it.isNotBlank() }?.let { list.add(it) }
            }
        }
        return list
    }

    private fun readSingleData(contactId: Long, mimeType: String, column: String): String? {
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(column),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId.toString(), mimeType),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)?.trim()?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    /**
     * Resolves a raw phone number to its system-contact identity via
     * ContactsContract.PhoneLookup — the canonical "who is this number"
     * query that matches ANY saved contact (not just bonded people) and
     * handles number formatting / country-code differences itself.
     * Returns null when the number isn't in the address book or
     * READ_CONTACTS isn't granted.
     */
    fun lookupByNumber(number: String): NumberIdentity? {
        if (number.isBlank()) return null
        val uri = android.net.Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(number),
        )
        return runCatching {
            resolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.LOOKUP_KEY,
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    NumberIdentity(
                        contactId = cursor.getLong(0),
                        lookupKey = cursor.getString(1),
                        displayName = cursor.getString(2)?.takeIf { it.isNotBlank() },
                        photoUri = cursor.getString(3)?.takeIf { it.isNotBlank() },
                    )
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    /** Opens the contact's photo for reading, or null when it has none. */
    fun openContactPhoto(contactId: Long): InputStream? {
        val contactUri = ContentUris.withAppendedId(
            ContactsContract.Contacts.CONTENT_URI,
            contactId,
        )
        return ContactsContract.Contacts.openContactPhotoInputStream(
            resolver,
            contactUri,
            true,
        )
    }

    /** Parses contact event dates in "yyyy-MM-dd" or "--MM-dd" (year unknown) form. */
    private fun parseContactDate(raw: String?): ContactDate? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return try {
            if (value.startsWith("--")) {
                val parts = value.removePrefix("--").split("-")
                if (parts.size < 2) return null
                ContactDate(parts[0].toInt(), parts[1].toInt(), null)
            } else {
                val parts = value.split("-")
                if (parts.size < 3) return null
                ContactDate(parts[1].toInt(), parts[2].toInt(), parts[0].toIntOrNull())
            }
        } catch (e: NumberFormatException) {
            null
        }
    }
}

/**
 * Collapse phone entries that point at the same number under different
 * types or formats. Suffix-match on the last 9 digits — the same key the
 * call-history / dialer match use — so "+44 7700 900000" and "07700 900000"
 * land in the same bucket. Short codes (911, 411) digit-collapse to
 * themselves and stay distinct. Within each group the first entry wins;
 * the caller pre-sorts so the primary-tagged row comes first.
 */
private fun List<ContactPhone>.dedupByDigits(): List<ContactPhone> {
    val seen = HashSet<String>()
    val out = ArrayList<ContactPhone>(size)
    for (entry in this) {
        val digits = entry.number.filter(Char::isDigit)
        val key = if (digits.length >= 4) digits.takeLast(9) else entry.number
        if (seen.add(key)) out += entry
    }
    return out
}
