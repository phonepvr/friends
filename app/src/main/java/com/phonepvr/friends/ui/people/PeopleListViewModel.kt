package com.phonepvr.friends.ui.people

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.db.relation.PersonWithDetails
import com.phonepvr.friends.data.repository.PeopleRepository
import com.phonepvr.friends.data.settings.SettingsRepository
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
import javax.inject.Inject

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

@HiltViewModel
class PeopleListViewModel @Inject constructor(
    repository: PeopleRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val people: StateFlow<List<PersonWithDetails>> =
        combine(repository.observeActiveWithDetails(), _searchQuery) { people, query ->
            if (query.isBlank()) {
                people
            } else {
                people.filter { it.person.displayName.contains(query, ignoreCase = true) }
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
