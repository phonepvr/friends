package com.phonepvr.friends.data.contacts

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Editable subset of a contact. Anything not represented here (addresses,
 * websites, structured given/family name, custom labels, etc.) is preserved
 * untouched when [ContactWriter.update] runs — the writer only deletes +
 * re-inserts the mime types it knows about for the contact's first
 * raw-contact row. Photo is special-cased through [photoChange].
 */
data class ContactForm(
    val displayName: String = "",
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val notes: String = "",
    val organization: String = "",
    /** Birthday to write on create; ignored on update so existing dates survive. */
    val birthday: ContactDate? = null,
    /** What to do with the contact's photo on save. Defaults to "leave it". */
    val photoChange: PhotoChange = PhotoChange.Unchanged,
)

/** What [ContactWriter] should do with the photo on save. */
sealed interface PhotoChange {
    /** No change — leave whatever photo the contact already has. */
    data object Unchanged : PhotoChange

    /** Delete the contact's photo. */
    data object Remove : PhotoChange

    /** Write [bytes] as the new photo (a JPEG, already scaled). */
    data class Replace(val bytes: ByteArray) : PhotoChange {
        // ByteArray identity-equality is wrong for data classes — give
        // structural equals + hashCode so the form participates in
        // copy() comparisons cleanly.
        override fun equals(other: Any?): Boolean =
            other is Replace && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = bytes.contentHashCode()
    }
}

/** Result of a successful create — both ids the caller needs to auto-track. */
data class CreatedContact(val contactId: Long, val lookupKey: String)

/**
 * Writes contacts back to the system Contacts provider. The detail screen
 * uses this for edit + delete; the browser uses it for create from the FAB.
 *
 * v1 scope: name + phones + emails + notes + organization. v1 doesn't
 * touch addresses, websites, structured names, photos, custom labels —
 * those round-trip through whichever app the user used to set them.
 */
