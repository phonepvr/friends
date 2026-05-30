package com.phonepvr.friends.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.phonepvr.friends.data.db.entity.PhoneNumberEntity
import kotlinx.coroutines.flow.Flow

/** A bonded (active) person's number, flattened for call-log matching. */
data class BondedNumberRow(
    val personId: Long,
    val displayName: String,
    val photoRelativePath: String?,
    val normalizedNumber: String,
)

@Dao
interface PhoneNumberDao {
    @Insert
    suspend fun insertAll(numbers: List<PhoneNumberEntity>)

    @Query("DELETE FROM phone_numbers WHERE personId = :personId")
    suspend fun deleteForPerson(personId: Long)

    @Query("SELECT * FROM phone_numbers WHERE personId = :personId")
    suspend fun getForPerson(personId: Long): List<PhoneNumberEntity>

    @Query("SELECT * FROM phone_numbers WHERE normalizedNumber = :normalizedNumber")
    suspend fun findByNormalizedNumber(normalizedNumber: String): List<PhoneNumberEntity>

    /**
     * Suffix match — finds the first stored number whose normalizedNumber
     * ends in [suffix]. Used by the in-call UI to resolve a caller's number
     * to a tracked person even when the caller-ID has a different country
     * code prefix than what was saved.
     */
    @Query(
        "SELECT * FROM phone_numbers " +
            "WHERE normalizedNumber LIKE '%' || :suffix " +
            "LIMIT 1",
    )
    suspend fun findOneByNumberSuffix(suffix: String): PhoneNumberEntity?

    @Query("SELECT * FROM phone_numbers")
    suspend fun getAll(): List<PhoneNumberEntity>

    /**
     * Every phone number belonging to a non-archived person, joined with
     * that person's name + photo. Powers the Width call-analytics bond
     * classification (a call counts as "bond" when its number suffix
     * matches one of these). Reactive — re-emits when people or their
     * numbers change.
     */
    @Query(
        "SELECT p.id AS personId, p.displayName AS displayName, " +
            "p.photoRelativePath AS photoRelativePath, " +
            "pn.normalizedNumber AS normalizedNumber " +
            "FROM phone_numbers pn " +
            "INNER JOIN people p ON pn.personId = p.id " +
            "WHERE p.isArchived = 0",
    )
    fun observeActiveBondedNumbers(): Flow<List<BondedNumberRow>>
}
