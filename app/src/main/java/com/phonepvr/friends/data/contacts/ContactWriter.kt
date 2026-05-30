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
 * websites, photo, structured given/family name, custom labels, etc.) is
 * preserved untouched when [ContactWriter.update] runs — the writer only
 * deletes + re-inserts the mime types it knows about for the contact's
 * first raw-contact row.
 */
data class ContactForm(
    val displayName: String = "",
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val notes: String = "",
    val organization: String = "",
)

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
     * intact.
     */
    suspend fun update(contactId: Long, form: ContactForm): Boolean =
        withContext(Dispatchers.IO) {
            val rawContactId = reader.firstRawContactId(contactId)
                ?: return@withContext false
            val ops = arrayListOf<ContentProviderOperation>()
            EDITABLE_MIME_TYPES.forEach { mime ->
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
