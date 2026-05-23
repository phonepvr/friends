package com.phonepvr.friends.ui.person

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.db.entity.EventEntity
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity
import com.phonepvr.friends.data.db.relation.PersonWithDetails
import com.phonepvr.friends.data.reachout.ReachOutLauncher
import com.phonepvr.friends.data.reachout.ReachOutMethod
import com.phonepvr.friends.data.repository.CallLogRepository
import com.phonepvr.friends.data.repository.PeopleRepository
import com.phonepvr.friends.data.repository.PersonCallCandidate
import com.phonepvr.friends.data.repository.TimelineRepository
import com.phonepvr.friends.domain.cadence.CadenceCalculator
import com.phonepvr.friends.domain.cadence.CadenceState
import com.phonepvr.friends.domain.cadence.CadenceStatus
import com.phonepvr.friends.domain.model.CallType
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

/** State for the per-profile call-log scan section. */
sealed interface CallScanState {
    /** No scan has been requested this session. */
    data object Idle : CallScanState

    /** Scan in progress — the device call log is being read. */
    data object Scanning : CallScanState

    /** Scan finished and these calls are eligible to be logged. */
    data class Ready(val candidates: List<PersonCallCandidate>) : CallScanState
}

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    private val peopleRepository: PeopleRepository,
    private val timelineRepository: TimelineRepository,
    private val callLogRepository: CallLogRepository,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val personId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.PERSON_ID_ARG))

    val person: StateFlow<PersonWithDetails?> =
        peopleRepository.observePersonWithDetails(personId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    private val _callScan = MutableStateFlow<CallScanState>(CallScanState.Idle)
    /** Per-profile call-log scan state. Idle until the user taps "Scan call log". */
    val callScan: StateFlow<CallScanState> = _callScan.asStateFlow()

    /**
     * Reads the last 120 days of the device call log and keeps only the
     * entries matching this person's phones that haven't already been logged
     * to the timeline. Idempotent and safe to call multiple times.
     */
    fun scanCalls() {
        if (_callScan.value is CallScanState.Scanning) return
        _callScan.value = CallScanState.Scanning
        viewModelScope.launch {
            val candidates = callLogRepository.scanForPerson(personId)
            _callScan.value = CallScanState.Ready(candidates)
        }
    }

    /** Insert every scanned candidate as a timeline entry and clear the list. */
    fun addAllScannedCalls() {
        val current = _callScan.value as? CallScanState.Ready ?: return
        if (current.candidates.isEmpty()) return
        viewModelScope.launch {
            current.candidates.forEach { insertCallCandidate(it) }
            _callScan.value = CallScanState.Ready(emptyList())
        }
    }

    /** Insert a single scanned candidate and drop it from the list. */
    fun addScannedCall(candidate: PersonCallCandidate) {
        val current = _callScan.value as? CallScanState.Ready ?: return
        viewModelScope.launch {
            insertCallCandidate(candidate)
            _callScan.value = CallScanState.Ready(
                current.candidates.filter { it.callDedupKey != candidate.callDedupKey },
            )
        }
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

    fun openAddEventSheet(type: EventType) { _addEventSheet.value = type }
    fun dismissAddEventSheet() { _addEventSheet.value = null }

    /** Inserts a new EventEntity for [type] and closes the sheet. */
    fun addEvent(type: EventType, day: Int, month: Int, year: Int?) {
        _addEventSheet.value = null
        viewModelScope.launch {
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

    private suspend fun insertCallCandidate(candidate: PersonCallCandidate) {
        val call = candidate.deviceCall
        timelineRepository.addEntry(
            TimelineEntryEntity(
                personId = personId,
                occurredAt = call.timestampMillis,
                type = InteractionType.CALL,
                note = null,
                source = EntrySource.CALL_LOG,
                // Auto-imported calls only count toward cadence when both
                // direction (incoming / outgoing) AND duration (> 0s) indicate
                // a real conversation happened. Missed / rejected calls and
                // ring-and-hang-up entries are excluded. Manual calls go
                // through a different path with no such filter — if the user
                // logs a call by hand we trust them.
                countsAsContact = (call.type == CallType.INCOMING ||
                    call.type == CallType.OUTGOING) && call.durationSeconds > 0L,
                callDedupKey = candidate.callDedupKey,
                callDirection = call.type,
                callDurationSeconds = call.durationSeconds,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }
}
