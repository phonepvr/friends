package com.phonepvr.friends.data.db

import androidx.room.TypeConverter
import com.phonepvr.friends.domain.model.CallType
import com.phonepvr.friends.domain.model.ConfirmationStatus
import com.phonepvr.friends.domain.model.EntrySource
import com.phonepvr.friends.domain.model.EventType
import com.phonepvr.friends.domain.model.InteractionType

/** Stores enums as their name string so reordering enum constants stays safe. */
class Converters {
    @TypeConverter
    fun eventTypeToString(value: EventType): String = value.name

    @TypeConverter
    fun stringToEventType(value: String): EventType = EventType.valueOf(value)

    @TypeConverter
    fun interactionTypeToString(value: InteractionType): String = value.name

    @TypeConverter
    fun stringToInteractionType(value: String): InteractionType = InteractionType.valueOf(value)

    @TypeConverter
    fun entrySourceToString(value: EntrySource): String = value.name

    @TypeConverter
    fun stringToEntrySource(value: String): EntrySource = EntrySource.valueOf(value)

    @TypeConverter
    fun callTypeToString(value: CallType): String = value.name

    @TypeConverter
    fun stringToCallType(value: String): CallType = CallType.valueOf(value)

    @TypeConverter
    fun confirmationStatusToString(value: ConfirmationStatus): String = value.name

    @TypeConverter
    fun stringToConfirmationStatus(value: String): ConfirmationStatus =
        ConfirmationStatus.valueOf(value)
}
