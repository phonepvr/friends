package com.phonepvr.friends.domain

import com.phonepvr.friends.domain.model.CallType
import com.phonepvr.friends.domain.review.BondRef
import com.phonepvr.friends.domain.review.CallAnalytics
import com.phonepvr.friends.domain.review.CallBucket
import com.phonepvr.friends.domain.review.CallRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallAnalyticsTest {

    private val bondBySuffix = mapOf(
        // Aanya, bonded, two device numbers that share the last-9 suffix.
        "771234567" to BondRef(personId = 1, displayName = "Aanya", photoRelativePath = null),
    )
    private val contactBySuffix = mapOf(
        "770000000" to "Plumber",
    )

    @Test
    fun emptyCalls_isEmptyResult() {
        val result = CallAnalytics.compute(
            windowDays = 30,
            calls = emptyList(),
            bondBySuffix = bondBySuffix,
            contactNameBySuffix = contactBySuffix,
            bondedTotalCount = 1,
        )
        assertTrue(result.isEmpty)
        assertEquals(0, result.bondedReachedCount)
    }

    @Test
    fun classifiesBondContactAndUnknown() {
        val calls = listOf(
            // Bonded — note a different country-code prefix; suffix still matches.
            CallRecord("+44771234567", CallType.OUTGOING, 120),
            CallRecord("0771234567", CallType.INCOMING, 60),
            // Saved contact, not bonded.
            CallRecord("0770000000", CallType.OUTGOING, 30),
            // Unknown number.
            CallRecord("0759999999", CallType.MISSED, 0),
        )
        val result = CallAnalytics.compute(
            windowDays = 30,
            calls = calls,
            bondBySuffix = bondBySuffix,
            contactNameBySuffix = contactBySuffix,
            bondedTotalCount = 1,
        )

        assertEquals(4, result.totalCalls)
        // Both bonded calls collapse onto one party (the person).
        assertEquals(2, result.bond.callCount)
        assertEquals(1, result.bond.uniqueParties)
        assertEquals(180, result.bond.totalDurationSec)
        assertEquals(1, result.contact.callCount)
        assertEquals(1, result.unknown.callCount)

        assertEquals(1, result.bondedReachedCount)
        assertEquals(1, result.bondedTotalCount)
        assertEquals(1, result.incomingTotal)
        assertEquals(2, result.outgoingTotal)
        assertEquals(1, result.missedTotal)

        // Top caller is the bonded person (most talk time).
        val top = result.topParties.first()
        assertEquals(CallBucket.BOND, top.bucket)
        assertEquals("Aanya", top.displayName)
    }

    @Test
    fun unreturnedMissedBondCountsAsCallBack() {
        // A single missed call from the bond, nothing since → worth a call back.
        val calls = listOf(
            CallRecord("0771234567", CallType.MISSED, 0, timestampMillis = 1_000),
            CallRecord("0759999999", CallType.MISSED, 0, timestampMillis = 1_000),
        )
        val result = CallAnalytics.compute(
            windowDays = 90,
            calls = calls,
            bondBySuffix = bondBySuffix,
            contactNameBySuffix = contactBySuffix,
            bondedTotalCount = 1,
        )
        assertEquals(2, result.missedTotal)
        assertEquals(1, result.bondsToCallBackCount)
        assertEquals("Aanya", result.bondsToCallBack.single().displayName)
    }

    @Test
    fun missedThenCalledBack_doesNotCount() {
        // Bond missed us, then we called them back later → already handled.
        val calls = listOf(
            CallRecord("0771234567", CallType.MISSED, 0, timestampMillis = 1_000),
            CallRecord("0771234567", CallType.OUTGOING, 90, timestampMillis = 2_000),
        )
        val result = CallAnalytics.compute(
            windowDays = 90,
            calls = calls,
            bondBySuffix = bondBySuffix,
            contactNameBySuffix = contactBySuffix,
            bondedTotalCount = 1,
        )
        // Still one missed call in the totals…
        assertEquals(1, result.missedTotal)
        // …but it's been returned, so it's not "worth a call back".
        assertEquals(0, result.bondsToCallBackCount)
    }

    @Test
    fun answeredAfterMiss_doesNotCount() {
        // Missed, then a later answered incoming call → reconnected.
        val calls = listOf(
            CallRecord("0771234567", CallType.MISSED, 0, timestampMillis = 5_000),
            CallRecord("0771234567", CallType.INCOMING, 200, timestampMillis = 9_000),
        )
        val result = CallAnalytics.compute(
            windowDays = 90,
            calls = calls,
            bondBySuffix = bondBySuffix,
            contactNameBySuffix = contactBySuffix,
            bondedTotalCount = 1,
        )
        assertEquals(0, result.bondsToCallBackCount)
    }

    @Test
    fun callBackThenMissedAgain_countsAgain() {
        // We called back, but later they missed us again → owed once more.
        val calls = listOf(
            CallRecord("0771234567", CallType.MISSED, 0, timestampMillis = 1_000),
            CallRecord("0771234567", CallType.OUTGOING, 90, timestampMillis = 2_000),
            CallRecord("0771234567", CallType.MISSED, 0, timestampMillis = 3_000),
        )
        val result = CallAnalytics.compute(
            windowDays = 90,
            calls = calls,
            bondBySuffix = bondBySuffix,
            contactNameBySuffix = contactBySuffix,
            bondedTotalCount = 1,
        )
        assertEquals(1, result.bondsToCallBackCount)
    }
}
