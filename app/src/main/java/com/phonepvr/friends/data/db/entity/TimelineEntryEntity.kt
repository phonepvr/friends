package com.phonepvr.friends.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.phonepvr.friends.domain.model.EntrySource
import com.phonepvr.friends.domain.model.InteractionType

@Entity(
    tableName = "timeline_entries",
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
        Index("occurredAt"),
        Index(value = ["callDedupKey"], unique = true),
    ],
)
data class TimelineEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    /** When the interaction happened, as epoch milliseconds. */
    val occurredAt: Long,
    val type: InteractionType,
    val note: String? = null,
    val source: EntrySource,
    /** False for logged missed/rejected calls, so cadence math ignores them. */
    val countsAsContact: Boolean = true,
    /** Set for call-log entries to prevent importing the same call twice. */
    val callDedupKey: String? = null,
    val createdAt: Long,
)
