package com.phonepvr.friends.ui.contacts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.contacts.ContactDetails
import com.phonepvr.friends.data.contacts.ContactForm
import com.phonepvr.friends.data.contacts.ContactTracker
import com.phonepvr.friends.data.contacts.ContactWriter
import com.phonepvr.friends.data.contacts.SystemContactsRepository
import com.phonepvr.friends.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ContactEditMode { NEW, EDIT }

data class ContactEditUiState(
    val mode: ContactEditMode = ContactEditMode.NEW,
    val loading: Boolean = false,
    val form: ContactForm = ContactForm(),
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class ContactEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val systemContactsRepository: SystemContactsRepository,
    private val contactWriter: ContactWriter,
    private val contactTracker: ContactTracker,
) : ViewModel() {

    private val contactId: Long = savedStateHandle.get<Long>(Routes.CONTACT_ID_ARG) ?: 0L
    private val mode: ContactEditMode =
        if (contactId == 0L) ContactEditMode.NEW else ContactEditMode.EDIT

    /** Number pre-filled from the Calls/recents "+" save flow, if any. */
    private val prefillNumber: String? =
        savedStateHandle.get<String>(Routes.DIALPAD_PREFILL_ARG)?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow(
        ContactEditUiState(
            mode = mode,
            loading = mode == ContactEditMode.EDIT,
            // Phones starts with the pre-fill (or one empty slot) for the
            // new-contact case so the user has somewhere to type without
            // tapping "Add phone" first.
            form = if (mode == ContactEditMode.NEW) {
                ContactForm(phones = listOf(prefillNumber ?: ""))
            } else {
                ContactForm()
            },
        ),
    )
    val state: StateFlow<ContactEditUiState> = _state.asStateFlow()

    init {
        if (mode == ContactEditMode.EDIT) {
            viewModelScope.launch {
                val details = systemContactsRepository.details(contactId)
                _state.update {
                    val loaded = details?.toForm() ?: it.form
                    // When saving a number to an existing contact, append it
                    // to the loaded numbers (unless the contact already has
                    // it, compared on digits so formatting differences don't
                    // create a duplicate).
                    val withPrefill = if (prefillNumber != null) {
                        val existingDigits = loaded.phones
                            .map { p -> p.filter(Char::isDigit) }
                        val incomingDigits = prefillNumber.filter(Char::isDigit)
                        if (incomingDigits.isNotBlank() &&
                            existingDigits.none { d -> d == incomingDigits }
                        ) {
                            loaded.copy(
                                phones = loaded.phones.filter { p -> p.isNotBlank() } +
                                    prefillNumber,
                            )
                        } else {
                            loaded
                        }
                    } else {
                        loaded
                    }
                    it.copy(loading = false, form = withPrefill)
                }
            }
        }
    }

    fun onDisplayNameChange(value: String) =
        _state.update { it.copy(form = it.form.copy(displayName = value)) }

    fun onPhoneChange(index: Int, value: String) = _state.update { s ->
        val updated = s.form.phones.toMutableList().apply { this[index] = value }
        s.copy(form = s.form.copy(phones = updated))
    }

    fun onAddPhone() = _state.update { s ->
        s.copy(form = s.form.copy(phones = s.form.phones + ""))
    }

    fun onRemovePhone(index: Int) = _state.update { s ->
        s.copy(form = s.form.copy(phones = s.form.phones.filterIndexed { i, _ -> i != index }))
    }

    fun onEmailChange(index: Int, value: String) = _state.update { s ->
        val updated = s.form.emails.toMutableList().apply { this[index] = value }
        s.copy(form = s.form.copy(emails = updated))
    }

    fun onAddEmail() = _state.update { s ->
        s.copy(form = s.form.copy(emails = s.form.emails + ""))
    }

    fun onRemoveEmail(index: Int) = _state.update { s ->
        s.copy(form = s.form.copy(emails = s.form.emails.filterIndexed { i, _ -> i != index }))
    }

    fun onNotesChange(value: String) =
        _state.update { it.copy(form = it.form.copy(notes = value)) }

    fun onOrganizationChange(value: String) =
        _state.update { it.copy(form = it.form.copy(organization = value)) }

    fun save() {
        if (_state.value.saving) return
        val form = _state.value.form
        if (form.displayName.isBlank()) {
            _state.update { it.copy(error = "Name is required") }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                when (mode) {
                    ContactEditMode.NEW -> {
                        val created = contactWriter.create(form)
                        if (created == null) {
                            _state.update {
                                it.copy(
                                    saving = false,
                                    error = "Couldn't create contact. Check WRITE_CONTACTS permission.",
                                )
                            }
                            return@launch
                        }
                        // Auto-track contacts the user creates from inside
                        // Bondwidth: they're presumably someone the user
                        // wants to maintain a cadence with.
                        if (created.lookupKey.isNotBlank()) {
                            contactTracker.track(created.contactId, created.lookupKey)
                        }
                    }
                    ContactEditMode.EDIT -> {
                        val ok = contactWriter.update(contactId, form)
                        if (!ok) {
                            _state.update {
                                it.copy(
                                    saving = false,
                                    error = "Couldn't save changes.",
                                )
                            }
                            return@launch
                        }
                        val details = systemContactsRepository.details(contactId)
                        details?.lookupKey
                            ?.takeIf { it.isNotBlank() }
                            ?.let { lookupKey ->
                                contactTracker.refreshTrackedFields(contactId, lookupKey)
                            }
                    }
                }
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.message ?: "Save failed") }
            }
        }
    }
}

private fun ContactDetails.toForm(): ContactForm = ContactForm(
    displayName = displayName,
    phones = phoneNumbers.ifEmpty { listOf("") },
    emails = emails,
    notes = notes.orEmpty(),
    organization = organization.orEmpty(),
)
