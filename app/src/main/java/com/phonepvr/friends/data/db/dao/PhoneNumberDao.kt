package com.phonepvr.friends.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.phonepvr.friends.data.db.entity.PhoneNumberEntity

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
}
