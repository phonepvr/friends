package com.phonepvr.friends.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity
import com.phonepvr.friends.data.repository.PeopleRepository
import com.phonepvr.friends.data.repository.TimelineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TimelineItem(
    val entry: TimelineEntryEntity,
    val personName: String,
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    timelineRepository: TimelineRepository,
    peopleRepository: PeopleRepository,
) : ViewModel() {

    private val _filterPersonId = MutableStateFlow<Long?>(null)
    val filterPersonId: StateFlow<Long?> = _filterPersonId.asStateFlow()

    val people: StateFlow<List<PersonEntity>> =
        peopleRepository.observeActiveWithDetails()
            .map { list -> list.map { it.person } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items: StateFlow<List<TimelineItem>> =
        combine(
            timelineRepository.observeAll(),
            peopleRepository.observeActiveWithDetails(),
            _filterPersonId,
        ) { entries, people, filter ->
            val nameById = people.associate { it.person.id to it.person.displayName }
            entries
                .filter { filter == null || it.personId == filter }
                .map { entry -> TimelineItem(entry, nameById[entry.personId] ?: "Unknown") }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(personId: Long?) {
        _filterPersonId.value = personId
    }
}
