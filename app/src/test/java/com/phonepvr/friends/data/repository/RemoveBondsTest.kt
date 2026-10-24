package com.phonepvr.friends.data.repository

import androidx.room.Room
import com.phonepvr.friends.data.db.FriendsDatabase
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity
import com.phonepvr.friends.domain.model.EntrySource
import com.phonepvr.friends.domain.model.InteractionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Removing several bonds at once takes their history with them and nothing else. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoveBondsTest {

    private lateinit var db: FriendsDatabase
    private lateinit var repository: PeopleRepository
    private val ids = mutableMapOf<String, Long>()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FriendsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PeopleRepository(db, db.personDao(), db.phoneNumberDao(), db.eventDao())
        for (name in listOf("Asha", "Bilal", "Chen")) {
            val id = db.personDao().insert(
                PersonEntity(uuid = name, displayName = name, contactLookupKey = "lk-$name", createdAt = 1, updatedAt = 1),
            )
            ids[name] = id
            db.timelineDao().insert(
                TimelineEntryEntity(
                    personId = id,
                    occurredAt = 10,
                    type = InteractionType.CALL,
                    source = EntrySource.MANUAL,
                    createdAt = 10,
                ),
            )
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun removesTheSelectedBondsAndTheirHistoryOnly() = runBlocking {
        val removed = repository.deletePeople(listOf(ids.getValue("Asha"), ids.getValue("Chen")))
        assertEquals(2, removed)
        assertEquals(listOf("Bilal"), db.personDao().getAll().map { it.displayName })
        assertEquals(listOf(ids.getValue("Bilal")), db.timelineDao().getAll().map { it.personId })
    }

    @Test
    fun nothingSelected_nothingRemoved() = runBlocking {
        assertEquals(0, repository.deletePeople(emptyList()))
        assertEquals(3, db.personDao().getAll().size)
    }
}
