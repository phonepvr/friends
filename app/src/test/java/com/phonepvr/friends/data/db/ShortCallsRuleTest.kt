package com.phonepvr.friends.data.db

import androidx.room.Room
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity
import com.phonepvr.friends.domain.model.CallContactRule
import com.phonepvr.friends.domain.model.CallType
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

/** Stored call-log entries are brought in line with the 3-second rule. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortCallsRuleTest {

    private lateinit var db: FriendsDatabase
    private var personId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FriendsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        personId = db.personDao().insert(
            PersonEntity(uuid = "u1", displayName = "Asha", createdAt = 1L, updatedAt = 1L),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun add(
        key: String?,
        source: EntrySource,
        type: CallType?,
        seconds: Long?,
        counts: Boolean,
    ): Long = db.timelineDao().insert(
        TimelineEntryEntity(
            personId = personId,
            occurredAt = 1_000L,
            type = InteractionType.CALL,
            source = source,
            countsAsContact = counts,
            callDedupKey = key,
            callDirection = type,
            callDurationSeconds = seconds,
            createdAt = 1L,
        ),
    )

    @Test
    fun onlyShortCallLogEntriesStopCounting() = runBlocking {
        val oneSecond = add("a", EntrySource.CALL_LOG, CallType.OUTGOING, 1, counts = true)
        val twoSeconds = add("b", EntrySource.CALL_LOG, CallType.INCOMING, 2, counts = true)
        val threeSeconds = add("c", EntrySource.CALL_LOG, CallType.INCOMING, 3, counts = true)
        val longCall = add("d", EntrySource.CALL_LOG, CallType.OUTGOING, 300, counts = true)
        val unknownLength = add("e", EntrySource.CALL_LOG, null, null, counts = true)
        val manual = add(null, EntrySource.MANUAL, null, null, counts = true)
        val missed = add("f", EntrySource.CALL_LOG, CallType.MISSED, 0, counts = false)

        val changed = db.timelineDao().stopCountingCallsShorterThan(CallContactRule.MIN_CONNECTED_SECONDS)

        assertEquals(2, changed)
        fun counts(id: Long) = runBlocking { db.timelineDao().getById(id)!!.countsAsContact }
        assertEquals(false, counts(oneSecond))
        assertEquals(false, counts(twoSeconds))
        assertEquals(true, counts(threeSeconds))
        assertEquals(true, counts(longCall))
        assertEquals(true, counts(unknownLength))
        assertEquals(true, counts(manual))
        assertEquals(false, counts(missed))
        // Running again changes nothing.
        assertEquals(0, db.timelineDao().stopCountingCallsShorterThan(CallContactRule.MIN_CONNECTED_SECONDS))
    }

    @Test
    fun lastContactIgnoresShortCallsAfterTheRuleIsApplied() = runBlocking {
        add("old", EntrySource.CALL_LOG, CallType.OUTGOING, 120, counts = true)
        db.timelineDao().insert(
            TimelineEntryEntity(
                personId = personId,
                occurredAt = 9_000L,
                type = InteractionType.CALL,
                source = EntrySource.CALL_LOG,
                countsAsContact = true,
                callDedupKey = "new-short",
                callDirection = CallType.OUTGOING,
                callDurationSeconds = 2,
                createdAt = 1L,
            ),
        )
        assertEquals(9_000L, db.timelineDao().latestContactAt(personId))
        db.timelineDao().stopCountingCallsShorterThan(CallContactRule.MIN_CONNECTED_SECONDS)
        assertEquals(1_000L, db.timelineDao().latestContactAt(personId))
    }
}
