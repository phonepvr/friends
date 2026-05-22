package com.phonepvr.friends.ui.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.db.entity.EventEntity
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.db.entity.PhoneNumberEntity
import com.phonepvr.friends.data.repository.PeopleRepository
import com.phonepvr.friends.domain.model.EventType
import com.phonepvr.friends.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DateFields(
    val month: String = "",
    val day: String = "",
    val year: String = "",
)

data class PersonFormState(
    val displayName: String = "",
    val phoneNumbers: List<String> = listOf(""),
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
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val personId: Long? = savedStateHandle.get<Long>(Routes.PERSON_ID_ARG)
    val isEditing: Boolean = personId != null

    private val _form = MutableStateFlow(PersonFormState())
    val form: StateFlow<PersonFormState> = _form.asStateFlow()

    private var loadedPerson: PersonEntity? = null

    init {
        if (personId != null) {
            _form.value = _form.value.copy(loading = true)
            viewModelScope.launch {
                val details = repository.observePersonWithDetails(personId).first()
                if (details != null) {
                    loadedPerson = details.person
                    _form.value = PersonFormState(
                        displayName = details.person.displayName,
                        phoneNumbers = details.phoneNumbers
                            .map { it.rawNumber }
                            .ifEmpty { listOf("") },
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
        }
    }

    fun onNameChange(value: String) {
        _form.value = _form.value.copy(displayName = value)
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

    fun onPhoneChange(index: Int, value: String) {
        val updated = _form.value.phoneNumbers.toMutableList()
        if (index in updated.indices) {
            updated[index] = value
            _form.value = _form.value.copy(phoneNumbers = updated)
        }
    }

    fun onAddPhone() {
        _form.value = _form.value.copy(phoneNumbers = _form.value.phoneNumbers + "")
    }

    fun onRemovePhone(index: Int) {
        val updated = _form.value.phoneNumbers.toMutableList()
        if (index in updated.indices) {
            updated.removeAt(index)
            _form.value = _form.value.copy(phoneNumbers = updated.ifEmpty { listOf("") })
        }
    }

    fun updateBirthday(transform: (DateFields) -> DateFields) {
        _form.value = _form.value.copy(birthday = transform(_form.value.birthday))
    }

    fun updateAnniversary(transform: (DateFields) -> DateFields) {
        _form.value = _form.value.copy(anniversary = transform(_form.value.anniversary))
    }

    fun save(onSaved: () -> Unit) {
        val state = _form.value
        if (state.displayName.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val phones = state.phoneNumbers
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { raw ->
                    PhoneNumberEntity(
                        personId = 0,
                        rawNumber = raw,
                        normalizedNumber = raw.filter { ch -> ch.isDigit() },
                    )
                }
            val events = listOfNotNull(
                state.birthday.toEvent(EventType.BIRTHDAY),
                state.anniversary.toEvent(EventType.WEDDING_ANNIVERSARY),
            )
            val cadence = state.cadenceTargetDays.toIntOrNull()?.takeIf { it > 0 }
            val existing = loadedPerson
            if (existing == null) {
                repository.createPerson(
                    person = PersonEntity(
                        uuid = UUID.randomUUID().toString(),
                        displayName = state.displayName.trim(),
                        relationshipTag = state.relationshipTag.trim().ifBlank { null },
                        cadenceTargetDays = cadence,
                        notes = state.notes.trim().ifBlank { null },
                        createdAt = now,
                        updatedAt = now,
                    ),
                    phoneNumbers = phones,
                    events = events,
                )
            } else {
                repository.updatePerson(
                    person = existing.copy(
                        displayName = state.displayName.trim(),
                        relationshipTag = state.relationshipTag.trim().ifBlank { null },
                        cadenceTargetDays = cadence,
                        notes = state.notes.trim().ifBlank { null },
                        updatedAt = now,
                    ),
                    phoneNumbers = phones,
                    events = events,
                )
            }
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

private fun EventEntity.toDateFields(): DateFields = DateFields(
    month = month.toString(),
    day = day.toString(),
    year = year?.toString().orEmpty(),
)

private fun DateFields.toEvent(type: EventType): EventEntity? {
    val parsedMonth = month.toIntOrNull() ?: return null
    val parsedDay = day.toIntOrNull() ?: return null
    if (parsedMonth !in 1..12 || parsedDay !in 1..31) return null
    return EventEntity(
        personId = 0,
        type = type,
        month = parsedMonth,
        day = parsedDay,
        year = year.toIntOrNull(),
    )
}
