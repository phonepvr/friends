package com.phonepvr.friends.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
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
    /**
     * All contacts, re-emitted whenever the address book changes. Without contacts
     * permission it emits an empty list once: observing or querying the contacts
     * provider without it throws SecurityException on Android 8+, and some callers
     * (call history, call analytics) subscribe without checking first.
     */
    fun observeAll(): Flow<List<DeviceContact>> = callbackFlow {
        if (!hasContactsPermission()) {
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(listContactsOrEmpty())
            }
        }
        val registered = registerContactsObserver(observer)
        trySend(listContactsOrEmpty())
        awaitClose { if (registered) resolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO)

    suspend fun details(contactId: Long): ContactDetails? =
        withContext(Dispatchers.IO) { reader.readDetails(contactId) }

    /**
     * Each contact's other searchable text (organisation, nickname, notes, emails,
     * address, website), already folded for [ContactSearch.matches]. Loaded on
     * demand — it reads every contact's notes — so only while a search is active.
     */
    suspend fun searchText(): Map<Long, String> =
        withContext(Dispatchers.IO) {
            reader.readSearchText().mapValues { ContactSearch.fold(it.value) }
        }

    /** The groups [contactId] can join and the ones it is in; see [ContactsReader.editableGroupsFor]. */
    suspend fun groupState(contactId: Long): ContactGroupState =
        withContext(Dispatchers.IO) { runCatching { reader.editableGroupsFor(contactId) }.getOrDefault(ContactGroupState()) }

    /**
     * Emits once immediately and again whenever the system contacts change (which
     * includes group-membership edits). A cheap trigger for things that must stay
     * current without re-reading the whole address book.
     */
    fun observeChanges(): Flow<Unit> = callbackFlow {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        // Without permission there is nothing to watch; emit once and stay quiet.
        val registered = hasContactsPermission() && registerContactsObserver(observer)
        trySend(Unit)
        awaitClose { if (registered) resolver.unregisterContentObserver(observer) }
    }

    private fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** False if the provider refused (permission revoked since the check). */
    private fun registerContactsObserver(observer: ContentObserver): Boolean =
        try {
            context.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                /* notifyForDescendants = */ true,
                observer,
            )
            true
        } catch (e: SecurityException) {
            false
        }

    private fun listContactsOrEmpty(): List<DeviceContact> =
        try {
            reader.listContacts()
        } catch (e: SecurityException) {
            emptyList()
        }

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
