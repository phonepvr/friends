package com.phonepvr.friends.data.contacts

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flow-based wrapper around [ContactsReader] for the all-in-one Contacts
 * browser. Emits whenever the system Contacts provider changes so the
 * UI never goes stale when the user (or another app) edits a contact.
 *
 * Read-only — writes go through ContentProviderOperation builders in a
 * later phase. The browser only needs to render and offer "Track in
 * Bondwidth"; the actual editing surface (Phase 2) writes back via a
 * separate path.
 */
@Singleton
class SystemContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reader: ContactsReader,
) {
    fun observeAll(): Flow<List<DeviceContact>> = callbackFlow {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(reader.listContacts())
            }
        }
        resolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            /* notifyForDescendants = */ true,
            observer,
        )
        trySend(reader.listContacts())
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO)

    suspend fun details(contactId: Long): ContactDetails? =
        withContext(Dispatchers.IO) { reader.readDetails(contactId) }

    /** User-visible contact-group titles for the browser's group filter. */
    suspend fun listGroupTitles(): List<String> =
        withContext(Dispatchers.IO) { reader.listGroupTitles() }

    /** Contact ids that belong to the group titled [title]. */
    suspend fun contactIdsInGroup(title: String): Set<Long> =
        withContext(Dispatchers.IO) { reader.contactIdsInGroup(title) }

    /** Clusters of likely-duplicate contacts (same normalised name). */
    suspend fun findDuplicateClusters(): List<DuplicateFinder.Cluster> =
        withContext(Dispatchers.IO) {
            DuplicateFinder.find(
                reader.listContacts().map {
                    DuplicateFinder.Member(it.contactId, it.displayName, it.phoneNumbers)
                },
            )
        }

    /**
     * Convenience for the bonded surfaces, which keep the contact's
     * lookupKey (stable across aggregate-id changes) rather than the
     * numeric id. Returns null if the contact has been deleted or the
     * lookup key never matched anything.
     */
    suspend fun detailsByLookupKey(lookupKey: String): Pair<Long, ContactDetails>? =
        withContext(Dispatchers.IO) {
            val id = reader.findContactIdByLookupKey(lookupKey) ?: return@withContext null
            val details = reader.readDetails(id) ?: return@withContext null
            id to details
        }
}
