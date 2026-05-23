package com.phonepvr.friends.data.repository

import com.phonepvr.friends.data.calllog.CallLogReader
import com.phonepvr.friends.data.db.dao.PendingConfirmationDao
import com.phonepvr.friends.data.db.dao.PhoneNumberDao
import com.phonepvr.friends.data.db.dao.TimelineDao
import com.phonepvr.friends.data.db.entity.PendingConfirmationEntity
import com.phonepvr.friends.data.db.entity.PhoneNumberEntity
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity
import com.phonepvr.friends.data.phone.AndroidPhoneNumberMatcher
import com.phonepvr.friends.domain.model.CallType
import com.phonepvr.friends.domain.model.ConfirmationStatus
import com.phonepvr.friends.domain.model.EntrySource
import com.phonepvr.friends.domain.model.InteractionType
import com.phonepvr.friends.domain.phone.PhoneNumberMatcher
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallLogRepository @Inject constructor(
    private val callLogReader: CallLogReader,
    private val phoneNumberDao: PhoneNumberDao,
    private val pendingConfirmationDao: PendingConfirmationDao,
    private val timelineDao: TimelineDao,
) {
    fun observePending(): Flow<List<PendingConfirmationEntity>> =
        pendingConfirmationDao.observeByStatus(ConfirmationStatus.PENDING)

    /**
     * Reads recent calls, matches them to tracked people, and queues any not
     * already seen. Re-scanning is safe: the unique call dedup key means
     * already-queued, confirmed, or dismissed calls are ignored.
     *
     * Matching is two-stage:
     *  1. **Prefilter** — index all stored phones by their last 7 digits
     *     (PhoneNumberMatcher.matchKey) and look up the call's last 7 digits.
     *     Fast O(1) but coarse (rare collisions on shared suffixes).
     *  2. **Strict confirm** — re-check each surviving candidate with
     *     PhoneNumberUtils.compare, which handles `+CC` vs `0`-prefix vs
     *     no-prefix variants correctly.
     *
     * Disambiguation policy after both stages:
     *  - Exactly one person survives → auto-attribute (`personId` set).
     *  - Two or more people survive → queue with `personId = null` and the
     *    ids joined into `candidatePersonIds`; the UI shows a picker.
     *  - No one survives → drop silently (no queue entry, no noise).
     *
     * Withheld / RESTRICTED / UNKNOWN / sub-7-digit calls fail the prefilter
     * (`matchKey` returns null) and are skipped at step 1.
     */
    suspend fun scanRecentCalls(windowDays: Int = 120) {
        val sinceMillis =
            System.currentTimeMillis() - windowDays.toLong() * 24L * 60L * 60L * 1000L
        val calls = callLogReader.recentCalls(sinceMillis)

        // Keep the full phone entity, not just personId — we need rawNumber
        // for the strict compare and the label for the UI picker.
        val phonesByMatchKey = HashMap<String, MutableList<PhoneNumberEntity>>()
        phoneNumberDao.getAll().forEach { phone ->
            val key = PhoneNumberMatcher.matchKey(phone.normalizedNumber) ?: return@forEach
            phonesByMatchKey.getOrPut(key) { mutableListOf() }.add(phone)
        }

        val now = System.currentTimeMillis()
        calls.forEach { call ->
            val key = PhoneNumberMatcher.matchKey(call.number) ?: return@forEach
            val prefiltered = phonesByMatchKey[key].orEmpty()
            val confirmedPersonIds = prefiltered
                .filter { AndroidPhoneNumberMatcher.strictMatches(call.number, it.rawNumber) }
                .map { it.personId }
                .distinct()
            if (confirmedPersonIds.isEmpty()) return@forEach
            pendingConfirmationDao.insert(
                PendingConfirmationEntity(
                    personId = confirmedPersonIds.singleOrNull(),
                    phoneNumber = call.number,
                    callTimestamp = call.timestampMillis,
                    callType = call.type,
                    callDedupKey = PhoneNumberMatcher.callDedupKey(
                        call.number,
                        call.timestampMillis,
                    ),
                    status = ConfirmationStatus.PENDING,
                    candidatePersonIds =
                        if (confirmedPersonIds.size > 1) {
                            confirmedPersonIds.joinToString(",")
                        } else {
                            null
                        },
                    durationSeconds = call.durationSeconds,
                    createdAt = now,
                ),
            )
        }
    }

    suspend fun confirm(confirmation: PendingConfirmationEntity, personId: Long) {
        if (!timelineDao.existsByCallDedupKey(confirmation.callDedupKey)) {
            timelineDao.insert(
                TimelineEntryEntity(
                    personId = personId,
                    occurredAt = confirmation.callTimestamp,
                    type = InteractionType.CALL,
                    note = null,
                    source = EntrySource.CALL_LOG,
                    countsAsContact = confirmation.callType == CallType.INCOMING ||
                        confirmation.callType == CallType.OUTGOING,
                    callDedupKey = confirmation.callDedupKey,
                    callDirection = confirmation.callType,
                    callDurationSeconds = confirmation.durationSeconds,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        pendingConfirmationDao.update(
            confirmation.copy(status = ConfirmationStatus.CONFIRMED, personId = personId),
        )
    }

    suspend fun dismiss(confirmation: PendingConfirmationEntity) {
        pendingConfirmationDao.update(
            confirmation.copy(status = ConfirmationStatus.DISMISSED),
        )
    }
}
