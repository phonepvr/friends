package com.phonepvr.friends.domain

import com.phonepvr.friends.domain.review.SlippingDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SlippingDetectorTest {

    private val today = LocalDate.of(2026, 1, 15)

    /** Builds a regular contact history every [gap] days ending [endDaysAgo] ago. */
    private fun regular(count: Int, gap: Int, endDaysAgo: Int): List<LocalDate> =
        (0 until count).map { i ->
            today.minusDays((endDaysAgo + (count - 1 - i) * gap).toLong())
        }

    private fun names(vararg ids: Long) = ids.associateWith { "P$it" }
    private fun photos(vararg ids: Long) = ids.associateWith { null as String? }

    @Test fun `regular contact then long silence is slipping`() {
        // Talked every ~5 days, last contact 15 days ago → ratio ~3.
        val contacts = mapOf(1L to regular(count = 5, gap = 5, endDaysAgo = 15))
        val result = SlippingDetector.detect(today, contacts, names(1), photos(1))
        assertEquals(1, result.size)
        val s = result.single()
        assertEquals(1L, s.personId)
        assertEquals(5, s.baselineGapDays)
        assertEquals(15, s.openGapDays)
        assertTrue("ratio ~3", s.ratio in 2.8f..3.2f)
    }

    @Test fun `fewer than 3 contacts has no baseline`() {
        val contacts = mapOf(1L to listOf(today.minusDays(30), today.minusDays(60)))
        val result = SlippingDetector.detect(today, contacts, names(1), photos(1))
        assertTrue(result.isEmpty())
    }

    @Test fun `on-rhythm bond is not slipping`() {
        // Every 5 days, last contact only 4 days ago → ratio < 1.5.
        val contacts = mapOf(1L to regular(count = 5, gap = 5, endDaysAgo = 4))
        val result = SlippingDetector.detect(today, contacts, names(1), photos(1))
        assertTrue(result.isEmpty())
    }

    @Test fun `daily talker quiet a few days is filtered as noise`() {
        // Baseline 1 day, silent 5 days → ratio 5 but openGap < MIN_OPEN_GAP_DAYS.
        val contacts = mapOf(1L to regular(count = 6, gap = 1, endDaysAgo = 5))
        val result = SlippingDetector.detect(today, contacts, names(1), photos(1))
        assertTrue(result.isEmpty())
    }

    @Test fun `excluded ids are skipped`() {
        val contacts = mapOf(1L to regular(count = 5, gap = 5, endDaysAgo = 15))
        val result = SlippingDetector.detect(
            today, contacts, names(1), photos(1), excludePersonIds = setOf(1L),
        )
        assertTrue(result.isEmpty())
    }

    @Test fun `results are sorted by severity and capped`() {
        val contacts = mapOf(
            1L to regular(count = 4, gap = 5, endDaysAgo = 12), // ratio ~2.4
            2L to regular(count = 4, gap = 5, endDaysAgo = 30), // ratio ~6
            3L to regular(count = 4, gap = 10, endDaysAgo = 16), // ratio ~1.6
        )
        val result = SlippingDetector.detect(
            today, contacts, names(1, 2, 3), photos(1, 2, 3), limit = 2,
        )
        assertEquals(2, result.size)
        assertEquals(2L, result[0].personId) // most severe first
        assertEquals(1L, result[1].personId)
        assertTrue(result[0].ratio > result[1].ratio)
    }
}
