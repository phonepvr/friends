package com.phonepvr.friends.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "phone_numbers",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("personId"),
        Index("normalizedNumber"),
    ],
)
data class PhoneNumberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val rawNumber: String,
    /** Digits-only form used to match against call-log numbers. */
    val normalizedNumber: String,
    val label: String? = null,
)
