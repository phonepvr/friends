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
    fun missedFromBondsCountedSeparately() {
        val calls = listOf(
            CallRecord("0771234567", CallType.MISSED, 0),
            CallRecord("0759999999", CallType.MISSED, 0),
        )
        val result = CallAnalytics.compute(
            windowDays = 90,
            calls = calls,
            bondBySuffix = bondBySuffix,
            contactNameBySuffix = contactBySuffix,
            bondedTotalCount = 1,
        )
        assertEquals(2, result.missedTotal)
        assertEquals(1, result.missedFromBonds)
    }
}
