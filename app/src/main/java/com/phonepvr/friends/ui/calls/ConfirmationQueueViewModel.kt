package com.phonepvr.friends.ui.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.db.entity.PendingConfirmationEntity
import com.phonepvr.friends.data.repository.CallLogRepository
import com.phonepvr.friends.data.repository.PeopleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PersonRef(val id: Long, val name: String)

data class ConfirmationItem(
    val confirmation: PendingConfirmationEntity,
    val candidates: List<PersonRef>,
)

@HiltViewModel
class ConfirmationQueueViewModel @Inject constructor(
    private val callLogRepository: CallLogRepository,
    peopleRepository: PeopleRepository,
) : ViewModel() {

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    val items: StateFlow<List<ConfirmationItem>> =
        combine(
            callLogRepository.observePending(),
            peopleRepository.observeActiveWithDetails(),
        ) { pending, people ->
            val nameById = people.associate { it.person.id to it.person.displayName }
            pending.map { confirmation ->
                val personId = confirmation.personId
                val candidateIds = confirmation.candidatePersonIds
                val ids = when {
                    personId != null -> listOf(personId)
                    candidateIds != null ->
                        candidateIds.split(",").mapNotNull { it.toLongOrNull() }
                    else -> emptyList()
                }
                ConfirmationItem(
                    confirmation = confirmation,
                    candidates = ids.map { PersonRef(it, nameById[it] ?: "Unknown") },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun scan() {
        if (_scanning.value) return
        _scanning.value = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) { callLogRepository.scanRecentCalls() }
            _scanning.value = false
        }
    }

    fun confirm(item: ConfirmationItem, personId: Long) {
        viewModelScope.launch { callLogRepository.confirm(item.confirmation, personId) }
    }

    fun dismiss(item: ConfirmationItem) {
        viewModelScope.launch { callLogRepository.dismiss(item.confirmation) }
    }
}
