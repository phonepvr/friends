package com.phonepvr.friends.ui.dialer

import com.phonepvr.friends.data.calllog.DeviceCall
import com.phonepvr.friends.domain.model.CallType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Issue #68: two call-log rows with the same time and number closed the app. */
class RecentsListTest {

    private fun call(id: Long, type: CallType = CallType.INCOMING, duration: Long = 30) =
        DeviceCall(number = "+49911347717", type = type, timestampMillis = 1_793_170_878_786, durationSeconds = duration, id = id)

    private fun entry(c: DeviceCall) = RecentEntry(
        number = c.number, displayName = null, contactId = null, isTracked = false,
        photoRelativePath = null, photoUri = null, type = c.type,
        timestampMillis = c.timestampMillis, durationSeconds = c.durationSeconds, id = c.id,
    )

    @Test
    fun theSameCallLoggedTwiceShowsOnce() {
        assertEquals(listOf(1L), RecentsList.withoutDuplicates(listOf(call(1), call(2))).map { it.id })
    }

    @Test
    fun differentCallsAtTheSameMomentAreKeptWithDistinctKeys() {
        val calls = RecentsList.withoutDuplicates(listOf(call(1), call(2, CallType.REJECTED, 0)))
        assertEquals(2, calls.size)
        val keys = calls.map(::entry).mapIndexed { i, e -> RecentsList.key(e, i) }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun keysStayUniqueEvenWithoutCallLogIds() {
        val rows = listOf(call(0), call(0)).map(::entry)
        val keys = rows.mapIndexed { i, e -> RecentsList.key(e, i) }
        assertEquals(2, keys.toSet().size)
    }
}
