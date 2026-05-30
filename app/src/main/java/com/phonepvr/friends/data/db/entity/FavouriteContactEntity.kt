package com.phonepvr.friends.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A contact the user has pinned to the favourites strip in the Calls tab.
 * Keyed by the system-contact lookupKey so the link survives a contact
 * rename / merge in the address book. The displayName + primaryNumber +
 * photoRelativePath are snapshot at favourite-time so the strip still
 * renders correctly if READ_CONTACTS hasn't been granted yet.
 */
@Entity(tableName = "favourite_contacts")
data class FavouriteContactEntity(
    @PrimaryKey val lookupKey: String,
    val displayName: String,
    val primaryNumber: String,
    val photoRelativePath: String? = null,
    val position: Int = 0,
    val addedAt: Long,
)
