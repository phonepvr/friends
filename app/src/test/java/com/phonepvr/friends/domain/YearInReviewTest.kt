package com.phonepvr.friends.domain

import com.phonepvr.friends.domain.model.InteractionType
import com.phonepvr.friends.domain.review.EventInfo
import com.phonepvr.friends.domain.review.PersonInfo
import com.phonepvr.friends.domain.review.TimelinePoint
import com.phonepvr.friends.domain.review.YearInReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class YearInReviewTest {

    @Test
    fun emptyDataset_returnsZeros() {
        val review = YearInReview.compute(
            year = 2026,
            people = emptyList(),
            timeline = emptyList(),
            events = emptyList(),
        )
        assertEquals(0, review.totalInteractions)
        assertEquals(0, review.contactInteractions)
        assertNull(review.mostConnected)
        assertNull(review.leastConnectedTracked)
        assertNull(review.longestGap)
        assertEquals(0, review.acknowledgedEventCount)
        assertEquals(0, review.totalEvents)
        assertEquals(0, review.typeBreakdown.values.sum())
    }

    @Test
    fun sparseDataset_picksMostConnectedButNotEnoughForGaps() {
        val aanya = PersonInfo(id = 1, displayName = "Aanya", isTracked = true)
        val bo = PersonInfo(id = 2, displayName = "Bo", isTracked = false)
        val timeline = listOf(
            point(aanya, "2026-01-10", InteractionType.CALL),
            point(aanya, "2026-03-05", InteractionType.MEET),
            point(bo, "2026-02-14", InteractionType.MESSAGE),
        )
        val review = YearInReview.compute(
            year = 2026,
            people = listOf(aanya, bo),
            timeline = timeline,
            events = emptyList(),
        )
        assertEquals(3, review.totalInteractions)
        assertEquals(3, review.contactInteractions)
        assertEquals(1, review.mostConnected?.personId)
        assertEquals(2, review.mostConnected?.count)
        // Aanya is tracked and has two interactions; she shows up as least too
        // since she is the only tracked person with a positive count.
        assertEquals(1, review.leastConnectedTracked?.personId)
        // Only Aanya has 2+ contacts so her gap is the only candidate.
        assertEquals(1, review.longestGap?.personId)
        assertEquals(LocalDate.parse("2026-01-10"), review.longestGap?.fromDate)
        assertEquals(LocalDate.parse("2026-03-05"), review.longestGap?.toDate)
    }

    @Test
    fun denseDataset_aggregatesAllStats() {
        val aanya = PersonInfo(id = 1, displayName = "Aanya", isTracked = true)
        val bo = PersonInfo(id = 2, displayName = "Bo", isTracked = true)
        val cara = PersonInfo(id = 3, displayName = "Cara", isTracked = false)
        val people = listOf(aanya, bo, cara)
        val events = listOf(
            EventInfo(personId = aanya.id, month = 3, day = 15),
            EventInfo(personId = bo.id, month = 7, day = 4),
        )
        val timeline = buildList {
            // Aanya: 4 contact interactions, big gap mid-year.
            add(point(aanya, "2026-01-05", InteractionType.CALL))
            add(point(aanya, "2026-01-20", InteractionType.MEET))
            add(point(aanya, "2026-06-30", InteractionType.MESSAGE))
            add(point(aanya, "2026-03-15", InteractionType.MEET)) // birthday acked
            // Bo: 2 contact interactions, small gap.
            add(point(bo, "2026-05-01", InteractionType.CALL))
            add(point(bo, "2026-05-10", InteractionType.MESSAGE))
            // Cara: 1 contact (untracked, so not eligible for least/gap).
            add(point(cara, "2026-08-12", InteractionType.OTHER))
            // A non-contact entry that should affect totals but not contact count.
            add(point(aanya, "2026-02-01", InteractionType.OTHER, countsAsContact = false))
            // An entry in a different year — must be excluded.
            add(point(aanya, "2025-12-31", InteractionType.CALL))
        }
        val review = YearInReview.compute(2026, people, timeline, events)

        assertEquals(8, review.totalInteractions)         // 9 entries, 1 in 2025
        assertEquals(7, review.contactInteractions)       // 8 in-year minus 1 non-contact
        assertEquals(aanya.id, review.mostConnected?.personId)
        assertEquals(4, review.mostConnected?.count)
        // Least connected tracked: Bo (2) < Aanya (4); Cara is untracked.
        assertEquals(bo.id, review.leastConnectedTracked?.personId)
        assertEquals(2, review.leastConnectedTracked?.count)
        // Longest gap is Aanya's Jan 20 -> Mar 15 (54 days), beating Mar 15 -> Jun 30 (107).
        // Actually Mar 15 -> Jun 30 is longer; max is across her sorted dates.
        // Sorted: Jan 5, Jan 20, Mar 15, Jun 30 -> pairs 15d, 54d, 107d. Max = 107.
        assertNotNull(review.longestGap)
        assertEquals(aanya.id, review.longestGap?.personId)
        assertEquals(107L, review.longestGap?.days)
        // Aanya's birthday Mar 15 is acknowledged (timeline entry that day).
        // Bo's July 4 is not.
        assertEquals(1, review.acknowledgedEventCount)
        assertEquals(2, review.totalEvents)
        assertEquals(2, review.typeBreakdown[InteractionType.CALL])
        assertEquals(2, review.typeBreakdown[InteractionType.MEET])
        assertEquals(2, review.typeBreakdown[InteractionType.MESSAGE])
        assertEquals(2, review.typeBreakdown[InteractionType.OTHER])
    }

    @Test
    fun availableYears_includesCurrentEvenWithoutData() {
        val years = YearInReview.availableYears(emptyList(), currentYear = 2026)
        assertEquals(listOf(2026), years)
    }

    @Test
    fun availableYears_isDescendingAndDistinct() {
        val timeline = listOf(
            point(PersonInfo(1, "A", true), "2024-06-01", InteractionType.CALL),
            point(PersonInfo(1, "A", true), "2025-06-01", InteractionType.CALL),
            point(PersonInfo(1, "A", true), "2024-12-01", InteractionType.CALL),
        )
        val years = YearInReview.availableYears(timeline, currentYear = 2026)
        assertEquals(listOf(2026, 2025, 2024), years)
    }

    @Test
    fun includeSilent_surfacesNeverContactedPerson() {
        val tracked = PersonInfo(1, "Quiet", isTracked = true)
        val review = YearInReview.compute(
            year = 2026,
            people = listOf(tracked),
            timeline = emptyList(),
            events = emptyList(),
            includeSilent = true,
        )
        assertEquals(0, review.leastConnectedTracked?.count)
        assertEquals(tracked.id, review.leastConnectedTracked?.personId)
    }

    @Test
    fun acknowledgedEvent_requiresContactInteraction() {
        val person = PersonInfo(1, "A", true)
        val events = listOf(EventInfo(person.id, month = 3, day = 15))
        // Same-day entry but doesn't count as contact -> not acknowledged.
        val notContact = point(person, "2026-03-15", InteractionType.OTHER, countsAsContact = false)
        val review = YearInReview.compute(
            year = 2026,
            people = listOf(person),
            timeline = listOf(notContact),
            events = events,
        )
        assertEquals(0, review.acknowledgedEventCount)
        // Same-day entry that counts as contact -> acknowledged.
        val contact = point(person, "2026-03-15", InteractionType.MEET)
        val review2 = YearInReview.compute(
            year = 2026,
            people = listOf(person),
            timeline = listOf(contact),
            events = events,
        )
        assertEquals(1, review2.acknowledgedEventCount)
        assertTrue(review2.acknowledgedEventCount <= review2.totalEvents)
    }

    private fun point(
        person: PersonInfo,
        date: String,
        type: InteractionType,
        countsAsContact: Boolean = true,
    ): TimelinePoint = TimelinePoint(
        personId = person.id,
        date = LocalDate.parse(date),
        type = type,
        countsAsContact = countsAsContact,
    )
}
