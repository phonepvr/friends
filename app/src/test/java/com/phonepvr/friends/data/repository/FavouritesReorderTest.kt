package com.phonepvr.friends.data.repository

import androidx.room.Room
import com.phonepvr.friends.data.db.FriendsDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The strip order survives a round trip through the real database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FavouritesReorderTest {

    private lateinit var db: FriendsDatabase
    private lateinit var repository: FavouritesRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FriendsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FavouritesRepository(db.favouriteContactDao())
        for (name in listOf("Asha", "Bilal", "Chen")) {
            repository.toggle(lookupKey = name, displayName = name, primaryNumber = "1", photoRelativePath = null)
        }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun strip() = repository.observeAll().first().map { it.lookupKey }

    @Test
    fun newFavouritesGoToTheEnd() = runBlocking {
        assertEquals(listOf("Asha", "Bilal", "Chen"), strip())
    }

    @Test
    fun reorderIsSaved() = runBlocking {
        repository.reorder(listOf("Chen", "Asha", "Bilal"))
        assertEquals(listOf("Chen", "Asha", "Bilal"), strip())
    }

    @Test
    fun removingKeepsTheRestInOrder() = runBlocking {
        repository.reorder(listOf("Chen", "Asha", "Bilal"))
        repository.remove("Asha")
        assertEquals(listOf("Chen", "Bilal"), strip())
    }
}
