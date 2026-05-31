package com.phonepvr.friends.ui.person

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.contacts.ContactDetails
import com.phonepvr.friends.data.contacts.ContactWriter
import com.phonepvr.friends.data.contacts.SystemContactsRepository
import com.phonepvr.friends.data.db.entity.EventEntity
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity
import com.phonepvr.friends.data.db.relation.PersonWithDetails
import com.phonepvr.friends.data.reachout.ReachOutLauncher
import com.phonepvr.friends.data.reachout.ReachOutMethod
import com.phonepvr.friends.data.repository.PeopleRepository
import com.phonepvr.friends.data.repository.TimelineRepository
import com.phonepvr.friends.data.settings.SettingsRepository
import com.phonepvr.friends.domain.cadence.CadenceCalculator
import com.phonepvr.friends.domain.cadence.CadenceState
import com.phonepvr.friends.domain.cadence.CadenceStatus
import com.phonepvr.friends.domain.model.EntrySource
import com.phonepvr.friends.domain.model.EventType
import com.phonepvr.friends.domain.model.InteractionType
import com.phonepvr.friends.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Interactions logged + total call time over the recent window the screen shows. */
data class InteractionSummary(
    val interactionCount: Int,
    val totalCallSeconds: Long,
)

private const val SUMMARY_WINDOW_DAYS = 365L
private const val SUMMARY_WINDOW_MILLIS = SUMMARY_WINDOW_DAYS * 24L * 60L * 60L * 1000L


