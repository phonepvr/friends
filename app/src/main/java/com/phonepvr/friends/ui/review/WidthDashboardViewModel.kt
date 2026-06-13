package com.phonepvr.friends.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.repository.PeopleRepository
import com.phonepvr.friends.data.repository.TimelineRepository
import com.phonepvr.friends.domain.cadence.CadenceState
import com.phonepvr.friends.domain.review.BondHealth
import com.phonepvr.friends.domain.review.BondInfo
import com.phonepvr.friends.domain.review.ConnectionHealth
import com.phonepvr.friends.domain.review.HealthWithTrend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** How far back the trend compares — exposed so the UI can label "vs 30 days ago". */
const val HEALTH_TREND_LOOKBACK_DAYS: Long = 30L

@HiltViewModel
class WidthDashboardViewModel @Inject constructor(
    peopleRepository: PeopleRepository,
    timelineRepository: TimelineRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** Everything the dashboard derives from, computed once per data change. */
    private data class Inputs(
        val bonds: List<BondInfo>,
        val contactDatesByPerson: Map<Long, List<LocalDate>>,
    )

    private val inputs: StateFlow<Inputs?> = combine(
        peopleRepository.observeActiveWithDetails(),
        timelineRepository.observeAll(),
    ) { people, timeline ->
        val bonds = people.map { p ->
            BondInfo(
                personId = p.person.id,
                displayName = p.person.displayName,
                photoRelativePath = p.person.photoRelativePath,
                cadenceTargetDays = p.person.cadenceTargetDays,
                bondedAt = p.person.createdAt.toLocalDate(),
            )
        }
        val datesByPerson = timeline
            .filter { it.countsAsContact }
            .groupBy { it.personId }
            .mapValues { (_, entries) -> entries.map { it.occurredAt.toLocalDate() } }
        Inputs(bonds = bonds, contactDatesByPerson = datesByPerson)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val health: StateFlow<HealthWithTrend?> = inputs
        .map { i ->
            i ?: return@map null
            ConnectionHealth.computeWithTrend(
                today = LocalDate.now(),
                bonds = i.bonds,
                contactDatesByPerson = i.contactDatesByPerson,
                lookbackDays = HEALTH_TREND_LOOKBACK_DAYS,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Bonds that need attention right now — most-overdue first, then due soon,
     * then never-contacted. Capped at 6 so the carousel stays glanceable.
     */
    val needsYou: StateFlow<List<BondHealth>> = health
        .map { it?.now?.byBond.orEmpty() }
        .map { bonds ->
            bonds
                .filter {
                    it.cadence.state == CadenceState.OVERDUE ||
                        it.cadence.state == CadenceState.DUE_SOON ||
                        it.cadence.state == CadenceState.NEVER_CONTACTED
                }
                .sortedBy { sortKey(it) }
                .take(6)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    /**
     * Most-overdue first, then due-soon by closest-to-due, then
     * never-contacted at the end of the urgency band.
     */
    private fun sortKey(bond: BondHealth): Long = when (bond.cadence.state) {
        CadenceState.OVERDUE -> bond.cadence.daysUntilDue ?: Long.MIN_VALUE
        CadenceState.DUE_SOON -> 1_000_000L + (bond.cadence.daysUntilDue ?: 0L)
        CadenceState.NEVER_CONTACTED -> 2_000_000L
        else -> Long.MAX_VALUE
    }
}
