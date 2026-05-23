package com.phonepvr.friends.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.phonepvr.friends.domain.model.CallType
import com.phonepvr.friends.domain.model.ConfirmationStatus

/**
 * A call detected from the device call log that is waiting for the user to
 * confirm or dismiss it. [personId] is null when the number matched more than
 * one tracked person (the candidates are listed in [candidatePersonIds]).
 */
@Entity(
    tableName = "pending_confirmations",
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
        Index(value = ["callDedupKey"], unique = true),
    ],
)
data class PendingConfirmationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long? = null,
    val phoneNumber: String,
    val callTimestamp: Long,
    val callType: CallType,
    val callDedupKey: String,
    val status: ConfirmationStatus = ConfirmationStatus.PENDING,
    /** Comma-separated person ids when the number matched several people. */
    val candidatePersonIds: String? = null,
    /** Call length in seconds, captured at scan time; null for pre-v2 rows. */
    val durationSeconds: Long? = null,
    val createdAt: Long,
)
