package com.phonepvr.friends.data.db.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.phonepvr.friends.data.db.entity.EventEntity
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.db.entity.PhoneNumberEntity

/** A person together with their phone numbers and important dates. */
data class PersonWithDetails(
    @Embedded val person: PersonEntity,
    @Relation(parentColumn = "id", entityColumn = "personId")
    val phoneNumbers: List<PhoneNumberEntity>,
    @Relation(parentColumn = "id", entityColumn = "personId")
    val events: List<EventEntity>,
)
