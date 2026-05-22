package com.phonepvr.friends.data.repository

import androidx.room.withTransaction
import com.phonepvr.friends.data.db.FriendsDatabase
import com.phonepvr.friends.data.db.dao.PersonDao
import com.phonepvr.friends.data.db.dao.PhoneNumberDao
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
) {
    fun observeActiveWithDetails(): Flow<List<PersonWithDetails>> =
        personDao.observeActiveWithDetails()

    fun observePersonWithDetails(id: Long): Flow<PersonWithDetails?> =
        personDao.observeWithDetails(id)

    suspend fun createPerson(
        person: PersonEntity,
        phoneNumbers: List<PhoneNumberEntity>,
    ): Long = database.withTransaction {
        val newId = personDao.insert(person)
        phoneNumberDao.insertAll(phoneNumbers.map { it.copy(id = 0, personId = newId) })
        newId
    }

    suspend fun updatePerson(
        person: PersonEntity,
        phoneNumbers: List<PhoneNumberEntity>,
    ) {
        database.withTransaction {
            personDao.update(person)
            phoneNumberDao.deleteForPerson(person.id)
            phoneNumberDao.insertAll(phoneNumbers.map { it.copy(id = 0, personId = person.id) })
        }
    }

    suspend fun deletePerson(person: PersonEntity) {
        personDao.delete(person)
    }
}
