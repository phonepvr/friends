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

    /**
     * Re-applies CallContactRule to call-log entries already stored: an entry made
     * under the earlier rule (any connected second counted), or restored from an
     * older backup, stops counting when the call lasted under [minSeconds]. Entries
     * whose length is unknown (logged before durations were recorded) and manual
     * entries are left alone. Returns how many entries changed.
     */
    @Query(
        "UPDATE timeline_entries SET countsAsContact = 0 " +
            "WHERE source = 'CALL_LOG' AND countsAsContact = 1 " +
            "AND callDurationSeconds IS NOT NULL AND callDurationSeconds < :minSeconds",
    )
    suspend fun stopCountingCallsShorterThan(minSeconds: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM timeline_entries WHERE callDedupKey = :key)")
    suspend fun existsByCallDedupKey(key: String): Boolean
}
