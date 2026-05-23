package com.phonepvr.friends.ui.person

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.phonepvr.friends.R
import com.phonepvr.friends.data.db.entity.EventEntity
import com.phonepvr.friends.data.db.entity.PhoneNumberEntity
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity
import com.phonepvr.friends.data.db.relation.PersonWithDetails
import com.phonepvr.friends.data.reachout.ReachOutMethod
import com.phonepvr.friends.data.repository.PersonCallCandidate
import com.phonepvr.friends.ui.components.PersonAvatar
import com.phonepvr.friends.domain.cadence.CadenceState
import com.phonepvr.friends.domain.cadence.CadenceStatus
import com.phonepvr.friends.domain.model.AnnualDate
import com.phonepvr.friends.domain.model.CallType
import com.phonepvr.friends.domain.model.EventType
import com.phonepvr.friends.domain.model.InteractionType
import com.phonepvr.friends.ui.common.DateTextField
import com.phonepvr.friends.ui.common.formatDuration
import com.phonepvr.friends.ui.common.formatEventDay
import com.phonepvr.friends.ui.common.formatTimestamp
import com.phonepvr.friends.ui.common.packDateDigits
import com.phonepvr.friends.ui.common.parseDateDigits
import com.phonepvr.friends.ui.permissions.PermissionRationaleSheet
import com.phonepvr.friends.ui.tooltips.CoachMarkBanner
import com.phonepvr.friends.ui.tooltips.Tooltips
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onLogInteraction: (Long) -> Unit,
    onEditInteraction: (Long) -> Unit,
    viewModel: PersonDetailViewModel = hiltViewModel(),
) {
    val person by viewModel.person.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val cadence by viewModel.cadence.collectAsStateWithLifecycle()
    val lastContactAt by viewModel.lastContactAt.collectAsStateWithLifecycle()
    val summary by viewModel.summary120d.collectAsStateWithLifecycle()
    val availableMethods by viewModel.availableMethods.collectAsStateWithLifecycle()
    val pickerMethod by viewModel.pickerMethod.collectAsStateWithLifecycle()
    val pendingLogPrompt by viewModel.pendingLogPrompt.collectAsStateWithLifecycle()
    val cadenceSheetOpen by viewModel.cadenceSheetOpen.collectAsStateWithLifecycle()
    val callScan by viewModel.callScan.collectAsStateWithLifecycle()
    val callLogRationaleShown by viewModel.callLogRationaleShown.collectAsStateWithLifecycle()
    val dismissedTooltips by viewModel.dismissedTooltips.collectAsStateWithLifecycle()
    val selectedTimelineIds by viewModel.selectedTimelineIds.collectAsStateWithLifecycle()
    val addEventSheet by viewModel.addEventSheet.collectAsStateWithLifecycle()
    val editEventTarget by viewModel.editEventTarget.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val isSelectionMode = selectedTimelineIds.isNotEmpty()
    BackHandler(enabled = isSelectionMode) { viewModel.clearTimelineSelection() }

    var showCallLogRationale by remember { mutableStateOf(false) }
    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.scanCalls()
    }
    val onTriggerScan: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG,
        ) == PackageManager.PERMISSION_GRANTED
        when {
            granted -> viewModel.scanCalls()
            callLogRationaleShown ->
                callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
            else -> showCallLogRationale = true
        }
    }

    if (showCallLogRationale) {
        PermissionRationaleSheet(
            title = "Pull recent calls into the timeline",
            body = "Friends can read the device call log to surface the calls " +
                "you've had with this person — direction, duration and " +
                "timestamp — so you can drop them straight into the timeline. " +
                "Stays on this phone, never goes anywhere.",
            manualFallback = "If you'd rather, skip this and tap " +
                "\"Log interaction\" to add calls by hand whenever you like.",
            grantLabel = "Grant access",
            manualLabel = "I'll log calls myself",
            onGrant = {
                viewModel.markCallLogRationaleShown()
                showCallLogRationale = false
                callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
            },
            onManualFallback = {
                viewModel.markCallLogRationaleShown()
                showCallLogRationale = false
            },
            onDismiss = {
                viewModel.markCallLogRationaleShown()
                showCallLogRationale = false
            },
        )
    }

    LaunchedEffect(pendingLogPrompt) {
        val method = pendingLogPrompt ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Log this ${reachOutMethodLabel(method).lowercase()} as an interaction?",
            actionLabel = "Log",
            duration = SnackbarDuration.Long,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.logPendingReachOut()
            SnackbarResult.Dismissed -> viewModel.dismissLogPrompt()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedTimelineIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearTimelineSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::deleteSelectedTimeline) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(person?.person?.displayName ?: "") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        TextButton(onClick = { onEdit(viewModel.personId) }) { Text("Edit") }
                    },
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onLogInteraction(viewModel.personId) },
            ) {
                Text("Log interaction")
            }
        },
    ) { padding ->
        val current = person
        if (current == null) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Loading…")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { PersonHeader(current) }
                item {
                    val hasSummary = summary.interactionCount > 0 ||
                        summary.totalCallSeconds > 0L
                    if (hasSummary) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CadenceCard(
                                cadence = cadence,
                                lastContactAt = lastContactAt,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            SummaryCard(
                                summary = summary,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    } else {
                        CadenceCard(
                            cadence = cadence,
                            lastContactAt = lastContactAt,
                        )
                    }
                }
                if (cadence.state == CadenceState.NOT_TRACKED &&
                    Tooltips.CADENCE_TAP !in dismissedTooltips
                ) {
                    item {
                        CoachMarkBanner(
                            tipId = Tooltips.CADENCE_TAP,
                            text = "Tap the \"Stay in touch\" card below to set " +
                                "how often you want to check in.",
                            dismissed = dismissedTooltips,
                            onDismiss = viewModel::dismissTooltip,
                        )
                    }
                }
                if (current.phoneNumbers.isNotEmpty() && availableMethods.isNotEmpty()) {
                    if (Tooltips.REACH_OUT !in dismissedTooltips) {
                        item {
                            CoachMarkBanner(
                                tipId = Tooltips.REACH_OUT,
                                text = "Quick reach-out. Friends will offer to log it " +
                                    "after you return.",
                                dismissed = dismissedTooltips,
                                onDismiss = viewModel::dismissTooltip,
                            )
                        }
                    }
                    item {
                        ReachOutRow(
                            methods = availableMethods,
                            onMethodTapped = viewModel::onReachOutMethodTapped,
                        )
                    }
                }
                val hasScan = current.phoneNumbers.isNotEmpty()
                val cadenceTargetDays = current.person.cadenceTargetDays
                if (hasScan && Tooltips.SCAN_CALLS !in dismissedTooltips) {
                    item {
                        CoachMarkBanner(
                            tipId = Tooltips.SCAN_CALLS,
                            text = "Friends can pull recent calls with this person " +
                                "into the timeline. Tap below to scan.",
                            dismissed = dismissedTooltips,
                            onDismiss = viewModel::dismissTooltip,
                        )
                    }
                }
                // Stay-in-touch card always renders so the user has one place
                // to set or change cadence; the call-scan card joins it on
                // the same row when the person has a phone number.
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (hasScan) {
                            CallScanSection(
                                state = callScan,
                                onScan = onTriggerScan,
                                onAddAll = viewModel::addAllScannedCalls,
                                onAddOne = viewModel::addScannedCall,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                        StayInTouchCard(
                            days = cadenceTargetDays,
                            onTap = viewModel::openCadenceSheet,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
                item {
                    InfoSection(
                        person = current,
                        today = today,
                        onMarkWished = viewModel::markAsWished,
                        onAddEvent = viewModel::openAddEventSheet,
                        onEditEvent = viewModel::openEditEventSheet,
                    )
                }
                item { Text("History", style = MaterialTheme.typography.titleMedium) }
                if (timeline.isEmpty()) {
                    if (Tooltips.TIMELINE_LOG !in dismissedTooltips) {
                        item {
                            CoachMarkBanner(
                                tipId = Tooltips.TIMELINE_LOG,
                                text = "Tap \"Log interaction\" below to add a check-in " +
                                    "by hand — call, message, meet-up.",
                                dismissed = dismissedTooltips,
                                onDismiss = viewModel::dismissTooltip,
                            )
                        }
                    }
                    item {
                        Text(
                            text = "Nothing here yet. Even a Tuesday \"thinking of you\" counts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(timeline, key = { it.id }) { entry ->
                        TimelineRow(
                            entry = entry,
                            isSelectionMode = isSelectionMode,
                            isSelected = entry.id in selectedTimelineIds,
                            onToggleSelect = { viewModel.toggleTimelineSelection(entry.id) },
                            onEdit = { onEditInteraction(entry.id) },
                        )
                    }
                }
            }
        }
    }

    val openPicker = pickerMethod
    val numbers = person?.phoneNumbers.orEmpty()
    if (openPicker != null && numbers.size >= 2) {
        NumberPickerSheet(
            method = openPicker,
            numbers = numbers,
            onSelect = { phone -> viewModel.launchReachOut(openPicker, phone.rawNumber) },
            onDismiss = viewModel::dismissPicker,
        )
    }

    if (cadenceSheetOpen) {
        CadenceSheet(
            current = person?.person?.cadenceTargetDays,
            onSelect = viewModel::setCadenceTargetDays,
            onDismiss = viewModel::dismissCadenceSheet,
        )
    }

    addEventSheet?.let { type ->
        AddEventSheet(
            type = type,
            existing = editEventTarget,
            onSave = { day, month, year -> viewModel.saveEvent(type, day, month, year) },
            onDismiss = viewModel::dismissAddEventSheet,
        )
    }
}

@Composable
private fun ReachOutRow(
    methods: Set<ReachOutMethod>,
    onMethodTapped: (ReachOutMethod) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReachOutMethod.entries.forEach { method ->
            if (method in methods) {
                OutlinedButton(
                    onClick = { onMethodTapped(method) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    ReachOutIcon(method)
                }
            }
        }
    }
}

@Composable
private fun ReachOutIcon(method: ReachOutMethod) {
    when (method) {
        ReachOutMethod.CALL -> Icon(
            imageVector = Icons.Filled.Call,
            contentDescription = "Call",
        )
        ReachOutMethod.SMS -> Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "SMS",
        )
        ReachOutMethod.WHATSAPP -> Icon(
            painter = painterResource(R.drawable.ic_brand_whatsapp),
            contentDescription = "WhatsApp",
            // Keep the brand green / blue glyphs untinted.
            tint = Color.Unspecified,
        )
        ReachOutMethod.SIGNAL -> Icon(
            painter = painterResource(R.drawable.ic_brand_signal),
            contentDescription = "Signal",
            tint = Color.Unspecified,
        )
    }
}

@Composable
private fun StayInTouchCard(
    days: Int?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Stay in touch", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (days == null) "Not set" else "Every $days days",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (days == null) "Tap to set" else "Tap to change",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CallScanSection(
    state: CallScanState,
    onScan: () -> Unit,
    onAddAll: () -> Unit,
    onAddOne: (PersonCallCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Call log", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            when (state) {
                CallScanState.Idle -> {
                    Text(
                        text = "Pull recent calls with this person from your call log.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onScan) { Text("Scan call log") }
                }
                CallScanState.Scanning -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Scanning…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is CallScanState.Ready -> {
                    if (state.candidates.isEmpty()) {
                        Text(
                            text = "Nothing new to add — every call is already in the timeline.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onScan) { Text("Scan again") }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${state.candidates.size} not yet logged",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Button(onClick = onAddAll) { Text("Add all") }
                        }
                        Spacer(Modifier.height(8.dp))
                        state.candidates.forEachIndexed { index, candidate ->
                            if (index > 0) HorizontalDivider()
                            CallCandidateRow(
                                candidate = candidate,
                                onAdd = { onAddOne(candidate) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallCandidateRow(
    candidate: PersonCallCandidate,
    onAdd: () -> Unit,
) {
    val call = candidate.deviceCall
    val direction = when (call.type) {
        CallType.INCOMING -> "Incoming"
        CallType.OUTGOING -> "Outgoing"
        CallType.MISSED -> "Missed"
        CallType.REJECTED -> "Rejected"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (call.durationSeconds > 0L) {
                    "$direction · ${formatDuration(call.durationSeconds)}"
                } else {
                    direction
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = formatTimestamp(call.timestampMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onAdd) { Text("Add") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberPickerSheet(
    method: ReachOutMethod,
    numbers: List<PhoneNumberEntity>,
    onSelect: (PhoneNumberEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    // Default focus: a phone explicitly labelled "mobile", else the first one.
    // The bottom sheet doesn't pre-select a row in the UI — it just orders so
    // mobile bubbles to the top.
    val ordered = remember(numbers) {
        val mobile = numbers.firstOrNull { it.label?.equals("mobile", ignoreCase = true) == true }
        if (mobile != null) listOf(mobile) + numbers.filter { it.id != mobile.id } else numbers
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "${reachOutMethodLabel(method)} via",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ordered.forEach { phone ->
                val label = phone.label?.takeIf { it.isNotBlank() }
                ListItem(
                    headlineContent = { Text(phone.rawNumber) },
                    supportingContent = if (label != null) {
                        { Text(label) }
                    } else {
                        null
                    },
                    modifier = Modifier.clickable { onSelect(phone) },
                )
            }
        }
    }
}

private fun reachOutMethodLabel(method: ReachOutMethod): String = when (method) {
    ReachOutMethod.CALL -> "Call"
    ReachOutMethod.SMS -> "SMS"
    ReachOutMethod.WHATSAPP -> "WhatsApp"
    ReachOutMethod.SIGNAL -> "Signal"
}

@Composable
private fun PersonHeader(person: PersonWithDetails) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        PersonAvatar(
            photoRelativePath = person.person.photoRelativePath,
            displayName = person.person.displayName,
            diameter = 96.dp,
        )
    }
}

@Composable
private fun CadenceCard(
    cadence: CadenceStatus,
    lastContactAt: Long?,
    modifier: Modifier = Modifier,
) {
    val text = when (cadence.state) {
        CadenceState.NOT_TRACKED -> "No cadence set"
        CadenceState.NEVER_CONTACTED -> "No interactions yet"
        CadenceState.ON_TRACK ->
            "On track" + (cadence.daysSinceLastContact?.let { " · ${it}d ago" }
                ?: "")
        CadenceState.DUE_SOON ->
            "Due soon" + (cadence.daysUntilDue?.let { " · ${it}d left" } ?: "")
        CadenceState.OVERDUE ->
            "Overdue" + (cadence.daysUntilDue?.let { " by ${-it}d" } ?: "")
    }
    val containerColor = when (cadence.state) {
        CadenceState.OVERDUE -> MaterialTheme.colorScheme.errorContainer
        CadenceState.DUE_SOON -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val lastContactDate = lastContactAt?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
            if (lastContactDate != null) {
                Text(
                    text = "Last: ${formatEventDay(
                        day = lastContactDate.dayOfMonth,
                        month = lastContactDate.monthValue,
                        year = lastContactDate.year,
                    )}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: InteractionSummary, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Last 365 days",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${summary.interactionCount} interactions",
                style = MaterialTheme.typography.titleMedium,
            )
            if (summary.totalCallSeconds > 0L) {
                Text(
                    text = "${formatDuration(summary.totalCallSeconds)} on calls",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val CADENCE_PRESETS = listOf(1, 3, 7, 14, 30, 45, 60, 90, 120, 180, 270, 360)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CadenceSheet(
    current: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Stay in touch every…",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            CADENCE_PRESETS.forEach { days ->
                val checked = current == days
                ListItem(
                    headlineContent = { Text("$days days") },
                    trailingContent = if (checked) {
                        { Text("Current", style = MaterialTheme.typography.labelMedium) }
                    } else {
                        null
                    },
                    modifier = Modifier.clickable { onSelect(days) },
                )
            }
            ListItem(
                headlineContent = { Text("Clear cadence") },
                supportingContent = { Text("Stop tracking how often you stay in touch") },
                modifier = Modifier.clickable { onSelect(null) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEventSheet(
    type: EventType,
    existing: EventEntity?,
    onSave: (day: Int, month: Int, year: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialDigits = existing
        ?.let { packDateDigits(day = it.day, month = it.month, year = it.year) }
        .orEmpty()
    var digits by remember(existing?.id) { mutableStateOf(initialDigits) }
    val parsed = parseDateDigits(digits)
    val isCompleteAttempt = digits.length == 4 || digits.length == 8
    val isError = isCompleteAttempt && parsed == null
    val supporting = when {
        digits.isEmpty() -> "DD/MM/YYYY — leave year blank if unknown"
        digits.length == 4 && parsed != null -> "Year unknown"
        digits.length == 4 -> "Invalid date"
        digits.length == 8 && parsed == null -> "Invalid date"
        digits.length in 5..7 -> "Keep typing or shorten to skip the year"
        else -> null
    }
    val title = if (existing != null) {
        when (type) {
            EventType.BIRTHDAY -> "Edit birthday"
            EventType.WEDDING_ANNIVERSARY -> "Edit anniversary"
            EventType.CUSTOM -> "Edit date"
        }
    } else {
        when (type) {
            EventType.BIRTHDAY -> "Add birthday"
            EventType.WEDDING_ANNIVERSARY -> "Add anniversary"
            EventType.CUSTOM -> "Add date"
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            DateTextField(
                digits = digits,
                onDigitsChange = { digits = it },
                label = "Date",
                allowYearOptional = true,
                isError = isError,
                supportingText = supporting,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("Cancel") }
                Button(
                    onClick = { parsed?.let { onSave(it.day, it.month, it.year) } },
                    enabled = parsed != null,
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun InfoSection(
    person: PersonWithDetails,
    today: LocalDate,
    onMarkWished: (String) -> Unit,
    onAddEvent: (EventType) -> Unit,
    onEditEvent: (EventEntity) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        person.person.relationshipTag
            ?.takeIf { it.isNotBlank() }
            ?.let { InfoRow("Relationship", it) }
        // Birthday + anniversary share one Row so the most-glanced dates stay
        // on the same screen line; everything else is one-per-row.
        val birthday = person.events.firstOrNull { it.type == EventType.BIRTHDAY }
        val anniversary = person.events.firstOrNull { it.type == EventType.WEDDING_ANNIVERSARY }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EventSlot(
                event = birthday,
                type = EventType.BIRTHDAY,
                today = today,
                onMarkWished = onMarkWished,
                onAddEvent = onAddEvent,
                onEditEvent = onEditEvent,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            EventSlot(
                event = anniversary,
                type = EventType.WEDDING_ANNIVERSARY,
                today = today,
                onMarkWished = onMarkWished,
                onAddEvent = onAddEvent,
                onEditEvent = onEditEvent,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        person.events
            .filter { it.type != EventType.BIRTHDAY && it.type != EventType.WEDDING_ANNIVERSARY }
            .forEach { event ->
                EventRow(
                    event = event,
                    today = today,
                    onMarkWished = { onMarkWished(eventLabel(event.type).lowercase()) },
                )
            }
        person.person.notes
            ?.takeIf { it.isNotBlank() }
            ?.let { InfoRow("Notes", it) }
    }
}

@Composable
private fun EventSlot(
    event: EventEntity?,
    type: EventType,
    today: LocalDate,
    onMarkWished: (String) -> Unit,
    onAddEvent: (EventType) -> Unit,
    onEditEvent: (EventEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (event == null) {
        val prompt = when (type) {
            EventType.BIRTHDAY -> "No birthday yet — add one?"
            EventType.WEDDING_ANNIVERSARY -> "No anniversary yet — add one?"
            EventType.CUSTOM -> "Add a date?"
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = modifier.clickable { onAddEvent(type) },
        ) {
            Text(
                text = prompt,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val daysUntil = AnnualDate(event.month, event.day, event.year).daysUntilNextOccurrence(today)
    val showWishButton = daysUntil <= 7L || daysUntil >= 358L
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        // Tap the slot itself to edit — saves a trip through the Edit screen.
        modifier = modifier.clickable { onEditEvent(event) },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = eventLabel(type),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = formatEventDate(event), style = MaterialTheme.typography.bodyLarge)
            if (showWishButton) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { onMarkWished(eventLabel(type).lowercase()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Mark as wished") }
            }
        }
    }
}

@Composable
private fun EventRow(
    event: EventEntity,
    today: LocalDate,
    onMarkWished: () -> Unit,
) {
    val daysUntil = AnnualDate(event.month, event.day, event.year).daysUntilNextOccurrence(today)
    // Show the action when the event is today, in the next 7 days, or in the
    // past 7 days (where daysUntil wraps to a high value near 365).
    val showWishButton = daysUntil <= 7L || daysUntil >= 358L
    if (!showWishButton) {
        InfoRow(eventLabel(event.type), formatEventDate(event))
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eventLabel(event.type),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = formatEventDate(event), style = MaterialTheme.typography.bodyLarge)
        }
        OutlinedButton(onClick = onMarkWished) { Text("Mark as wished") }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TimelineRow(
    entry: TimelineEntryEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onEdit: () -> Unit,
) {
    val accent = entryAccentColor(entry)
    val cardColors = if (isSelected) {
        androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        )
    } else {
        androidx.compose.material3.CardDefaults.elevatedCardColors()
    }
    ElevatedCard(
        colors = cardColors,
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelect() else onEdit() },
                onLongClick = onToggleSelect,
            ),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Column(modifier = Modifier.padding(12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${entryHeadline(entry)} · ${formatTimestamp(entry.occurredAt)}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (!entry.note.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Has note",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val note = entry.note
                if (!note.isNullOrBlank()) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun entryAccentColor(entry: TimelineEntryEntity): Color = when (entry.type) {
    InteractionType.CALL -> when (entry.callDirection) {
        CallType.INCOMING -> MaterialTheme.colorScheme.primary
        CallType.OUTGOING -> MaterialTheme.colorScheme.tertiary
        CallType.MISSED -> MaterialTheme.colorScheme.error
        CallType.REJECTED -> MaterialTheme.colorScheme.outline
        null -> MaterialTheme.colorScheme.primary
    }
    InteractionType.MEET -> MaterialTheme.colorScheme.secondary
    InteractionType.MESSAGE -> MaterialTheme.colorScheme.tertiary
    InteractionType.OTHER -> MaterialTheme.colorScheme.outlineVariant
}

private fun entryHeadline(entry: TimelineEntryEntity): String {
    if (entry.type != InteractionType.CALL) return interactionLabel(entry.type)
    val base = when (entry.callDirection) {
        CallType.INCOMING -> "Incoming call"
        CallType.OUTGOING -> "Outgoing call"
        CallType.MISSED -> "Missed call"
        CallType.REJECTED -> "Rejected call"
        null -> "Call"
    }
    val duration = entry.callDurationSeconds
    return if (duration != null && duration > 0L) {
        "$base · ${formatDuration(duration)}"
    } else {
        base
    }
}

private fun interactionLabel(type: InteractionType): String =
    type.name.lowercase().replaceFirstChar { it.uppercase() }

private fun eventLabel(type: EventType): String = when (type) {
    EventType.BIRTHDAY -> "Birthday"
    EventType.WEDDING_ANNIVERSARY -> "Anniversary"
    EventType.CUSTOM -> "Date"
}

private fun formatEventDate(event: EventEntity): String =
    formatEventDay(day = event.day, month = event.month, year = event.year)
