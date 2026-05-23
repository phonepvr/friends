package com.phonepvr.friends.data.backup

import com.phonepvr.friends.data.db.entity.EventEntity
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.db.entity.PhoneNumberEntity
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity
import com.phonepvr.friends.domain.model.CallType
import com.phonepvr.friends.domain.model.EntrySource
import com.phonepvr.friends.domain.model.EventType
import com.phonepvr.friends.domain.model.InteractionType
import kotlinx.serialization.Serializable

/**
 * Bumped only when the backup JSON structure changes in a way older builds
 * cannot read. Import refuses a backup whose version is newer than this.
 *
 * v2 added the optional [BackupFile.settings] map; v1 backups still import,
 * with restored devices keeping their current preference values.
 *
 * v3 added optional call direction + duration on [BackupTimelineEntry] and
 * [BackupPendingConfirmation]. v1 and v2 backups still import, with the new
 * fields restored as null.
 */
const val BACKUP_SCHEMA_VERSION = 3

/**
 * The complete, self-contained contents of a backup. Enum fields are stored
 * as their name string, matching how Room persists them, so the format does
 * not depend on enum ordering.
 */
@Serializable
data class BackupFile(
    val schemaVersion: Int,
    val exportedAt: Long,
    val people: List<BackupPerson>,
    val phoneNumbers: List<BackupPhoneNumber>,
    val events: List<BackupEvent>,
    val timelineEntries: List<BackupTimelineEntry>,
    /**
     * Retained on the shape (with an empty default) so v1 / v2 backups still
     * deserialise. The pending-confirmations table itself is gone — new
     * exports always write an empty list and imports ignore it.
     */
    val pendingConfirmations: List<BackupPendingConfirmation> = emptyList(),
    /** Added in v2; null when restoring a v1 backup. */
    val settings: Map<String, String>? = null,
)

@Serializable
data class BackupPerson(
    val id: Long,
    val uuid: String,
    val displayName: String,
    val relationshipTag: String? = null,
    val cadenceTargetDays: Int? = null,
    val photoRelativePath: String? = null,
    val contactLookupKey: String? = null,
    val notes: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupPhoneNumber(
    val id: Long,
    val personId: Long,
    val rawNumber: String,
    val normalizedNumber: String,
    val label: String? = null,
)

@Serializable
data class BackupEvent(
    val id: Long,
    val personId: Long,
    val type: String,
    val label: String? = null,
    val month: Int,
    val day: Int,
    val year: Int? = null,
)

@Serializable
data class BackupTimelineEntry(
    val id: Long,
    val personId: Long,
    val occurredAt: Long,
    val type: String,
    val note: String? = null,
    val source: String,
    val countsAsContact: Boolean = true,
    val callDedupKey: String? = null,
    /** Added in v3. CallType name string; null for non-calls and pre-v3 entries. */
    val callDirection: String? = null,
    /** Added in v3. Seconds; null for non-calls and pre-v3 entries. */
    val callDurationSeconds: Long? = null,
    val createdAt: Long,
)

@Serializable
data class BackupPendingConfirmation(
    val id: Long,
    val personId: Long? = null,
    val phoneNumber: String,
    val callTimestamp: Long,
    val callType: String,
    val callDedupKey: String,
    val status: String,
    val candidatePersonIds: String? = null,
    /** Added in v3. Seconds; null for pre-v3 backups. */
    val durationSeconds: Long? = null,
    val createdAt: Long,
)

fun PersonEntity.toBackup(): BackupPerson = BackupPerson(
    id = id,
    uuid = uuid,
    displayName = displayName,
    relationshipTag = relationshipTag,
    cadenceTargetDays = cadenceTargetDays,
    photoRelativePath = photoRelativePath,
    contactLookupKey = contactLookupKey,
    notes = notes,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun BackupPerson.toEntity(): PersonEntity = PersonEntity(
    id = id,
    uuid = uuid,
    displayName = displayName,
    relationshipTag = relationshipTag,
    cadenceTargetDays = cadenceTargetDays,
    photoRelativePath = photoRelativePath,
    contactLookupKey = contactLookupKey,
    notes = notes,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PhoneNumberEntity.toBackup(): BackupPhoneNumber = BackupPhoneNumber(
    id = id,
    personId = personId,
    rawNumber = rawNumber,
    normalizedNumber = normalizedNumber,
    label = label,
)

fun BackupPhoneNumber.toEntity(): PhoneNumberEntity = PhoneNumberEntity(
    id = id,
    personId = personId,
    rawNumber = rawNumber,
    normalizedNumber = normalizedNumber,
    label = label,
)

fun EventEntity.toBackup(): BackupEvent = BackupEvent(
    id = id,
    personId = personId,
    type = type.name,
    label = label,
    month = month,
    day = day,
    year = year,
)

fun BackupEvent.toEntity(): EventEntity = EventEntity(
    id = id,
    personId = personId,
    type = EventType.valueOf(type),
    label = label,
    month = month,
    day = day,
    year = year,
)

fun TimelineEntryEntity.toBackup(): BackupTimelineEntry = BackupTimelineEntry(
    id = id,
    personId = personId,
    occurredAt = occurredAt,
    type = type.name,
    note = note,
    source = source.name,
    countsAsContact = countsAsContact,
    callDedupKey = callDedupKey,
    callDirection = callDirection?.name,
    callDurationSeconds = callDurationSeconds,
    createdAt = createdAt,
)

fun BackupTimelineEntry.toEntity(): TimelineEntryEntity = TimelineEntryEntity(
    id = id,
    personId = personId,
    occurredAt = occurredAt,
    type = InteractionType.valueOf(type),
    note = note,
    source = EntrySource.valueOf(source),
    countsAsContact = countsAsContact,
    callDedupKey = callDedupKey,
    callDirection = callDirection?.let { CallType.valueOf(it) },
    callDurationSeconds = callDurationSeconds,
    createdAt = createdAt,
)

// PendingConfirmationEntity ⇄ BackupPendingConfirmation mappers were removed
// when the global Calls queue was retired. The BackupPendingConfirmation data
// class above stays so existing backups still parse, but its values are no
// longer materialised into the database on restore.