@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    private val peopleRepository: PeopleRepository,
    private val timelineRepository: TimelineRepository,
    private val settingsRepository: SettingsRepository,
    private val systemContactsRepository: SystemContactsRepository,
    private val contactWriter: ContactWriter,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val personId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.PERSON_ID_ARG))

    val person: StateFlow<PersonWithDetails?> =
        peopleRepository.observePersonWithDetails(personId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The bonded person's system contact resolved by lookupKey, paired with
     * its current numeric id (the id can change as the platform merges /
     * splits aggregates). Null until loaded, or when the person isn't linked
     * to a system contact (rare — most bonded people come through the
     * import / save-number flows that always carry a lookupKey).
     */
    private val _contactSnapshot = MutableStateFlow<Pair<Long, ContactDetails>?>(null)
    val contactDetails: StateFlow<ContactDetails?> = _contactSnapshot
        .map { it?.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The contact id that backs [contactDetails], for navigating to its editor. */
    val contactId: StateFlow<Long?> = _contactSnapshot
        .map { it?.first }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            // Re-load whenever the lookupKey changes — covers the
            // rename-on-bond flow where the key fills in after the person
            // is first inserted.
            person
                .map { it?.person?.contactLookupKey?.takeIf { k -> k.isNotBlank() } }
                .collect { key ->
                    _contactSnapshot.value =
                        if (key == null) null else systemContactsRepository.detailsByLookupKey(key)
                }
        }
    }

    /** Re-reads the contact after a write so the UI picks up the change. */
    private fun refreshContact() {
        viewModelScope.launch {
            val key = person.value?.person?.contactLookupKey?.takeIf { it.isNotBlank() }
                ?: return@launch
            _contactSnapshot.value = systemContactsRepository.detailsByLookupKey(key)
        }
    }

    /** Marks [dataId] as the contact's default number and reloads details. */
    fun setPrimaryContactNumber(dataId: Long) {
        viewModelScope.launch {
            contactWriter.setPrimaryNumber(dataId)
            refreshContact()
        }
    }

    /** Per-contact ringtone — null reverts to system default. */
    fun setCustomRingtone(uri: Uri?) {
        viewModelScope.launch {
            val id = _contactSnapshot.value?.first ?: return@launch
            contactWriter.setCustomRingtone(id, uri)
            refreshContact()
        }
    }

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

    /** Epoch millis of the most recent counts-as-contact interaction, null if none. */
    val lastContactAt: StateFlow<Long?> = timeline
        .map { entries ->
            entries.filter { it.countsAsContact }.maxOfOrNull { it.occurredAt }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Interactions logged in the last 120 days and total call time over the same window. */
    val summary120d: StateFlow<InteractionSummary> = timeline
        .map { entries ->
            val cutoff = System.currentTimeMillis() - SUMMARY_WINDOW_MILLIS
            val inWindow = entries.filter { it.occurredAt >= cutoff }
            val callSeconds = inWindow
                .filter { it.type == InteractionType.CALL }
                .sumOf { it.callDurationSeconds ?: 0L }
            InteractionSummary(inWindow.size, callSeconds)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            InteractionSummary(0, 0L),
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

    /** Which reach-out methods can target an installed app on this device. */
    val availableMethods: StateFlow<Set<ReachOutMethod>> =
        MutableStateFlow(
            ReachOutMethod.entries
                .filter { ReachOutLauncher.isAvailable(appContext, it) }
                .toSet(),
        ).asStateFlow()

    private val _pickerMethod = MutableStateFlow<ReachOutMethod?>(null)
    /** Non-null when the multi-number picker should be shown for that method. */
    val pickerMethod: StateFlow<ReachOutMethod?> = _pickerMethod.asStateFlow()

    private val _pendingLogPrompt = MutableStateFlow<ReachOutMethod?>(null)
    /**
     * Non-null when the user just launched a reach-out and we want to prompt
     * "Log this interaction?" on their return.
     */
    val pendingLogPrompt: StateFlow<ReachOutMethod?> = _pendingLogPrompt.asStateFlow()

    /**
     * Called when the user taps one of the reach-out buttons. If the person
     * has more than one phone, opens the number picker; otherwise launches
     * the method against the only number.
     */
    fun onReachOutMethodTapped(method: ReachOutMethod) {
        val phones = person.value?.phoneNumbers.orEmpty()
        when {
            phones.isEmpty() -> Unit
            phones.size == 1 -> launchReachOut(method, phones.first().rawNumber)
            else -> _pickerMethod.value = method
        }
    }

    /** Dismiss the number picker without launching anything. */
    fun dismissPicker() {
        _pickerMethod.value = null
    }

    /**
     * Launch [method] against [rawNumber]. Picker is closed and the
     * "Log this interaction?" prompt is armed for when the user returns.
     */
    fun launchReachOut(method: ReachOutMethod, rawNumber: String) {
        _pickerMethod.value = null
        val launched = ReachOutLauncher.launch(appContext, method, rawNumber)
        if (launched) {
            _pendingLogPrompt.value = method
        }
    }

    fun logPendingReachOut() {
        val method = _pendingLogPrompt.value ?: return
        _pendingLogPrompt.value = null
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            timelineRepository.addEntry(
                TimelineEntryEntity(
                    personId = personId,
                    occurredAt = now,
                    type = method.interactionType,
                    note = null,
                    source = EntrySource.MANUAL,
                    countsAsContact = true,
                    callDedupKey = null,
                    createdAt = now,
                ),
            )
        }
    }

    fun dismissLogPrompt() {
        _pendingLogPrompt.value = null
    }

    private val _cadenceSheetOpen = MutableStateFlow(false)
    /** When true, the Person Detail screen renders the cadence-set bottom sheet. */
    val cadenceSheetOpen: StateFlow<Boolean> = _cadenceSheetOpen.asStateFlow()

    fun openCadenceSheet() { _cadenceSheetOpen.value = true }
    fun dismissCadenceSheet() { _cadenceSheetOpen.value = false }

    /**
     * Writes [days] to the person's cadenceTargetDays (null clears it) and
     * closes the sheet. Touches only the cadence column, not phones or events.
     */
    fun setCadenceTargetDays(days: Int?) {
        _cadenceSheetOpen.value = false
        viewModelScope.launch {
            peopleRepository.setCadenceTargetDays(personId, days)
        }
    }

    /** Stable ids of coach-mark tooltips the user has already dismissed. */
    val dismissedTooltips: StateFlow<Set<String>> = settingsRepository.settings
        .map { it.dismissedTooltipIds }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun dismissTooltip(id: String) {
        viewModelScope.launch { settingsRepository.dismissTooltip(id) }
    }

    private val _selectedTimelineIds = MutableStateFlow<Set<Long>>(emptySet())
    /** Ids of timeline entries the user has marked for bulk delete. */
    val selectedTimelineIds: StateFlow<Set<Long>> = _selectedTimelineIds.asStateFlow()

    /** Toggle a row in/out of the bulk-delete selection. Long-press enters the
     *  selection mode by adding the first id; subsequent taps in the same mode
     *  toggle additions and removals. */
    fun toggleTimelineSelection(id: Long) {
        val current = _selectedTimelineIds.value
        _selectedTimelineIds.value = if (id in current) current - id else current + id
    }

    fun clearTimelineSelection() {
        _selectedTimelineIds.value = emptySet()
    }

    fun deleteSelectedTimeline() {
        val ids = _selectedTimelineIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            timelineRepository.deleteByIds(ids)
            _selectedTimelineIds.value = emptySet()
        }
    }

    private val _addEventSheet = MutableStateFlow<EventType?>(null)
    /** Non-null when the inline "Add birthday / anniversary" sheet is showing. */
    val addEventSheet: StateFlow<EventType?> = _addEventSheet.asStateFlow()

    fun openAddEventSheet(type: EventType) {
        _editEventTarget.value = null
        _addEventSheet.value = type
    }
    fun dismissAddEventSheet() {
        _addEventSheet.value = null
        _editEventTarget.value = null
    }

    private val _editEventTarget = MutableStateFlow<EventEntity?>(null)
    /** Non-null when the sheet is editing an existing event. */
    val editEventTarget: StateFlow<EventEntity?> = _editEventTarget.asStateFlow()

    fun openEditEventSheet(event: EventEntity) {
        _editEventTarget.value = event
        _addEventSheet.value = event.type
    }

    /**
     * Inserts a new EventEntity for [type] (or updates the one in
     * [editEventTarget] when set) and closes the sheet.
     */
    fun saveEvent(type: EventType, day: Int, month: Int, year: Int?) {
        val target = _editEventTarget.value
        _addEventSheet.value = null
        _editEventTarget.value = null
        viewModelScope.launch {
            if (target != null) {
                peopleRepository.updateEvent(
                    target.copy(type = type, month = month, day = day, year = year),
                )
            } else {
                peopleRepository.addEvent(
                    EventEntity(
                        personId = personId,
                        type = type,
                        month = month,
                        day = day,
                        year = year,
                    ),
                )
            }
        }
    }

}
