package com.phonepvr.friends.domain

import com.phonepvr.friends.domain.model.AnnualDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AnnualDateTest {

    @Test
    fun nextOccurrence_laterThisYear_returnsThisYear() {
        val date = AnnualDate(month = 8, day = 15)
        val from = LocalDate.of(2026, 3, 1)
        assertEquals(LocalDate.of(2026, 8, 15), date.nextOccurrenceOnOrAfter(from))
    }

    @Test
    fun nextOccurrence_alreadyPassedThisYear_returnsNextYear() {
        val date = AnnualDate(month = 1, day = 10)
        val from = LocalDate.of(2026, 6, 1)
        assertEquals(LocalDate.of(2027, 1, 10), date.nextOccurrenceOnOrAfter(from))
    }

    @Test
    fun nextOccurrence_onTheDay_returnsThatDay() {
        val date = AnnualDate(month = 5, day = 22)
        val from = LocalDate.of(2026, 5, 22)
        assertEquals(from, date.nextOccurrenceOnOrAfter(from))
    }

    @Test
    fun feb29_inNonLeapYear_clampsToFeb28() {
        val date = AnnualDate(month = 2, day = 29)
        assertEquals(LocalDate.of(2027, 2, 28), date.occurrenceInYear(2027))
    }

    @Test
    fun feb29_inLeapYear_staysFeb29() {
        val date = AnnualDate(month = 2, day = 29)
        assertEquals(LocalDate.of(2028, 2, 29), date.occurrenceInYear(2028))
    }

    @Test
    fun feb29_nextOccurrence_clampsAcrossNonLeapYear() {
        val date = AnnualDate(month = 2, day = 29)
        val from = LocalDate.of(2026, 6, 1)
        assertEquals(LocalDate.of(2027, 2, 28), date.nextOccurrenceOnOrAfter(from))
    }

    @Test
    fun day31_inThirtyDayMonth_clampsToLastDay() {
        val date = AnnualDate(month = 4, day = 31)
        assertEquals(LocalDate.of(2026, 4, 30), date.occurrenceInYear(2026))
    }

    @Test
    fun daysUntilNextOccurrence_countsWholeDays() {
        val date = AnnualDate(month = 5, day = 25)
        val from = LocalDate.of(2026, 5, 22)
        assertEquals(3L, date.daysUntilNextOccurrence(from))
    }

    @Test
    fun daysUntilNextOccurrence_isZeroOnTheDay() {
        val date = AnnualDate(month = 5, day = 22)
        val from = LocalDate.of(2026, 5, 22)
        assertEquals(0L, date.daysUntilNextOccurrence(from))
    }
}
