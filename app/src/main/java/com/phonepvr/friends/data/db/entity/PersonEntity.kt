package com.phonepvr.friends.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "people",
    indices = [Index(value = ["uuid"], unique = true)],
)
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable identifier that survives backup and restore onto a new device. */
    val uuid: String,
    val displayName: String,
    val relationshipTag: String? = null,
    /** How often the user wants to stay in touch; null means not tracked. */
    val cadenceTargetDays: Int? = null,
    /** Path to the contact photo, relative to the app's files directory. */
    val photoRelativePath: String? = null,
    /** Device-contact lookup key, used to refresh details from contacts. */
    val contactLookupKey: String? = null,
    val notes: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
