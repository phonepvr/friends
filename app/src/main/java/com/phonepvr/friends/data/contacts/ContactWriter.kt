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
    val phones: List<PhoneEntry> = emptyList(),
    val emails: List<EmailEntry> = emptyList(),
    val notes: String = "",
    val organization: String = "",
    /** Free-text postal address (single). Blank means "don't write one". */
    val postalAddress: String = "",
    /** Website / homepage URL (single). Blank means "don't write one". */
    val website: String = "",
    /** Birthday to write on create; ignored on update so existing dates survive. */
    val birthday: ContactDate? = null,
    /** What to do with the contact's photo on save. Defaults to "leave it". */
    val photoChange: PhotoChange = PhotoChange.Unchanged,
)

/**
 * One editable email row: the address, the chosen [type], and a [customLabel]
 * used when type is [EmailType.CUSTOM]. Round-trips through [ContactWriter]
 * so editing a contact no longer flattens every address to TYPE_HOME on save.
 */
data class EmailEntry(
    val address: String,
    val type: EmailType = EmailType.HOME,
    val customLabel: String? = null,
)

/** Email-type labels exposed in the editor — the common subset of
 *  ContactsContract.CommonDataKinds.Email.TYPE_* people actually use. */
enum class EmailType { HOME, WORK, OTHER, CUSTOM }

/**
 * One editable phone number row: the digits, the chosen [type], and a
 * [customLabel] used when type is [PhoneType.CUSTOM]. Round-trips through
 * [ContactWriter] so editing a contact no longer flattens "Home" / "Work" /
 * "Mobile" labels into a single TYPE_MOBILE on save.
 */
data class PhoneEntry(
    val number: String,
    val type: PhoneType = PhoneType.MOBILE,
    val customLabel: String? = null,
)

/** The phone-type labels exposed in the editor. Mirrors the common subset of
 *  ContactsContract.CommonDataKinds.Phone.TYPE_* that everyone actually uses. */
