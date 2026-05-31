package com.phonepvr.friends.domain.review

import java.time.LocalDate

/** One day's contact tally in the momentum strip. */
data class MomentumDay(
    val date: LocalDate,
    val count: Int,
    val isToday: Boolean,
)

/**
 * Recent contact rhythm across all bonds — the "am I keeping up?" pulse that
 * complements the per-bond health score. Pure data; the UI renders [days] as
 * a small bar strip.
 */
data class Momentum(
    /** Oldest → newest, length == [windowDays]. */
    val days: List<MomentumDay>,
    val last7Count: Int,
    val prev7Count: Int,
    /** Days in the window with at least one contact. */
    val activeDays: Int,
    val windowDays: Int,
) {
    val weekDelta: Int get() = last7Count - prev7Count
    val maxCount: Int get() = days.maxOfOrNull { it.count } ?: 0
    val hasActivity: Boolean get() = days.any { it.count > 0 }
}

/** Pure-Kotlin momentum computation. No Android deps; JVM-testable. */
object MomentumCalculator {

    fun compute(
        today: LocalDate,
        contactDates: List<LocalDate>,
        windowDays: Int = 14,
    ): Momentum {
        val countByDate = contactDates.groupingBy { it }.eachCount()
        val days = (0 until windowDays).map { offset ->
            val date = today.minusDays((windowDays - 1 - offset).toLong())
            MomentumDay(
                date = date,
                count = countByDate[date] ?: 0,
                isToday = date == today,
            )
        }
        // prev7 is the 7 days immediately before the most recent 7 in the window.
        val last7 = days.takeLast(7).sumOf { it.count }
        val prev7 = days.dropLast(7).takeLast(7).sumOf { it.count }
        return Momentum(
            days = days,
            last7Count = last7,
            prev7Count = prev7,
            activeDays = days.count { it.count > 0 },
            windowDays = windowDays,
        )
    }
}
