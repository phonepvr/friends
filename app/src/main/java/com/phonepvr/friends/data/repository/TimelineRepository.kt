package com.phonepvr.friends.data.repository

import com.phonepvr.friends.data.db.dao.TimelineDao
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity
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

    suspend fun addEntry(entry: TimelineEntryEntity): Long = timelineDao.insert(entry)

    suspend fun deleteEntry(entry: TimelineEntryEntity) {
        timelineDao.delete(entry)
    }
}
