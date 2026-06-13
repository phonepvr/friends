package com.phonepvr.friends.domain.review

import com.phonepvr.friends.domain.cadence.CadenceCalculator
import com.phonepvr.friends.domain.cadence.CadenceState
import com.phonepvr.friends.domain.cadence.CadenceStatus
import java.time.LocalDate

/**
 * Per-bond relationship health at a single point in time.
 *
 * [score] is 0.0..1.0 — ON_TRACK and untracked = 1.0, DUE_SOON = 0.7,
 * NEVER_CONTACTED = 0.4, OVERDUE decays from 0.5 down to 0.0 as the overdue
 * gap exceeds the cadence target. Aggregated by [HealthSnapshot] into the
 * dashboard's 0–100 score.
 */
data class BondHealth(
    val personId: Long,
    val displayName: String,
    val photoRelativePath: String?,
    val cadence: CadenceStatus,
    val score: Float,
)

/** Aggregate dashboard view at a single timestamp. */
data class HealthSnapshot(
    /** 0..100 weighted-average bond health across tracked bonds. */
    val score: Int,
    val byBond: List<BondHealth>,
    val overdueCount: Int,
    val dueSoonCount: Int,
    val onTrackCount: Int,
    val neverContactedCount: Int,
    val trackedCount: Int,
)

/** Current snapshot paired with one from [lookbackDays] ago, for trends. */
data class HealthWithTrend(
    val now: HealthSnapshot,
    val previous: HealthSnapshot,
    /** now.score - previous.score. Negative when health has slipped. */
    val deltaScore: Int,
)

/** Lightweight bonded-person view for [ConnectionHealth]. */
data class BondInfo(
    val personId: Long,
    val displayName: String,
    val photoRelativePath: String?,
    val cadenceTargetDays: Int?,
    /** When the user bonded this person — gates the past-snapshot fairness. */
    val bondedAt: LocalDate,
)

/**
 * Pure-Kotlin relationship-health computation for the Width dashboard.
 *
 * Stateless. The trend version replays the timeline by truncating each
 * person's contact history to a cutoff date, so we get a meaningful
 * "vs last month" delta without any snapshot table — what we already
 * store is enough.
 */
object ConnectionHealth {

    private const val SCORE_ON_TRACK = 1.0f
    private const val SCORE_DUE_SOON = 0.7f
    private const val SCORE_NEVER = 0.4f
    private const val SCORE_OVERDUE_FLOOR = 0.0f
    private const val SCORE_OVERDUE_CEIL = 0.5f

    fun compute(
        today: LocalDate,
        bonds: List<BondInfo>,
        lastContactByPerson: Map<Long, LocalDate>,
    ): HealthSnapshot {
        val byBond = bonds.map { bond ->
            val cadence = CadenceCalculator.status(
                lastContact = lastContactByPerson[bond.personId],
                cadenceTargetDays = bond.cadenceTargetDays,
                today = today,
            )
            BondHealth(
                personId = bond.personId,
                displayName = bond.displayName,
                photoRelativePath = bond.photoRelativePath,
                cadence = cadence,
                score = scoreFor(cadence, bond.cadenceTargetDays),
            )
        }
        val tracked = byBond.filter { it.cadence.state != CadenceState.NOT_TRACKED }
        val aggregateScore = if (tracked.isEmpty()) {
            // No one is being tracked — there's nothing to fall behind on.
            // 100 reads better than 0 here and matches "healthy by default".
            100
        } else {
            val sum = tracked.sumOf { (it.score * 100).toInt() }
            (sum / tracked.size).coerceIn(0, 100)
        }
        return HealthSnapshot(
            score = aggregateScore,
            byBond = byBond,
            overdueCount = byBond.count { it.cadence.state == CadenceState.OVERDUE },
            dueSoonCount = byBond.count { it.cadence.state == CadenceState.DUE_SOON },
            onTrackCount = byBond.count { it.cadence.state == CadenceState.ON_TRACK },
            neverContactedCount = byBond.count { it.cadence.state == CadenceState.NEVER_CONTACTED },
            trackedCount = tracked.size,
        )
    }

    /**
     * Compute the current snapshot and one from [lookbackDays] ago by
     * truncating each person's contact history to that cutoff. Bonds the
     * user added AFTER the cutoff are excluded from the past snapshot so
     * a newly-bonded contact doesn't inflate the delta either way.
     */
    fun computeWithTrend(
        today: LocalDate,
        bonds: List<BondInfo>,
        contactDatesByPerson: Map<Long, List<LocalDate>>,
        lookbackDays: Long,
    ): HealthWithTrend {
        val nowLastContact = contactDatesByPerson
            .mapNotNull { (id, dates) -> dates.maxOrNull()?.let { id to it } }
            .toMap()
        val now = compute(today, bonds, nowLastContact)

        val pastDate = today.minusDays(lookbackDays)
        val bondsAsOfPast = bonds.filter { !it.bondedAt.isAfter(pastDate) }
        val pastLastContact = contactDatesByPerson
            .mapNotNull { (id, dates) ->
                dates.filter { !it.isAfter(pastDate) }.maxOrNull()?.let { id to it }
            }
            .toMap()
        val previous = compute(pastDate, bondsAsOfPast, pastLastContact)

        return HealthWithTrend(
            now = now,
            previous = previous,
            deltaScore = now.score - previous.score,
        )
    }

    private fun scoreFor(cadence: CadenceStatus, cadenceTargetDays: Int?): Float =
        when (cadence.state) {
            CadenceState.NOT_TRACKED -> SCORE_ON_TRACK
            CadenceState.ON_TRACK -> SCORE_ON_TRACK
            CadenceState.DUE_SOON -> SCORE_DUE_SOON
            CadenceState.NEVER_CONTACTED -> SCORE_NEVER
            CadenceState.OVERDUE -> {
                val overdueDays = -(cadence.daysUntilDue ?: 0L)
                val target = (cadenceTargetDays ?: 30).coerceAtLeast(1)
                val decay = (overdueDays.toFloat() / target.toFloat()).coerceIn(0f, 1f)
                (SCORE_OVERDUE_CEIL - (SCORE_OVERDUE_CEIL - SCORE_OVERDUE_FLOOR) * decay)
                    .coerceIn(SCORE_OVERDUE_FLOOR, SCORE_OVERDUE_CEIL)
            }
        }
}
