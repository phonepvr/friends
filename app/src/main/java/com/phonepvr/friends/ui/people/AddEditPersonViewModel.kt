package com.phonepvr.friends.ui.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.db.entity.EventEntity
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.db.entity.PhoneNumberEntity
import com.phonepvr.friends.data.repository.PeopleRepository
import com.phonepvr.friends.data.settings.SettingsRepository
import com.phonepvr.friends.domain.model.EventType
import com.phonepvr.friends.ui.common.packDateDigits
import com.phonepvr.friends.ui.common.parseDateDigits
import com.phonepvr.friends.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Raw digit string (0–8 chars) backing the masked DD/MM/YYYY field. A length
 * of 4 means "year omitted", 8 means full date; anything else is in-progress
 * input.
 */
data class DateFields(val digits: String = "")

data class PersonFormState(
    /** Read-only here — a bond's name comes from its linked contact. */
    val displayName: String = "",
    val relationshipTag: String = "",
    val cadenceTargetDays: String = "",
    val notes: String = "",
    val birthday: DateFields = DateFields(),
    val anniversary: DateFields = DateFields(),
    val loading: Boolean = false,
)

@HiltViewModel
class AddEditPersonViewModel @Inject constructor(
    private val repository: PeopleRepository,
    settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val personId: Long? = savedStateHandle.get<Long>(Routes.PERSON_ID_ARG)
    val isEditing: Boolean = personId != null

    private val _form = MutableStateFlow(PersonFormState())
    val form: StateFlow<PersonFormState> = _form.asStateFlow()

    private var loadedPerson: PersonEntity? = null

    // Phones are owned by the linked contact, not edited here; keep them as
    // loaded so save() preserves them (they back call-matching).
    private var loadedPhones: List<PhoneNumberEntity> = emptyList()

    init {
        if (personId != null) {
            _form.value = _form.value.copy(loading = true)
            viewModelScope.launch {
                val details = repository.observePersonWithDetails(personId).first()
                if (details != null) {
                    loadedPerson = details.person
                    loadedPhones = details.phoneNumbers
                    _form.value = PersonFormState(
                        displayName = details.person.displayName,
                        relationshipTag = details.person.relationshipTag.orEmpty(),
                        cadenceTargetDays = details.person.cadenceTargetDays?.toString().orEmpty(),
                        notes = details.person.notes.orEmpty(),
                        birthday = details.events
                            .firstOrNull { it.type == EventType.BIRTHDAY }
                            ?.toDateFields() ?: DateFields(),
                        anniversary = details.events
                            .firstOrNull { it.type == EventType.WEDDING_ANNIVERSARY }
                            ?.toDateFields() ?: DateFields(),
                        loading = false,
                    )
                } else {
                    _form.value = _form.value.copy(loading = false)
                }
            }
        } else {
            // Pre-fill the cadence for a new person from the configured default.
            viewModelScope.launch {
                val defaultCadence = settingsRepository.settings.first().defaultCadenceDays
                if (_form.value.cadenceTargetDays.isEmpty()) {
                    _form.value = _form.value.copy(
                        cadenceTargetDays = defaultCadence.toString(),
                    )
                }
            }
        }
    }

    fun onTagChange(value: String) {
        _form.value = _form.value.copy(relationshipTag = value)
    }

    fun onCadenceChange(value: String) {
        if (value.all { it.isDigit() }) {
            _form.value = _form.value.copy(cadenceTargetDays = value)
        }
    }

    fun onNotesChange(value: String) {
        _form.value = _form.value.copy(notes = value)
    }

    fun updateBirthday(transform: (DateFields) -> DateFields) {
        _form.value = _form.value.copy(birthday = transform(_form.value.birthday))
    }

    fun updateAnniversary(transform: (DateFields) -> DateFields) {
        _form.value = _form.value.copy(anniversary = transform(_form.value.anniversary))
    }

    fun save(onSaved: () -> Unit) {
        // Edit-only: a bond always already exists (created via contact import).
        val existing = loadedPerson ?: run { onSaved(); return }
        val state = _form.value
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val events = listOfNotNull(
                state.birthday.toEvent(EventType.BIRTHDAY),
                state.anniversary.toEvent(EventType.WEDDING_ANNIVERSARY),
            )
            val cadence = state.cadenceTargetDays.toIntOrNull()?.takeIf { it > 0 }
            repository.updatePerson(
                person = existing.copy(
                    relationshipTag = state.relationshipTag.trim().ifBlank { null },
                    cadenceTargetDays = cadence,
                    notes = state.notes.trim().ifBlank { null },
                    updatedAt = now,
                ),
                // Phones are owned by the contact — pass the loaded set back
                // unchanged so updatePerson's replace doesn't wipe them.
                phoneNumbers = loadedPhones,
                events = events,
            )
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val existing = loadedPerson ?: return
        viewModelScope.launch {
            repository.deletePerson(existing)
            onDeleted()
        }
    }
}

private fun EventEntity.toDateFields(): DateFields =
    DateFields(digits = packDateDigits(day = day, month = month, year = year))

private fun DateFields.toEvent(type: EventType): EventEntity? {
    val parsed = parseDateDigits(digits) ?: return null
    return EventEntity(
        personId = 0,
        type = type,
        month = parsed.month,
        day = parsed.day,
        year = parsed.year,
    )
}
