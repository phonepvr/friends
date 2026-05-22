package com.phonepvr.friends.data.repository

import com.phonepvr.friends.data.calllog.CallLogReader
import com.phonepvr.friends.data.db.dao.PendingConfirmationDao
import com.phonepvr.friends.data.db.dao.PhoneNumberDao
import com.phonepvr.friends.data.db.dao.TimelineDao
import com.phonepvr.friends.data.db.entity.PendingConfirmationEntity
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity
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
     */
    suspend fun scanRecentCalls(windowDays: Int = 120) {
        val sinceMillis =
            System.currentTimeMillis() - windowDays.toLong() * 24L * 60L * 60L * 1000L
        val calls = callLogReader.recentCalls(sinceMillis)

        val personIdsByMatchKey = HashMap<String, MutableSet<Long>>()
        phoneNumberDao.getAll().forEach { phone ->
            val key = PhoneNumberMatcher.matchKey(phone.normalizedNumber) ?: return@forEach
            personIdsByMatchKey.getOrPut(key) { mutableSetOf() }.add(phone.personId)
        }

        val now = System.currentTimeMillis()
        calls.forEach { call ->
            val key = PhoneNumberMatcher.matchKey(call.number) ?: return@forEach
            val personIds = personIdsByMatchKey[key]?.toList().orEmpty()
            if (personIds.isEmpty()) return@forEach
            pendingConfirmationDao.insert(
                PendingConfirmationEntity(
                    personId = personIds.singleOrNull(),
                    phoneNumber = call.number,
                    callTimestamp = call.timestampMillis,
                    callType = call.type,
                    callDedupKey = PhoneNumberMatcher.callDedupKey(
                        call.number,
                        call.timestampMillis,
                    ),
                    status = ConfirmationStatus.PENDING,
                    candidatePersonIds =
                        if (personIds.size > 1) personIds.joinToString(",") else null,
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
