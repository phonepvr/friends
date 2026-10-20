package com.phonepvr.friends.data.repository

import com.phonepvr.friends.data.db.dao.TimelineDao
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity
import com.phonepvr.friends.domain.model.CallContactRule
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimelineRepository @Inject constructor(
    private val timelineDao: TimelineDao,
) {
    fun observeForPerson(personId: Long): Flow<List<TimelineEntryEntity>> =
        timelineDao.observeForPerson(personId)

    fun observeAll(): Flow<List<TimelineEntryEntity>> = timelineDao.observeAll()

    /** Brings stored call-log entries in line with [CallContactRule]; see the DAO. */
    suspend fun reapplyCallContactRule(): Int =
        timelineDao.stopCountingCallsShorterThan(CallContactRule.MIN_CONNECTED_SECONDS)

    suspend fun addEntry(entry: TimelineEntryEntity): Long = timelineDao.insert(entry)

    suspend fun updateEntry(entry: TimelineEntryEntity) {
        timelineDao.update(entry)
    }

    suspend fun getById(id: Long): TimelineEntryEntity? = timelineDao.getById(id)

    suspend fun deleteEntry(entry: TimelineEntryEntity) {
        timelineDao.delete(entry)
    }

    suspend fun deleteByIds(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        timelineDao.deleteByIds(ids.toList())
    }
}