@Singleton
class ContactWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reader: ContactsReader,
) {
    private val resolver get() = context.contentResolver

    /**
     * Inserts a new contact and returns its aggregate id + lookup key. A
     * null return means the batch failed (read-only authority, OOM, etc.)
     * — the UI surfaces a generic error.
     */
    suspend fun create(form: ContactForm): CreatedContact? = withContext(Dispatchers.IO) {
        val ops = arrayListOf<ContentProviderOperation>()
        // Local-only contact (no account sync). Routing through a Google /
        // SIM account would push the contact to the cloud, which the
        // privacy posture explicitly avoids. The user can move it manually
        // in their other contacts app if they want sync.
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build(),
        )
        addDataRows(ops, form) { it.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0) }
        val results = runCatching {
            resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        }.getOrNull() ?: return@withContext null
        val rawContactUri = results.firstOrNull()?.uri ?: return@withContext null
        val rawContactId = ContentUris.parseId(rawContactUri)
        val contactId = rawContactToContactId(rawContactId) ?: return@withContext null
        val lookupKey = lookupKeyOf(contactId).orEmpty()
        CreatedContact(contactId = contactId, lookupKey = lookupKey)
    }

    /**
     * Replaces the editable mime types for the contact's first raw row,
     * leaving anything else (addresses, websites, custom-typed phones, etc.)
     * intact. The Photo mime type is only deleted when [form.photoChange] is
     * Remove or Replace — Unchanged keeps the existing picture intact.
     */
    suspend fun update(contactId: Long, form: ContactForm): Boolean =
        withContext(Dispatchers.IO) {
            val rawContactId = reader.firstRawContactId(contactId)
                ?: return@withContext false
            val ops = arrayListOf<ContentProviderOperation>()
            val mimeTypesToDelete = if (form.photoChange == PhotoChange.Unchanged) {
                EDITABLE_MIME_TYPES
            } else {
                EDITABLE_MIME_TYPES + ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
            }
            mimeTypesToDelete.forEach { mime ->
                ops.add(
                    ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                        .withSelection(
                            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND " +
                                "${ContactsContract.Data.MIMETYPE} = ?",
                            arrayOf(rawContactId.toString(), mime),
                        )
                        .build(),
                )
            }
            addDataRows(ops, form) {
                it.withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            }
            runCatching {
                resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            }.isSuccess
        }

    suspend fun delete(contactId: Long, lookupKey: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
                resolver.delete(uri, null, null) > 0
            }.getOrElse { false }
        }

    /**
     * Marks the phone Data row [dataId] as the contact's default number by
     * setting IS_SUPER_PRIMARY (+ IS_PRIMARY). The provider clears the flag
     * on the contact's other numbers automatically, so this both promotes
     * the chosen number and demotes the rest. Needs WRITE_CONTACTS.
     */
    suspend fun setPrimaryNumber(dataId: Long): Boolean = withContext(Dispatchers.IO) {
        val values = android.content.ContentValues().apply {
            put(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
            put(ContactsContract.Data.IS_PRIMARY, 1)
        }
        runCatching {
            resolver.update(
                ContactsContract.Data.CONTENT_URI,
                values,
                "${ContactsContract.Data._ID} = ?",
                arrayOf(dataId.toString()),
            ) > 0
        }.getOrElse { false }
    }

    private fun addDataRows(
        ops: ArrayList<ContentProviderOperation>,
        form: ContactForm,
        attach: (ContentProviderOperation.Builder) -> ContentProviderOperation.Builder,
    ) {
        if (form.displayName.isNotBlank()) {
            ops.add(
                attach(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI))
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                        form.displayName.trim(),
                    )
                    .build(),
            )
        }
        form.phones.map { it.trim() }.filter { it.isNotBlank() }.forEach { number ->
            ops.add(
                attach(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI))
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                    )
                    .build(),
            )
        }
        form.emails.map { it.trim() }.filter { it.isNotBlank() }.forEach { address ->
            ops.add(
                attach(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI))
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                    )
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, address)
                    .withValue(
                        ContactsContract.CommonDataKinds.Email.TYPE,
                        ContactsContract.CommonDataKinds.Email.TYPE_HOME,
                    )
                    .build(),
            )
        }
        form.notes.trim().takeIf { it.isNotBlank() }?.let { notes ->
            ops.add(
                attach(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI))
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
                    )
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, notes)
                    .build(),
            )
        }
        form.organization.trim().takeIf { it.isNotBlank() }?.let { company ->
            ops.add(
                attach(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI))
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                    )
                    .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, company)
                    .build(),
            )
        }
        form.birthday?.let { bday ->
            // ContactsContract stores birthdays as Event rows with
            // TYPE_BIRTHDAY. START_DATE accepts "yyyy-MM-dd" or "--MM-dd"
            // when the year is unknown — the same shapes vCard uses.
            val date = if (bday.year != null) {
                "%04d-%02d-%02d".format(bday.year, bday.month, bday.day)
            } else {
                "--%02d-%02d".format(bday.month, bday.day)
            }
            ops.add(
                attach(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI))
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                    )
                    .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, date)
                    .withValue(
                        ContactsContract.CommonDataKinds.Event.TYPE,
                        ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY,
                    )
                    .build(),
            )
        }
        (form.photoChange as? PhotoChange.Replace)?.let { replace ->
            ops.add(
                attach(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI))
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE,
                    )
                    // The provider takes the raw bytes and generates its own
                    // thumbnail + full-size storage on the receiving end, so
                    // we just hand it the scaled JPEG.
                    .withValue(
                        ContactsContract.CommonDataKinds.Photo.PHOTO,
                        replace.bytes,
                    )
                    .build(),
            )
        }
    }

    private fun rawContactToContactId(rawContactId: Long): Long? {
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.CONTACT_ID),
            "${ContactsContract.RawContacts._ID} = ?",
            arrayOf(rawContactId.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }

    private fun lookupKeyOf(contactId: Long): String? {
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.LOOKUP_KEY),
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    companion object {
        private val EDITABLE_MIME_TYPES = listOf(
            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
        )
    }
}
