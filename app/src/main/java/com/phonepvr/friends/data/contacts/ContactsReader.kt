package com.phonepvr.friends.data.contacts

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceContact(
    val contactId: Long,
    val lookupKey: String,
    val displayName: String,
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
                contacts.add(
                    DeviceContact(
                        contactId = cursor.getLong(idColumn),
                        lookupKey = cursor.getString(keyColumn).orEmpty(),
                        displayName = name,
                    ),
                )
            }
        }
        return contacts
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

        return ContactDetails(
            lookupKey = lookupKey,
            displayName = displayName,
            phoneNumbers = phoneNumbers.distinct(),
            birthday = birthday,
            anniversary = anniversary,
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
