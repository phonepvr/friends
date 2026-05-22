package com.phonepvr.friends.domain.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * A date that recurs every year, such as a birthday or wedding anniversary.
 * [year] is null when only the month and day are known.
 */
data class AnnualDate(
    val month: Int,
    val day: Int,
    val year: Int? = null,
) {
    init {
        require(month in 1..12) { "month must be in 1..12, was $month" }
        require(day in 1..31) { "day must be in 1..31, was $day" }
    }

    /**
     * This date as it falls in [targetYear]. The day is clamped to the last
     * day of the month, so Feb 29 becomes Feb 28 in non-leap years.
     */
    fun occurrenceInYear(targetYear: Int): LocalDate {
        val lastDayOfMonth = YearMonth.of(targetYear, month).lengthOfMonth()
        return LocalDate.of(targetYear, month, minOf(day, lastDayOfMonth))
    }

    /** The next occurrence on or after [from]. */
    fun nextOccurrenceOnOrAfter(from: LocalDate): LocalDate {
        val thisYear = occurrenceInYear(from.year)
        return if (thisYear.isBefore(from)) occurrenceInYear(from.year + 1) else thisYear
    }

    /** Whole days from [from] until the next occurrence; 0 when [from] is the day. */
    fun daysUntilNextOccurrence(from: LocalDate): Long =
        ChronoUnit.DAYS.between(from, nextOccurrenceOnOrAfter(from))
}
