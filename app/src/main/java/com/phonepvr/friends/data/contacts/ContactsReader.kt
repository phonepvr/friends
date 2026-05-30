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
)

data class ContactDate(
    val month: Int,
    val day: Int,
    val year: Int?,
)

data class ContactDetails(
    val lookupKey: String,
    val displayName: String,
    val phoneNumbers: List<String>,
    val emails: List<String> = emptyList(),
    val notes: String? = null,
    val organization: String? = null,
    val birthday: ContactDate?,
    val anniversary: ContactDate?,
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
            ),
            null,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val keyColumn = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val nameColumn =
                cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn)?.takeIf { it.isNotBlank() } ?: continue
                val id = cursor.getLong(idColumn)
                contacts.add(
                    DeviceContact(
                        contactId = id,
                        lookupKey = cursor.getString(keyColumn).orEmpty(),
                        displayName = name,
                        phoneNumbers = phonesByContact[id].orEmpty(),
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
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ),
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                lookupKey = cursor.getString(0).orEmpty()
                displayName = cursor.getString(1).orEmpty()
            }
        }
        if (displayName.isBlank()) return null

        val phoneNumbers = mutableListOf<String>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            val numberColumn =
                cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                cursor.getString(numberColumn)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { phoneNumbers.add(it) }
            }
        }

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
            phoneNumbers = phoneNumbers.distinct(),
            emails = emails.distinct(),
            notes = notes,
            organization = organization,
            birthday = birthday,
            anniversary = anniversary,
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
