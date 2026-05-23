package com.phonepvr.friends.ui.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity
import com.phonepvr.friends.data.db.relation.PersonWithDetails
import com.phonepvr.friends.data.repository.PeopleRepository
import com.phonepvr.friends.data.repository.TimelineRepository
import com.phonepvr.friends.domain.cadence.CadenceCalculator
import com.phonepvr.friends.domain.cadence.CadenceState
import com.phonepvr.friends.domain.cadence.CadenceStatus
import com.phonepvr.friends.domain.model.EntrySource
import com.phonepvr.friends.domain.model.InteractionType
import com.phonepvr.friends.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    peopleRepository: PeopleRepository,
    private val timelineRepository: TimelineRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val personId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.PERSON_ID_ARG))

    val person: StateFlow<PersonWithDetails?> =
        peopleRepository.observePersonWithDetails(personId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val timeline: StateFlow<List<TimelineEntryEntity>> =
        timelineRepository.observeForPerson(personId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cadence: StateFlow<CadenceStatus> =
        combine(person, timeline) { personWithDetails, entries ->
            val lastContactMillis = entries
                .filter { it.countsAsContact }
                .maxOfOrNull { it.occurredAt }
            CadenceCalculator.status(
                lastContact = lastContactMillis?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                },
                cadenceTargetDays = personWithDetails?.person?.cadenceTargetDays,
                today = LocalDate.now(),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CadenceStatus(CadenceState.NOT_TRACKED, null, null),
        )

    /** Logs a `Wished – <label>` interaction so the cadence timer resets. */
    fun markAsWished(eventLabel: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            timelineRepository.addEntry(
                TimelineEntryEntity(
                    personId = personId,
                    occurredAt = now,
                    type = InteractionType.OTHER,
                    note = "Wished – $eventLabel",
                    source = EntrySource.MANUAL,
                    countsAsContact = true,
                    callDedupKey = null,
                    createdAt = now,
                ),
            )
        }
    }
}
