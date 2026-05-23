package com.phonepvr.friends.data.repository

import androidx.room.withTransaction
import com.phonepvr.friends.data.db.FriendsDatabase
import com.phonepvr.friends.data.db.dao.EventDao
import com.phonepvr.friends.data.db.dao.PersonDao
import com.phonepvr.friends.data.db.dao.PhoneNumberDao
import com.phonepvr.friends.data.db.entity.EventEntity
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.db.entity.PhoneNumberEntity
import com.phonepvr.friends.data.db.relation.PersonWithDetails
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeopleRepository @Inject constructor(
    private val database: FriendsDatabase,
    private val personDao: PersonDao,
    private val phoneNumberDao: PhoneNumberDao,
    private val eventDao: EventDao,
) {
    fun observeActiveWithDetails(): Flow<List<PersonWithDetails>> =
        personDao.observeActiveWithDetails()

    fun observePersonWithDetails(id: Long): Flow<PersonWithDetails?> =
        personDao.observeWithDetails(id)

    suspend fun createPerson(
        person: PersonEntity,
        phoneNumbers: List<PhoneNumberEntity>,
        events: List<EventEntity>,
    ): Long = database.withTransaction {
        val newId = personDao.insert(person)
        phoneNumberDao.insertAll(phoneNumbers.map { it.copy(id = 0, personId = newId) })
        events.forEach { eventDao.insert(it.copy(id = 0, personId = newId)) }
        newId
    }

    suspend fun updatePerson(
        person: PersonEntity,
        phoneNumbers: List<PhoneNumberEntity>,
        events: List<EventEntity>,
    ) {
        database.withTransaction {
            personDao.update(person)
            phoneNumberDao.deleteForPerson(person.id)
            phoneNumberDao.insertAll(phoneNumbers.map { it.copy(id = 0, personId = person.id) })
            eventDao.getForPerson(person.id).forEach { eventDao.delete(it) }
            events.forEach { eventDao.insert(it.copy(id = 0, personId = person.id)) }
        }
    }

    suspend fun deletePerson(person: PersonEntity) {
        personDao.delete(person)
    }

    /** Inline cadence update used by the Person Detail tap-the-card flow. */
    suspend fun setCadenceTargetDays(personId: Long, days: Int?) {
        personDao.setCadenceTargetDays(personId, days, System.currentTimeMillis())
    }
}
