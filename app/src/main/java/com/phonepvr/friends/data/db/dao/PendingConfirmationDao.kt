package com.phonepvr.friends.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.phonepvr.friends.data.db.entity.PendingConfirmationEntity
import com.phonepvr.friends.domain.model.ConfirmationStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingConfirmationDao {
    /** Ignores re-inserts of an already-queued call (unique callDedupKey). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(confirmation: PendingConfirmationEntity): Long

    @Update
    suspend fun update(confirmation: PendingConfirmationEntity)

    @Delete
    suspend fun delete(confirmation: PendingConfirmationEntity)

    @Query("SELECT * FROM pending_confirmations WHERE status = :status ORDER BY callTimestamp DESC")
    fun observeByStatus(status: ConfirmationStatus): Flow<List<PendingConfirmationEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM pending_confirmations WHERE callDedupKey = :key)")
    suspend fun existsByCallDedupKey(key: String): Boolean
}
