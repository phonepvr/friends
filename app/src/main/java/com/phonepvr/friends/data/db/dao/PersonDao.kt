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

    /**
     * Active person linked to this contact, or null when the contact isn't
     * tracked. Matches on the lookupKey OR the contact id: a rename can change
     * a contact's lookupKey while its id stays put, so either one identifies
     * the bond. Drives the Contact detail screen's "Track in Bondwidth" toggle.
     * (A blank [lookupKey] never matches a person with no key.)
     */
    @Query(
        "SELECT * FROM people WHERE isArchived = 0 AND " +
            "((contactLookupKey = :lookupKey AND :lookupKey != '') OR contactId = :contactId) " +
            "ORDER BY (contactLookupKey = :lookupKey) DESC, id ASC LIMIT 1",
    )
    fun observeActiveByContact(lookupKey: String, contactId: Long): Flow<PersonEntity?>

    /**
     * Any person row linked to this contact (by lookupKey or contact id),
     * archived or not. The tracker uses this to revive an archived row when the
     * user re-tracks a contact, preserving the existing timeline + events.
     */
    @Query(
        "SELECT * FROM people WHERE " +
            "((contactLookupKey = :lookupKey AND :lookupKey != '') OR contactId = :contactId) " +
            "ORDER BY (contactLookupKey = :lookupKey) DESC, isArchived ASC, id ASC LIMIT 1",
    )
    suspend fun findAnyByContact(lookupKey: String, contactId: Long): PersonEntity?

    /** Every person with phones + events, for the bond↔contact reconciler. */
    @Transaction
    @Query("SELECT * FROM people")
    suspend fun getAllWithDetails(): List<PersonWithDetails>

    /**
     * Points a bond at its (re-)resolved contact: current lookupKey, contact id
     * and name. Targeted so it never touches cadence, notes, phones or events.
     */
    @Query(
        "UPDATE people SET contactLookupKey = :lookupKey, contactId = :contactId, " +
            "displayName = :displayName, updatedAt = :now WHERE id = :id",
    )
    suspend fun updateContactLink(
        id: Long,
        lookupKey: String?,
        contactId: Long?,
        displayName: String,
        now: Long,
    )

    /** Detaches a bond from any contact; it stays as a standalone bond. */
    @Query(
        "UPDATE people SET contactLookupKey = NULL, contactId = NULL, updatedAt = :now " +
            "WHERE id = :id",
    )
    suspend fun clearContactLink(id: Long, now: Long)

    @Query(
        "UPDATE people SET isArchived = :archived, updatedAt = :now WHERE id = :id",
    )
    suspend fun setArchived(id: Long, archived: Boolean, now: Long)

    @Transaction
    @Query("SELECT * FROM people WHERE id = :id")
    fun observeWithDetails(id: Long): Flow<PersonWithDetails?>

    @Transaction
    @Query("SELECT * FROM people WHERE isArchived = 0 ORDER BY displayName COLLATE NOCASE")
    fun observeActiveWithDetails(): Flow<List<PersonWithDetails>>
}
