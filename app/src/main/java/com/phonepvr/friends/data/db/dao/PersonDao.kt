package com.phonepvr.friends.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.db.relation.PersonWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Insert
    suspend fun insert(person: PersonEntity): Long

    /** Inserts rows keeping their ids; used to restore a backup. */
    @Insert
    suspend fun insertAll(people: List<PersonEntity>)

    @Update
    suspend fun update(person: PersonEntity)

    @Delete
    suspend fun delete(person: PersonEntity)

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun getById(id: Long): PersonEntity?

    @Query("SELECT * FROM people")
    suspend fun getAll(): List<PersonEntity>

    @Query("UPDATE people SET cadenceTargetDays = :days, updatedAt = :now WHERE id = :id")
    suspend fun setCadenceTargetDays(id: Long, days: Int?, now: Long)

    @Query(
        "UPDATE people SET cadenceTargetDays = :days, updatedAt = :now " +
            "WHERE cadenceTargetDays IS NULL",
    )
    suspend fun backfillMissingCadence(days: Int, now: Long): Int

    @Query("SELECT * FROM people WHERE id = :id")
    fun observeById(id: Long): Flow<PersonEntity?>

    @Query("SELECT * FROM people WHERE isArchived = 0 ORDER BY displayName COLLATE NOCASE")
    fun observeActive(): Flow<List<PersonEntity>>

    @Transaction
    @Query("SELECT * FROM people WHERE id = :id")
    fun observeWithDetails(id: Long): Flow<PersonWithDetails?>

    @Transaction
    @Query("SELECT * FROM people WHERE isArchived = 0 ORDER BY displayName COLLATE NOCASE")
    fun observeActiveWithDetails(): Flow<List<PersonWithDetails>>
}
