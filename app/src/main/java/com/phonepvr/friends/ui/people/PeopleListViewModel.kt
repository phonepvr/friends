package com.phonepvr.friends.ui.people

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.db.relation.PersonWithDetails
import com.phonepvr.friends.data.repository.PeopleRepository
import com.phonepvr.friends.data.repository.TimelineRepository
import com.phonepvr.friends.data.settings.SettingsRepository
import com.phonepvr.friends.domain.cadence.CadenceCalculator
import com.phonepvr.friends.domain.cadence.CadenceStatus
import com.phonepvr.friends.domain.quotes.Quote
import com.phonepvr.friends.domain.quotes.QuoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** A person enriched with their cadence status for the People list grid. */
data class PersonListItem(
    val person: PersonWithDetails,
    val cadence: CadenceStatus,
)

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

@HiltViewModel
class PeopleListViewModel @Inject constructor(
    repository: PeopleRepository,
    timelineRepository: TimelineRepository,
    private val settingsRepository: SettingsRepository,
    private val quoteRepository: QuoteRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _todayQuote = MutableStateFlow<Quote?>(null)
    /** Today's quote-of-the-day, surfaced above the people list. */
    val todayQuote: StateFlow<Quote?> = _todayQuote.asStateFlow()

    init {
        viewModelScope.launch {
            _todayQuote.value = quoteRepository.quoteOfTheDay()
        }
    }

    /** Stable ids of coach-mark tooltips the user has already dismissed. */
    val dismissedTooltips: StateFlow<Set<String>> = settingsRepository.settings
        .map { it.dismissedTooltipIds }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun dismissTooltip(id: String) {
        viewModelScope.launch { settingsRepository.dismissTooltip(id) }
    }

    val people: StateFlow<List<PersonListItem>> =
        combine(
            repository.observeActiveWithDetails(),
            timelineRepository.observeAll(),
            _searchQuery,
        ) { people, timeline, query ->
            val today = LocalDate.now()
            val zone = ZoneId.systemDefault()
            // Bucket the timeline by personId once so each row's cadence lookup
            // is O(1) — the People list re-emits on every timeline change.
            val lastContactByPerson = timeline
                .asSequence()
                .filter { it.countsAsContact }
                .groupingBy { it.personId }
                .fold(0L) { acc, entry -> maxOf(acc, entry.occurredAt) }
            val filtered = if (query.isBlank()) {
                people
            } else {
                people.filter { it.person.displayName.contains(query, ignoreCase = true) }
            }
            filtered.map { detail ->
                val lastMs = lastContactByPerson[detail.person.id]?.takeIf { it > 0L }
                val cadence = CadenceCalculator.status(
                    lastContact = lastMs?.let {
                        Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                    },
                    cadenceTargetDays = detail.person.cadenceTargetDays,
                    today = today,
                )
                PersonListItem(detail, cadence)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * True when the user has not exported a backup in
     * [com.phonepvr.friends.data.settings.AppSettings.backupNudgeIntervalDays]
     * and they haven't dismissed the banner since the current overdue window
     * started.
     */
    val showBackupNudge: StateFlow<Boolean> = settingsRepository.settings
        .map { settings ->
            val anchor = settings.lastSuccessfulBackupAt ?: installTimeMillis()
            val threshold = settings.backupNudgeIntervalDays.toLong() * DAY_MILLIS
            val overdue = System.currentTimeMillis() - anchor > threshold
            val notDismissedSinceAnchor =
                settings.backupNudgeDismissedAt?.let { it < anchor } ?: true
            overdue && notDismissedSinceAnchor
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun dismissBackupNudge() {
        viewModelScope.launch {
            settingsRepository.setBackupNudgeDismissedAt(System.currentTimeMillis())
        }
    }

    private fun installTimeMillis(): Long = runCatching {
        appContext.packageManager
            .getPackageInfo(appContext.packageName, 0)
            .firstInstallTime
    }.getOrDefault(System.currentTimeMillis())
}