enum class PhoneType { MOBILE, HOME, WORK, MAIN, OTHER, CUSTOM }

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
        // Photo is streamed in after the raw contact exists (see update()),
        // not inserted as a Data blob in the batch.
        (form.photoChange as? PhotoChange.Replace)?.let {
            writeDisplayPhoto(rawContactId, it.bytes)
        }
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
            // The photo is written separately (see writeDisplayPhoto) via the
            // DisplayPhoto asset stream, not as a Data-row blob — that's the
            // reliable path on aggregated / synced contacts. The batch only
            // deletes the old photo row when we're replacing or removing.
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
            val batchOk = runCatching {
                resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            }.isSuccess
            if (!batchOk) return@withContext false
            // Now stream the new photo (if any) onto the raw contact. A failure
            // here shouldn't fail the whole save — the text fields are already
            // written — but Replace failing is the user's headline action, so
            // report it.
            when (val change = form.photoChange) {
                is PhotoChange.Replace -> writeDisplayPhoto(rawContactId, change.bytes)
                else -> true
            }
        }

    /**
     * Writes [bytes] as the raw contact's photo through the DisplayPhoto
     * asset stream. The provider stores the full-size image, regenerates the
     * thumbnail, and refreshes the aggregate contact's PHOTO_URI — none of
     * which a plain Photo.PHOTO data-row blob reliably triggers once a
     * contact is aggregated or account-synced.
     */
    private fun writeDisplayPhoto(rawContactId: Long, bytes: ByteArray): Boolean {
        val rawContactUri = ContentUris.withAppendedId(
            ContactsContract.RawContacts.CONTENT_URI,
            rawContactId,
        )
        val photoUri = android.net.Uri.withAppendedPath(
            rawContactUri,
            ContactsContract.RawContacts.DisplayPhoto.CONTENT_DIRECTORY,
        )
        return runCatching {
            resolver.openAssetFileDescriptor(photoUri, "rw")?.use { fd ->
                fd.createOutputStream().use { it.write(bytes) }
            }
            true
        }.getOrElse { false }
    }

    /**
     * Writes [ringtoneUri] (or null = system default) to the contact's
     * CUSTOM_RINGTONE column. Lives on the Contacts row itself rather than
     * in a Data sub-row, so we update the Contacts content URI directly.
     */
    suspend fun setCustomRingtone(contactId: Long, ringtoneUri: android.net.Uri?): Boolean =
        withContext(Dispatchers.IO) {
            val values = android.content.ContentValues().apply {
                if (ringtoneUri == null) {
                    putNull(ContactsContract.Contacts.CUSTOM_RINGTONE)
                } else {
                    put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtoneUri.toString())
                }
            }
            val uri = ContentUris.withAppendedId(
                ContactsContract.Contacts.CONTENT_URI,
                contactId,
            )
            runCatching {
                resolver.update(uri, values, null, null) > 0
            }.getOrElse { false }
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
        form.phones
            .map { it.copy(number = it.number.trim()) }
            .filter { it.number.isNotBlank() }
            .forEach { entry ->
                val builder = attach(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI),
                )
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, entry.number)
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        entry.type.toContactsContractType(),
                    )
                if (entry.type == PhoneType.CUSTOM) {
                    // Provider requires a non-null LABEL when TYPE is CUSTOM.
                    val label = entry.customLabel?.trim().orEmpty()
                    builder.withValue(ContactsContract.CommonDataKinds.Phone.LABEL, label)
                }
                ops.add(builder.build())
            }
        form.emails
            .map { it.copy(address = it.address.trim()) }
            .filter { it.address.isNotBlank() }
            .forEach { entry ->
                val builder = attach(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI),
                )
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                    )
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, entry.address)
                    .withValue(
                        ContactsContract.CommonDataKinds.Email.TYPE,
                        entry.type.toContactsContractType(),
                    )
                if (entry.type == EmailType.CUSTOM) {
                    val label = entry.customLabel?.trim().orEmpty()
                    builder.withValue(ContactsContract.CommonDataKinds.Email.LABEL, label)
                }
                ops.add(builder.build())
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
        form.postalAddress.trim().takeIf { it.isNotBlank() }?.let { address ->
            ops.add(
                attach(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI))
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
                        address,
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
                        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME,
                    )
                    .build(),
            )
        }
        form.website.trim().takeIf { it.isNotBlank() }?.let { url ->
            ops.add(
                attach(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI))
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE,
                    )
                    .withValue(ContactsContract.CommonDataKinds.Website.URL, url)
                    .withValue(
                        ContactsContract.CommonDataKinds.Website.TYPE,
                        ContactsContract.CommonDataKinds.Website.TYPE_HOMEPAGE,
                    )
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
        // Photo is intentionally NOT added here — it's streamed onto the raw
        // contact via writeDisplayPhoto() after this batch, which is the
        // reliable path for aggregated / synced contacts.
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
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE,
        )
    }
}

/** Map our editor-facing [PhoneType] to ContactsContract's int TYPE column. */
internal fun PhoneType.toContactsContractType(): Int = when (this) {
    PhoneType.MOBILE -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
    PhoneType.HOME -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
    PhoneType.WORK -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
    PhoneType.MAIN -> ContactsContract.CommonDataKinds.Phone.TYPE_MAIN
    PhoneType.OTHER -> ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
    PhoneType.CUSTOM -> ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM
}

/** Inverse: from ContactsContract int TYPE to our enum. Unknown types fall
 *  through to OTHER so we don't lose the number — just the niche label. */
internal fun phoneTypeFromContactsContract(type: Int): PhoneType = when (type) {
    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> PhoneType.MOBILE
    ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> PhoneType.HOME
    ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> PhoneType.WORK
    ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> PhoneType.MAIN
    ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> PhoneType.CUSTOM
    else -> PhoneType.OTHER
}

/** Map our editor-facing [EmailType] to ContactsContract's int TYPE column. */
internal fun EmailType.toContactsContractType(): Int = when (this) {
    EmailType.HOME -> ContactsContract.CommonDataKinds.Email.TYPE_HOME
    EmailType.WORK -> ContactsContract.CommonDataKinds.Email.TYPE_WORK
    EmailType.OTHER -> ContactsContract.CommonDataKinds.Email.TYPE_OTHER
    EmailType.CUSTOM -> ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM
}

/** Inverse: ContactsContract int TYPE to our enum; unknown → OTHER. */
internal fun emailTypeFromContactsContract(type: Int): EmailType = when (type) {
    ContactsContract.CommonDataKinds.Email.TYPE_HOME -> EmailType.HOME
    ContactsContract.CommonDataKinds.Email.TYPE_WORK -> EmailType.WORK
    ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM -> EmailType.CUSTOM
    else -> EmailType.OTHER
}
