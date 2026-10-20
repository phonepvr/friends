package com.phonepvr.friends.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallContactRuleTest {

    private fun counts(type: CallType?, seconds: Long?) = CallContactRule.countsAsContact(type, seconds)

    @Test
    fun conversationsOfThreeSecondsOrMore_count() {
        assertTrue(counts(CallType.INCOMING, 3))
        assertTrue(counts(CallType.OUTGOING, 3))
        assertTrue(counts(CallType.OUTGOING, 600))
    }

    @Test
    fun callsUnderThreeSeconds_doNotCount() {
        assertFalse(counts(CallType.INCOMING, 2))
        assertFalse(counts(CallType.OUTGOING, 1))
        assertFalse(counts(CallType.OUTGOING, 0))
    }

    @Test
    fun missedAndRejectedCalls_neverCount() {
        assertFalse(counts(CallType.MISSED, 0))
        assertFalse(counts(CallType.MISSED, 30))
        assertFalse(counts(CallType.REJECTED, 0))
    }

    @Test
    fun unknownTypeOrLength_doesNotCount() {
        assertFalse(counts(null, 60))
        assertFalse(counts(CallType.INCOMING, null))
    }
}
