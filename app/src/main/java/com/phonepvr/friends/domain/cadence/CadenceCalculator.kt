package com.phonepvr.friends.domain.cadence

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class CadenceState {
    NOT_TRACKED,
    NEVER_CONTACTED,
    ON_TRACK,
    DUE_SOON,
    OVERDUE,
}

data class CadenceStatus(
    val state: CadenceState,
    val daysSinceLastContact: Long?,
    val daysUntilDue: Long?,
)

/**
 * Pure cadence logic: how a person's "stay in touch" target compares to the
 * date they were last contacted. No Android dependencies, so it is fully
 * unit-testable on the JVM.
 */
object CadenceCalculator {

    /**
     * @param lastContact date of the most recent interaction that counts as
     *   contact, or null if there has been none.
     * @param cadenceTargetDays how often the user wants to stay in touch, or
     *   null/non-positive when this person is not tracked.
     * @param dueSoonWindowDays how many days before the due date a person is
     *   considered "due soon".
     */
    fun status(
        lastContact: LocalDate?,
        cadenceTargetDays: Int?,
        today: LocalDate,
        dueSoonWindowDays: Int = 3,
    ): CadenceStatus {
        if (cadenceTargetDays == null || cadenceTargetDays <= 0) {
            return CadenceStatus(CadenceState.NOT_TRACKED, null, null)
        }
        if (lastContact == null) {
            return CadenceStatus(CadenceState.NEVER_CONTACTED, null, null)
        }
        val daysSince = ChronoUnit.DAYS.between(lastContact, today)
        val dueDate = lastContact.plusDays(cadenceTargetDays.toLong())
        val daysUntilDue = ChronoUnit.DAYS.between(today, dueDate)
        val state = when {
            daysUntilDue < 0 -> CadenceState.OVERDUE
            daysUntilDue <= dueSoonWindowDays -> CadenceState.DUE_SOON
            else -> CadenceState.ON_TRACK
        }
        return CadenceStatus(state, daysSince, daysUntilDue)
    }
}
