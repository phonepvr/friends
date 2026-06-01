package com.phonepvr.friends.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.contacts.ContactWriter
import com.phonepvr.friends.data.contacts.DuplicateFinder
import com.phonepvr.friends.data.contacts.SystemContactsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Finds and merges duplicate contacts. Detection is read-only and refreshes
 * on demand; merging asks the platform aggregator to keep the cluster's raw
 * contacts together (reversible later via the system contacts app).
 */
@HiltViewModel
class MergeDuplicatesViewModel @Inject constructor(
    private val systemContactsRepository: SystemContactsRepository,
    private val contactWriter: ContactWriter,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val clusters: List<DuplicateFinder.Cluster> = emptyList(),
        val merging: Boolean = false,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            val clusters = systemContactsRepository.findDuplicateClusters()
            _state.update { it.copy(loading = false, clusters = clusters) }
        }
    }

    fun merge(cluster: DuplicateFinder.Cluster) {
        viewModelScope.launch {
            _state.update { it.copy(merging = true) }
            val ok = contactWriter.mergeContacts(cluster.contactIds)
            // Re-scan so the merged cluster drops off the list.
            val clusters = systemContactsRepository.findDuplicateClusters()
            _state.update {
                it.copy(
                    merging = false,
                    clusters = clusters,
                    message = if (ok) {
                        "Merged \"${cluster.displayName}\""
                    } else {
                        "Couldn't merge \"${cluster.displayName}\""
                    },
                )
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}
