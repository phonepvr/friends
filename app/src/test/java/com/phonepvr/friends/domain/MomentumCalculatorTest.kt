package com.phonepvr.friends.domain

import com.phonepvr.friends.domain.review.MomentumCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MomentumCalculatorTest {

    private val today = LocalDate.of(2026, 1, 15)

    @Test fun `empty timeline → all zero, no activity`() {
        val m = MomentumCalculator.compute(today, emptyList())
        assertEquals(14, m.days.size)
        assertEquals(0, m.activeDays)
        assertEquals(0, m.last7Count)
        assertEquals(0, m.prev7Count)
        assertEquals(0, m.weekDelta)
        assertFalse(m.hasActivity)
        assertEquals(0, m.maxCount)
    }

    @Test fun `window is oldest to newest and last bar is today`() {
        val m = MomentumCalculator.compute(today, emptyList())
        assertEquals(today.minusDays(13), m.days.first().date)
        assertEquals(today, m.days.last().date)
        assertTrue(m.days.last().isToday)
        assertFalse(m.days.first().isToday)
    }

    @Test fun `per-day counts and active days`() {
        val contacts = listOf(
            today,                 // today
            today,                 // today again (2 today)
            today.minusDays(1),    // yesterday
            today.minusDays(10),   // older
        )
        val m = MomentumCalculator.compute(today, contacts)
        assertEquals(2, m.days.last().count)
        assertEquals(1, m.days[m.days.size - 2].count)
        assertEquals(2, m.maxCount)
        assertEquals(3, m.activeDays) // today, yesterday, day-10
        assertTrue(m.hasActivity)
    }

    @Test fun `week-over-week delta`() {
        val contacts = buildList {
            // last 7 days (today .. today-6): 3 contacts
            add(today)
            add(today.minusDays(2))
            add(today.minusDays(5))
            // prev 7 days (today-7 .. today-13): 1 contact
            add(today.minusDays(9))
        }
        val m = MomentumCalculator.compute(today, contacts)
        assertEquals(3, m.last7Count)
        assertEquals(1, m.prev7Count)
        assertEquals(2, m.weekDelta)
    }

    @Test fun `contacts outside the window are ignored`() {
        val contacts = listOf(today.minusDays(20), today.minusDays(40))
        val m = MomentumCalculator.compute(today, contacts)
        assertEquals(0, m.activeDays)
        assertFalse(m.hasActivity)
    }
}
