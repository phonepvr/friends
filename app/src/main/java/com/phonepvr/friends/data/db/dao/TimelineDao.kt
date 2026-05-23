package com.phonepvr.friends.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineDao {
    @Insert
    suspend fun insert(entry: TimelineEntryEntity): Long

    /** Inserts rows keeping their ids; used to restore a backup. */
    @Insert
    suspend fun insertAll(entries: List<TimelineEntryEntity>)

    @Update
    suspend fun update(entry: TimelineEntryEntity)

    @Delete
    suspend fun delete(entry: TimelineEntryEntity)

    @Query("DELETE FROM timeline_entries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM timeline_entries ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<TimelineEntryEntity>>

    @Query("SELECT * FROM timeline_entries")
    suspend fun getAll(): List<TimelineEntryEntity>

    @Query("SELECT * FROM timeline_entries WHERE personId = :personId ORDER BY occurredAt DESC")
    fun observeForPerson(personId: Long): Flow<List<TimelineEntryEntity>>

    @Query("SELECT * FROM timeline_entries WHERE id = :id")
    suspend fun getById(id: Long): TimelineEntryEntity?

    @Query(
        "SELECT MAX(occurredAt) FROM timeline_entries " +
            "WHERE personId = :personId AND countsAsContact = 1",
    )
    suspend fun latestContactAt(personId: Long): Long?

    @Query("SELECT EXISTS(SELECT 1 FROM timeline_entries WHERE callDedupKey = :key)")
    suspend fun existsByCallDedupKey(key: String): Boolean
}
