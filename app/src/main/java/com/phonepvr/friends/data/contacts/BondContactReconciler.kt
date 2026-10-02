package com.phonepvr.friends.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.phonepvr.friends.data.db.dao.FavouriteContactDao
import com.phonepvr.friends.data.db.dao.PersonDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps every bond (and pinned favourite) pointing at the address-book contact
 * it was created from, even after that contact is renamed or re-aggregated.
 *
 * Bonds keep the contact's lookup key plus its numeric id. When either goes
 * stale — typically because renaming a local contact changes its lookup key —
 * [reconcile] re-resolves the contact and writes back the current key, id and
 * name, so "is this contact bonded?" checks keep matching (issue #33).
 *
 * A bond whose contact has really gone (deleted) is **never** removed: it holds
 * the user's history. It is reported through [unlinkedPersonIds] so the UI can
 * offer to relink it to another contact, keep it without one, or remove it.
 *
 * All decisions are made by the pure [BondContactMatcher]; this class only
 * reads the address book and applies the resulting plan.
 */
@Singleton
class BondContactReconciler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reader: ContactsReader,
    private val personDao: PersonDao,
    private val favouriteDao: FavouriteContactDao,
) {
    private val mutex = Mutex()
    private var started = false

    private val _unlinkedPersonIds = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * Ids of active bonds whose contact could not be found at the last
     * reconcile. Empty until the first successful run and whenever contacts
     * permission is missing, so nothing is flagged on an unknown.
     */
    val unlinkedPersonIds: StateFlow<Set<Long>> = _unlinkedPersonIds.asStateFlow()

    /**
     * Reconciles now and again whenever the address book changes (debounced, so
     * a sync that touches hundreds of contacts triggers a single pass).
     * Idempotent.
     */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        val changes = Channel<Unit>(Channel.CONFLATED)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                changes.trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            /* notifyForDescendants = */ true,
            observer,
        )
        scope.launch {
            changes.receiveAsFlow().debounce(CHANGE_DEBOUNCE_MS).collect { reconcile() }
        }
        scope.launch { reconcile() }
    }

    /** One reconciliation pass. Safe to call from anywhere, any time. */
    suspend fun reconcile() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    reconcileLocked()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A contacts-provider hiccup must never crash the app or
                    // damage bonds; the next trigger simply tries again.
                }
            }
        }
    }

    private suspend fun reconcileLocked() {
        if (!hasContactsPermission()) {
            _unlinkedPersonIds.value = emptySet()
            return
        }
        val contacts = reader.listContacts()
        val plan = BondContactMatcher.plan(
            people = personDao.getAllWithDetails(),
            favourites = favouriteDao.getAll(),
            contacts = contacts,
            resolveLookupKey = reader::findContactIdByLookupKey,
        )
        val now = System.currentTimeMillis()
        for (u in plan.linkUpdates) {
            personDao.updateContactLink(u.personId, u.lookupKey, u.contactId, u.displayName, now)
        }
        for (u in plan.favouriteUpdates) {
            favouriteDao.rekey(u.oldLookupKey, u.newLookupKey, u.displayName)
        }
        // An empty address book yields an empty plan (see BondContactMatcher.plan),
        // which would wrongly clear the flags; keep the previous state instead.
        if (contacts.isNotEmpty()) _unlinkedPersonIds.value = plan.unlinkedPersonIds
    }

    /**
     * Links bond [personId] to the contact [contactId] (the user picked it to
     * replace a contact that was deleted). Only the link and name change; the
     * bond's cadence, history, numbers and dates are left untouched.
     * Returns false if that contact can't be read.
     */
    suspend fun linkTo(personId: Long, contactId: Long): Boolean {
        val linked = withContext(Dispatchers.IO) {
            val details = reader.readDetails(contactId) ?: return@withContext false
            val person = personDao.getById(personId) ?: return@withContext false
            personDao.updateContactLink(
                id = personId,
                lookupKey = details.lookupKey.takeIf { it.isNotBlank() },
                contactId = contactId,
                displayName = details.displayName.ifBlank { person.displayName },
                now = System.currentTimeMillis(),
            )
            true
        }
        if (linked) reconcile()
        return linked
    }

    /** Detaches bond [personId] from any contact; it stays as a standalone bond. */
    suspend fun detach(personId: Long) {
        withContext(Dispatchers.IO) {
            personDao.clearContactLink(personId, System.currentTimeMillis())
        }
        reconcile()
    }

    private fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val CHANGE_DEBOUNCE_MS = 1_500L
    }
}
