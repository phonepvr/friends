package com.phonepvr.friends.data.repository

import com.phonepvr.friends.data.db.entity.TimelineEntryEntity
import com.phonepvr.friends.domain.model.CallType
import com.phonepvr.friends.domain.model.EntrySource
import com.phonepvr.friends.domain.model.InteractionType
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls recent calls from the device call log into every active person's
 * timeline. Idempotent — the `callDedupKey` UNIQUE index on
 * `timeline_entries` makes a repeat run insert nothing, and
 * [CallLogRepository.scanForPerson] already filters out anything already
 * present.
 *
 * Called from:
 *  - `ImportContactsViewModel.importSelected` after the contact write so a
 *    freshly-imported list immediately carries its call history.
 *  - `FriendsApplication.onCreate` so each app launch picks up new calls.
 *
 * `scanForPerson` already swallows `SecurityException` when READ_CALL_LOG
 * is missing, so calling `syncAllPeople()` without the permission is a safe
 * no-op rather than a crash.
 */
@Singleton
class CallLogAutoSync @Inject constructor(
    private val peopleRepository: PeopleRepository,
    private val callLogRepository: CallLogRepository,
    private val timelineRepository: TimelineRepository,
) {
    /** Returns the number of timeline rows added across all people. */
    suspend fun syncAllPeople(): Int {
        val people = peopleRepository.observeActiveWithDetails().first()
        if (people.isEmpty()) return 0
        var added = 0
        people.forEach { detail ->
            val candidates = callLogRepository.scanForPerson(detail.person.id)
            candidates.forEach { candidate ->
                val call = candidate.deviceCall
                timelineRepository.addEntry(
                    TimelineEntryEntity(
                        personId = detail.person.id,
                        occurredAt = call.timestampMillis,
                        type = InteractionType.CALL,
                        note = null,
                        source = EntrySource.CALL_LOG,
                        // Same rule the manual scan flow used: only real
                        // conversations move the cadence clock.
                        countsAsContact = (call.type == CallType.INCOMING ||
                            call.type == CallType.OUTGOING) && call.durationSeconds > 0L,
                        callDedupKey = candidate.callDedupKey,
                        callDirection = call.type,
                        callDurationSeconds = call.durationSeconds,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                added++
            }
        }
        return added
    }
}
