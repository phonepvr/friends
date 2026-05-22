package com.phonepvr.friends.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.phonepvr.friends.domain.model.EventType

/**
 * A recurring important date for a person. The schema supports any number of
 * events of any type; the v1 UI writes only birthdays and wedding anniversaries.
 */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("personId")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val type: EventType,
    /** Optional label, used for custom event types. */
    val label: String? = null,
    val month: Int,
    val day: Int,
    /** Null when the year is unknown (common for birthdays). */
    val year: Int? = null,
)
