package com.phonepvr.friends.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.phonepvr.friends.data.db.entity.FavouriteContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavouriteContactDao {
    @Query("SELECT * FROM favourite_contacts ORDER BY position ASC, addedAt ASC")
    fun observeAll(): Flow<List<FavouriteContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fav: FavouriteContactEntity)

    @Query("DELETE FROM favourite_contacts WHERE lookupKey = :lookupKey")
    suspend fun delete(lookupKey: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favourite_contacts WHERE lookupKey = :lookupKey)")
    fun observeIsFavourite(lookupKey: String): Flow<Boolean>

    @Query("SELECT MAX(position) FROM favourite_contacts")
    suspend fun maxPosition(): Int?
}
