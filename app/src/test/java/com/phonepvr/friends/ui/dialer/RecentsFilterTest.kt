package com.phonepvr.friends.ui.dialer

import com.phonepvr.friends.domain.model.CallType
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentsFilterTest {

    private fun shown(filter: RecentsFilter) = CallType.entries.filter { filter.matches(it) }.toSet()

    @Test
    fun allShowsEverything() {
        assertEquals(CallType.entries.toSet(), shown(RecentsFilter.ALL))
    }

    @Test
    fun missedShowsOnlyUnansweredCalls() {
        assertEquals(setOf(CallType.MISSED), shown(RecentsFilter.MISSED))
    }

    @Test
    fun incomingIncludesDeclinedCalls() {
        assertEquals(setOf(CallType.INCOMING, CallType.REJECTED), shown(RecentsFilter.INCOMING))
    }

    @Test
    fun outgoingShowsOnlyCallsYouMade() {
        assertEquals(setOf(CallType.OUTGOING), shown(RecentsFilter.OUTGOING))
    }

    @Test
    fun everyCallTypeAppearsUnderExactlyOneSpecificFilter() {
        val specific = RecentsFilter.entries - RecentsFilter.ALL
        for (type in CallType.entries) {
            assertEquals(type.name, 1, specific.count { it.matches(type) })
        }
    }
}
