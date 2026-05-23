package com.phonepvr.friends.ui.timeline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity
import com.phonepvr.friends.data.repository.TimelineRepository
import com.phonepvr.friends.domain.model.EntrySource
import com.phonepvr.friends.domain.model.InteractionType
import com.phonepvr.friends.ui.common.packDateDigits
import com.phonepvr.friends.ui.common.parseDateDigits
import com.phonepvr.friends.ui.navigation.Routes
import com.phonepvr.friends.ui.people.DateFields
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class LogInteractionForm(
    val type: InteractionType = InteractionType.CALL,
    val date: DateFields = DateFields(),
    val note: String = "",
    val loading: Boolean = false,
)

@HiltViewModel
class LogInteractionViewModel @Inject constructor(
    private val timelineRepository: TimelineRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val entryId: Long? = savedStateHandle.get<Long>(Routes.ENTRY_ID_ARG)
    /** True when editing an existing entry, false when logging a new one. */
    val isEditing: Boolean = entryId != null

    // For a new entry the route carries personId directly. For an edit we
    // discover it by loading the existing TimelineEntry.
    private var personId: Long = savedStateHandle.get<Long>(Routes.PERSON_ID_ARG) ?: 0L
    private var loadedEntry: TimelineEntryEntity? = null

    private val _form = MutableStateFlow(
        LogInteractionForm(date = LocalDate.now().toDateFields(), loading = isEditing),
    )
    val form: StateFlow<LogInteractionForm> = _form.asStateFlow()

    init {
        if (entryId != null) {
            viewModelScope.launch {
                val existing = timelineRepository.getById(entryId)
                if (existing != null) {
                    loadedEntry = existing
                    personId = existing.personId
                    _form.value = LogInteractionForm(
                        type = existing.type,
                        date = existing.occurredAt.toDateFields(),
                        note = existing.note.orEmpty(),
                        loading = false,
                    )
                } else {
                    _form.value = _form.value.copy(loading = false)
                }
            }
        }
    }

    fun onTypeChange(type: InteractionType) {
        _form.value = _form.value.copy(type = type)
    }

    fun onDateChange(transform: (DateFields) -> DateFields) {
        _form.value = _form.value.copy(date = transform(_form.value.date))
    }

    fun onNoteChange(note: String) {
        _form.value = _form.value.copy(note = note)
    }

    fun save(onSaved: () -> Unit) {
        val state = _form.value
        val occurredAt = state.date.toEpochMillisOrNull() ?: return
        val noteValue = state.note.trim().ifBlank { null }
        viewModelScope.launch {
            val existing = loadedEntry
            if (existing != null) {
                timelineRepository.updateEntry(
                    existing.copy(
                        occurredAt = occurredAt,
                        type = state.type,
                        note = noteValue,
                    ),
                )
            } else {
                timelineRepository.addEntry(
                    TimelineEntryEntity(
                        personId = personId,
                        occurredAt = occurredAt,
                        type = state.type,
                        note = noteValue,
                        source = EntrySource.MANUAL,
                        countsAsContact = true,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
            onSaved()
        }
    }
}

private fun LocalDate.toDateFields(): DateFields = DateFields(
    digits = packDateDigits(day = dayOfMonth, month = monthValue, year = year),
)

private fun Long.toDateFields(): DateFields =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate().toDateFields()

private fun DateFields.toEpochMillisOrNull(): Long? {
    val parsed = parseDateDigits(digits) ?: return null
    val date = parsed.toLocalDateOrNull() ?: return null
    return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
