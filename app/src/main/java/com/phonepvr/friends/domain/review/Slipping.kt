package com.phonepvr.friends.domain.review

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A bond whose contact frequency is dropping relative to its own history —
 * an early warning that fires *before* a cadence goes overdue, and works even
 * for untracked bonds (no cadence set) that you simply used to talk to often.
 */
data class SlippingBond(
    val personId: Long,
    val displayName: String,
    val photoRelativePath: String?,
    /** Typical days between contacts for this person (median of past gaps). */
    val baselineGapDays: Int,
    /** Days since the most recent contact. */
    val openGapDays: Int,
    /** openGap / baseline — how far beyond their norm the current silence is. */
    val ratio: Float,
)

/**
 * Detects "going quiet" bonds. Deliberately distinct from cadence/overdue:
 * cadence is the user's stated target, this is the person's *observed* rhythm.
 * Anyone already surfaced as overdue/due-soon should be passed in
 * [excludePersonIds] so we never list the same person in two attention rails.
 */
object SlippingDetector {

    /** Need at least this many contacts to have ≥2 gaps and a stable baseline. */
    private const val MIN_CONTACTS = 3

    /** Below this, day-to-day noise dominates (e.g. a daily texter skipping a day). */
    private const val MIN_OPEN_GAP_DAYS = 7

    /** Open gap must be at least this multiple of the baseline to count as slipping. */
    private const val SLIP_RATIO = 1.5f

    fun detect(
        today: LocalDate,
        contactDatesByPerson: Map<Long, List<LocalDate>>,
        nameById: Map<Long, String>,
        photoById: Map<Long, String?>,
        excludePersonIds: Set<Long> = emptySet(),
        limit: Int = 5,
    ): List<SlippingBond> =
        contactDatesByPerson.mapNotNull { (id, rawDates) ->
            if (id in excludePersonIds) return@mapNotNull null
            val name = nameById[id] ?: return@mapNotNull null

            val dates = rawDates.distinct().sorted()
            if (dates.size < MIN_CONTACTS) return@mapNotNull null

            val gaps = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }
                .filter { it > 0 }
            if (gaps.isEmpty()) return@mapNotNull null

            val baseline = median(gaps).coerceAtLeast(1L)
            val openGap = ChronoUnit.DAYS.between(dates.last(), today)
            if (openGap < MIN_OPEN_GAP_DAYS) return@mapNotNull null

            val ratio = openGap.toFloat() / baseline.toFloat()
            if (ratio < SLIP_RATIO) return@mapNotNull null

            SlippingBond(
                personId = id,
                displayName = name,
                photoRelativePath = photoById[id],
                baselineGapDays = baseline.toInt(),
                openGapDays = openGap.toInt(),
                ratio = ratio,
            )
        }
            .sortedByDescending { it.ratio }
            .take(limit)

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2
        }
    }
}
