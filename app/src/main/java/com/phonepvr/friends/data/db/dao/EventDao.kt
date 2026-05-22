package com.phonepvr.friends.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.phonepvr.friends.data.db.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("SELECT * FROM events WHERE personId = :personId")
    suspend fun getForPerson(personId: Long): List<EventEntity>

    @Query("SELECT * FROM events")
    fun observeAll(): Flow<List<EventEntity>>
}
