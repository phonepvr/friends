package com.phonepvr.friends.domain

import com.phonepvr.friends.domain.cadence.CadenceState
import com.phonepvr.friends.domain.review.BondInfo
import com.phonepvr.friends.domain.review.ConnectionHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ConnectionHealthTest {

    private val today = LocalDate.of(2026, 1, 15)
    private val longAgo = today.minusYears(5) // safe "bonded at" for legacy bonds

    private fun bond(
        id: Long,
        name: String = "P$id",
        cadenceDays: Int? = 7,
        bondedAt: LocalDate = longAgo,
    ) = BondInfo(
        personId = id,
        displayName = name,
        photoRelativePath = null,
        cadenceTargetDays = cadenceDays,
        bondedAt = bondedAt,
    )

    @Test fun `no bonds → score 100`() {
        val snap = ConnectionHealth.compute(today, emptyList(), emptyMap())
        assertEquals(100, snap.score)
        assertEquals(0, snap.trackedCount)
    }

    @Test fun `all on-track → score 100`() {
        val bonds = listOf(bond(1), bond(2), bond(3))
        val lastContact = mapOf(
            1L to today.minusDays(1),
            2L to today.minusDays(2),
            3L to today.minusDays(3),
        )
        val snap = ConnectionHealth.compute(today, bonds, lastContact)
        assertEquals(100, snap.score)
        assertEquals(3, snap.onTrackCount)
        assertEquals(0, snap.overdueCount)
    }

    @Test fun `OVERDUE decays as gap grows past cadence`() {
        // Cadence 10 days, last contact 11 days ago = barely overdue (1 day past).
        // Cadence 10 days, last contact 30 days ago = 20 days past target.
        val recentOverdue = bond(1, cadenceDays = 10)
        val deepOverdue = bond(2, cadenceDays = 10)
        val lastContact = mapOf(
            1L to today.minusDays(11),
            2L to today.minusDays(30),
        )
        val snap = ConnectionHealth.compute(today, listOf(recentOverdue, deepOverdue), lastContact)
        assertEquals(2, snap.overdueCount)
        // Recent overdue should outscore deep overdue by a wide margin.
        val recent = snap.byBond.first { it.personId == 1L }.score
        val deep = snap.byBond.first { it.personId == 2L }.score
        assertTrue("recent ($recent) > deep ($deep)", recent > deep)
        assertTrue("deep ($deep) at or near floor", deep <= 0.05f)
    }

    @Test fun `NEVER_CONTACTED counted distinctly from OVERDUE`() {
        val bonds = listOf(bond(1), bond(2))
        // No timeline entries for either — both NEVER_CONTACTED.
        val snap = ConnectionHealth.compute(today, bonds, emptyMap())
        assertEquals(2, snap.neverContactedCount)
        assertEquals(0, snap.overdueCount)
        // Both score 0.4 → aggregate 40.
        assertEquals(40, snap.score)
    }

    @Test fun `NOT_TRACKED bonds do not drag down the score`() {
        val tracked = bond(1, cadenceDays = 7)
        val untracked = bond(2, cadenceDays = null)
        val lastContact = mapOf(1L to today.minusDays(1)) // tracked is on-track
        val snap = ConnectionHealth.compute(today, listOf(tracked, untracked), lastContact)
        assertEquals(100, snap.score)
        assertEquals(1, snap.trackedCount)
    }

    @Test fun `trend rises when overdue is resolved`() {
        // Person 1: cadence 7d. 40 days ago = overdue. Today: contacted yesterday = on-track.
        val bonds = listOf(bond(1, cadenceDays = 7))
        val contactDates = mapOf(
            // Includes a contact within the past 30 days, so "now" is on-track
            // but "30 days ago" had only the 40-day-old contact (overdue).
            1L to listOf(today.minusDays(40), today.minusDays(1)),
        )
        val trend = ConnectionHealth.computeWithTrend(today, bonds, contactDates, lookbackDays = 30L)
        assertTrue("now should be on-track", trend.now.score == 100)
        assertTrue("previous should be overdue", trend.previous.score < 50)
        assertTrue("delta positive", trend.deltaScore > 0)
    }

    @Test fun `bonds added after the cutoff don't count in the past snapshot`() {
        // Old bond, deeply overdue past + now.
        val oldBond = bond(1, cadenceDays = 7, bondedAt = today.minusYears(1))
        // New bond, added a week ago, no contact yet.
        val newBond = bond(2, cadenceDays = 7, bondedAt = today.minusDays(7))
        val contactDates = mapOf(
            1L to listOf(today.minusDays(60)),
            // person 2 has no contact at all
        )
        val trend = ConnectionHealth.computeWithTrend(today, listOf(oldBond, newBond), contactDates, 30L)
        // Past snapshot should only include the old bond.
        assertEquals(1, trend.previous.byBond.size)
        assertEquals(1L, trend.previous.byBond.single().personId)
        // Now snapshot should include both.
        assertEquals(2, trend.now.byBond.size)
    }

    @Test fun `DUE_SOON scores between ON_TRACK and OVERDUE`() {
        // Cadence 10d, last contact 8d ago = 2d until due = DUE_SOON.
        val bonds = listOf(bond(1, cadenceDays = 10))
        val lastContact = mapOf(1L to today.minusDays(8))
        val snap = ConnectionHealth.compute(today, bonds, lastContact)
        assertEquals(1, snap.dueSoonCount)
        assertEquals(70, snap.score)
    }
}
