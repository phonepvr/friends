package com.phonepvr.friends.ui.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.contacts.BondContactMatcher
import com.phonepvr.friends.data.contacts.BondContactReconciler
import com.phonepvr.friends.data.contacts.BondIndex
import com.phonepvr.friends.data.contacts.ContactsReader
import com.phonepvr.friends.data.db.dao.PersonDao
import com.phonepvr.friends.data.repository.PeopleRepository
import com.phonepvr.friends.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One address-book contact the user can pick to relink a bond to. */
data class LinkCandidate(
    val contactId: Long,
    val displayName: String,
    val number: String?,
    val photoUri: String?,
    /** True when the contact shares a phone number with the bond being relinked. */
    val suggested: Boolean,
    /**
     * Name of a *different* bond that already tracks this contact. Such contacts
     * are shown but can't be picked, so two bonds never point at one contact.
     */
    val bondedToName: String?,
)

data class LinkContactUiState(
    val loading: Boolean = true,
    val query: String = "",
    val bondName: String = "",
    val suggested: List<LinkCandidate> = emptyList(),
    val others: List<LinkCandidate> = emptyList(),
    val linking: Boolean = false,
    val error: String? = null,
)

/**
 * Lets the user point a bond whose contact was deleted at another contact (or at
 * the replacement they re-created). Only the link changes — the bond's history,
 * cadence, notes, numbers and dates are untouched.
 */
@HiltViewModel
class LinkContactViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactsReader: ContactsReader,
    private val peopleRepository: PeopleRepository,
    private val personDao: PersonDao,
    private val reconciler: BondContactReconciler,
) : ViewModel() {

    private val personId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.PERSON_ID_ARG))

    private val _state = MutableStateFlow(LinkContactUiState())
    val state: StateFlow<LinkContactUiState> = _state.asStateFlow()

    private var all: List<LinkCandidate> = emptyList()

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val bond = peopleRepository.observePersonWithDetails(personId).first()
                val bondNumbers = bond?.phoneNumbers.orEmpty().map { it.rawNumber }
                val others = BondIndex(
                    personDao.getAll().filter { !it.isArchived && it.id != personId },
                )
                val candidates = contactsReader.listContacts().map { c ->
                    LinkCandidate(
                        contactId = c.contactId,
                        displayName = c.displayName,
                        number = c.phoneNumbers.firstOrNull(),
                        photoUri = c.photoUri,
                        suggested = BondContactMatcher.sharesPhoneNumber(bondNumbers, c.phoneNumbers),
                        bondedToName = others.personFor(c)?.displayName,
                    )
                }
                bond?.person?.displayName.orEmpty() to candidates
            }
            all = loaded.second
            publish(query = "", bondName = loaded.first)
        }
    }

    fun onQueryChange(value: String) {
        publish(query = value, bondName = _state.value.bondName)
    }

    /** Relinks the bond to [contactId], then calls [onDone] on success. */
    fun link(contactId: Long, onDone: () -> Unit) {
        if (_state.value.linking) return
        _state.value = _state.value.copy(linking = true, error = null)
        viewModelScope.launch {
            if (reconciler.linkTo(personId, contactId)) {
                onDone()
            } else {
                _state.value = _state.value.copy(
                    linking = false,
                    error = "Couldn't read that contact. Try another one.",
                )
            }
        }
    }

    private fun publish(query: String, bondName: String) {
        val trimmed = query.trim()
        val lower = trimmed.lowercase()
        val digits = trimmed.filter { it.isDigit() }
        val shown = if (trimmed.isEmpty()) {
            all
        } else {
            // The list only carries each contact's first number, so a number search
            // matches that one; names are matched in full.
            all.filter { c ->
                c.displayName.lowercase().contains(lower) ||
                    (digits.isNotEmpty() && c.number?.filter { it.isDigit() }?.contains(digits) == true)
            }
        }
        _state.value = _state.value.copy(
            loading = false,
            query = query,
            bondName = bondName,
            suggested = shown.filter { it.suggested },
            others = shown.filter { !it.suggested },
        )
    }
}
