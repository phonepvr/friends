package com.phonepvr.friends.data.contacts

import com.phonepvr.friends.data.db.dao.PersonDao
import com.phonepvr.friends.data.db.entity.EventEntity
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.db.entity.PhoneNumberEntity
import com.phonepvr.friends.data.photo.PhotoStorage
import com.phonepvr.friends.data.repository.CallLogAutoSync
import com.phonepvr.friends.data.repository.PeopleRepository
import com.phonepvr.friends.data.settings.SettingsRepository
import com.phonepvr.friends.domain.model.EventType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single source of truth for the "Track in Bondwidth" toggle on a
 * system contact. Both the legacy import flow and the new Contacts
 * detail screen call into here so the rules stay consistent:
 *
 *  - Tracking an already-tracked contact is a no-op.
 *  - Re-tracking a previously-untracked contact UNARCHIVES the existing
 *    person row, preserving the timeline + events accumulated before.
 *  - First-time tracking creates a new person, copies phone numbers,
 *    saves the contact photo, picks up birthday + anniversary, applies
 *    the user's default cadence, and triggers a call-log backfill.
 *  - Untracking ARCHIVES the person row — never deletes. The Contacts
 *    browser then renders the contact as untracked again, but if the
 *    user toggles back on, they pick up exactly where they left off.
 */
@Singleton
class ContactTracker @Inject constructor(
    private val contactsReader: ContactsReader,
    private val peopleRepository: PeopleRepository,
    private val personDao: PersonDao,
    private val photoStorage: PhotoStorage,
    private val settingsRepository: SettingsRepository,
    private val callLogAutoSync: CallLogAutoSync,
) {
    /**
     * Tracks the given system contact in Bondwidth. Returns the
     * resulting person id, or null when the contact couldn't be read.
     */
    suspend fun track(contactId: Long, lookupKey: String): Long? =
        withContext(Dispatchers.IO) {
            val existing = if (lookupKey.isNotBlank()) {
                personDao.findAnyByContactLookupKey(lookupKey)
            } else {
                null
            }
            if (existing != null) {
                if (existing.isArchived) {
                    personDao.setArchived(
                        existing.id,
                        archived = false,
                        now = System.currentTimeMillis(),
                    )
                    callLogAutoSync.syncAllPeople()
                }
                return@withContext existing.id
            }
            val details = contactsReader.readDetails(contactId)
                ?: return@withContext null
            val now = System.currentTimeMillis()
            val defaultCadence = settingsRepository.settings.first().defaultCadenceDays
            val uuid = UUID.randomUUID().toString()
            val photoRelativePath = runCatching {
                contactsReader.openContactPhoto(contactId)?.use { stream ->
                    photoStorage.savePhoto(uuid, stream)
                }
            }.getOrNull()
            val person = PersonEntity(
                uuid = uuid,
                displayName = details.displayName,
                contactLookupKey = details.lookupKey
                    .ifBlank { lookupKey.ifBlank { null } },
                photoRelativePath = photoRelativePath,
                cadenceTargetDays = defaultCadence,
                createdAt = now,
                updatedAt = now,
            )
            val phones = details.phoneNumbers.map { raw ->
                PhoneNumberEntity(
                    personId = 0,
                    rawNumber = raw,
                    normalizedNumber = raw.filter { it.isDigit() },
                )
            }
            val events = buildList {
                details.birthday?.let { add(it.toEventEntity(EventType.BIRTHDAY)) }
                details.anniversary?.let {
                    add(it.toEventEntity(EventType.WEDDING_ANNIVERSARY))
                }
            }
            val newId = peopleRepository.createPerson(person, phones, events)
            callLogAutoSync.syncAllPeople()
            newId
        }

    suspend fun untrack(lookupKey: String) = withContext(Dispatchers.IO) {
        val person = personDao.findAnyByContactLookupKey(lookupKey)
            ?: return@withContext
        if (!person.isArchived) {
            personDao.setArchived(
                person.id,
                archived = true,
                now = System.currentTimeMillis(),
            )
        }
    }

    /**
     * After a contact edit, refresh the linked Bondwidth person's editable
     * fields (displayName + phone numbers + birthday + anniversary) from
     * the system contact. No-op for untracked contacts and for archived
     * person rows. The cadence / notes / photo / relationship tag stay
     * intact — those are Bondwidth-only state.
     */
    suspend fun refreshTrackedFields(contactId: Long, lookupKey: String) =
        withContext(Dispatchers.IO) {
            if (lookupKey.isBlank()) return@withContext
            val person = personDao.findAnyByContactLookupKey(lookupKey)
                ?: return@withContext
            if (person.isArchived) return@withContext
            val details = contactsReader.readDetails(contactId)
                ?: return@withContext
            val now = System.currentTimeMillis()
            val updatedPerson = person.copy(
                displayName = details.displayName.ifBlank { person.displayName },
                updatedAt = now,
            )
            val phones = details.phoneNumbers.map { raw ->
                PhoneNumberEntity(
                    personId = person.id,
                    rawNumber = raw,
                    normalizedNumber = raw.filter { it.isDigit() },
                )
            }
            val events = buildList {
                details.birthday?.let { add(it.toEventEntity(EventType.BIRTHDAY)) }
                details.anniversary?.let {
                    add(it.toEventEntity(EventType.WEDDING_ANNIVERSARY))
                }
            }
            peopleRepository.updatePerson(updatedPerson, phones, events)
        }
}

private fun ContactDate.toEventEntity(type: EventType): EventEntity = EventEntity(
    personId = 0,
    type = type,
    month = month,
    day = day,
    year = year,
)
