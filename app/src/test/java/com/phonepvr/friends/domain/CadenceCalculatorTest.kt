package com.phonepvr.friends.domain

import com.phonepvr.friends.domain.cadence.CadenceCalculator
import com.phonepvr.friends.domain.cadence.CadenceState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CadenceCalculatorTest {

    private val today = LocalDate.of(2026, 5, 22)

    @Test
    fun nullTarget_isNotTracked() {
        val status = CadenceCalculator.status(
            lastContact = LocalDate.of(2026, 1, 1),
            cadenceTargetDays = null,
            today = today,
        )
        assertEquals(CadenceState.NOT_TRACKED, status.state)
    }

    @Test
    fun nullLastContact_isNeverContacted() {
        val status = CadenceCalculator.status(
            lastContact = null,
            cadenceTargetDays = 30,
            today = today,
        )
        assertEquals(CadenceState.NEVER_CONTACTED, status.state)
    }

    @Test
    fun recentContact_isOnTrack() {
        val status = CadenceCalculator.status(
            lastContact = today.minusDays(5),
            cadenceTargetDays = 30,
            today = today,
        )
        assertEquals(CadenceState.ON_TRACK, status.state)
        assertEquals(5L, status.daysSinceLastContact)
        assertEquals(25L, status.daysUntilDue)
    }

    @Test
    fun withinDueSoonWindow_isDueSoon() {
        val status = CadenceCalculator.status(
            lastContact = today.minusDays(28),
            cadenceTargetDays = 30,
            today = today,
        )
        assertEquals(CadenceState.DUE_SOON, status.state)
        assertEquals(2L, status.daysUntilDue)
    }

    @Test
    fun pastDueDate_isOverdue() {
        val status = CadenceCalculator.status(
            lastContact = today.minusDays(40),
            cadenceTargetDays = 30,
            today = today,
        )
        assertEquals(CadenceState.OVERDUE, status.state)
        assertEquals(-10L, status.daysUntilDue)
    }

    @Test
    fun exactlyOnDueDate_isDueSoon() {
        val status = CadenceCalculator.status(
            lastContact = today.minusDays(30),
            cadenceTargetDays = 30,
            today = today,
        )
        assertEquals(CadenceState.DUE_SOON, status.state)
        assertEquals(0L, status.daysUntilDue)
    }
}
