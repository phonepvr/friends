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
}
