package com.phonepvr.friends.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.db.relation.PersonWithDetails
import com.phonepvr.friends.data.repository.PeopleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PeopleListViewModel @Inject constructor(
    repository: PeopleRepository,
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

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }
}
