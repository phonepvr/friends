package com.phonepvr.friends.ui.dialer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.data.dialer.CallPlacer
import com.phonepvr.friends.domain.model.CallType
import com.phonepvr.friends.ui.components.PersonAvatar
import com.phonepvr.friends.ui.permissions.PermissionRationaleSheet
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    onOpenContact: (Long) -> Unit,
    bottomBar: @Composable () -> Unit,
    viewModel: DialerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var hasCallLog by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasContacts by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasCallPhone by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var showRationale by remember { mutableStateOf(false) }
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        hasCallLog = grants[Manifest.permission.READ_CALL_LOG] == true || hasCallLog
        hasContacts = grants[Manifest.permission.READ_CONTACTS] == true || hasContacts
        hasCallPhone = grants[Manifest.permission.CALL_PHONE] == true || hasCallPhone
        viewModel.onPermissionState(hasCallLog, hasContacts)
        pendingCallNumber?.let { number ->
            pendingCallNumber = null
            if (hasCallPhone) tryPlace(viewModel, number, snackbarState, scope)
        }
    }

    LaunchedEffect(hasCallLog, hasContacts) {
        viewModel.onPermissionState(hasCallLog, hasContacts)
    }

    LaunchedEffect(state.placeError) {
        state.placeError?.let { msg ->
            snackbarState.showSnackbar(msg)
            viewModel.dismissPlaceError()
        }
    }

    if (showRationale) {
        PermissionRationaleSheet(
            title = "A few phone permissions",
            body = "Bondwidth needs: read access to your call log (to show " +
                "recents), read access to your contacts (to label the " +
                "numbers), and permission to place calls when you tap one. " +
                "All on-device.",
            manualFallback = "If you decline, this tab still opens but " +
                "stays empty until you grant access in system settings.",
            grantLabel = "Grant access",
            manualLabel = "Not now",
            onGrant = {
                showRationale = false
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.CALL_PHONE,
                    ),
                )
            },
            onManualFallback = { showRationale = false },
            onDismiss = { showRationale = false },
        )
    }

    val placeCall: (String) -> Unit = { number ->
        if (hasCallPhone) {
            tryPlace(viewModel, number, snackbarState, scope)
        } else {
            pendingCallNumber = number
            showRationale = true
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Phone") }) },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarState) { Snackbar(it) } },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            SegmentRow(
                segment = state.segment,
                onChange = viewModel::onSegmentChange,
            )
            when (state.segment) {
                DialerSegment.RECENTS -> RecentsSegment(
                    state = state,
                    hasPermissions = hasCallLog,
                    onRequest = { showRationale = true },
                    onCallNumber = placeCall,
                    onOpenContact = onOpenContact,
                )
                DialerSegment.DIALPAD -> DialpadSegment(
                    state = state,
                    onDigit = viewModel::onDigit,
                    onBackspace = viewModel::onBackspace,
                    onCall = {
                        val target = state.matches.firstOrNull()?.matchedNumber
                            ?.takeIf { it.isNotBlank() }
                            ?: state.dialpadInput
                        placeCall(target)
                    },
                    onCallMatch = { match -> placeCall(match.matchedNumber.ifBlank { state.dialpadInput }) },
                    onOpenContact = onOpenContact,
                )
            }
        }
    }
}

private fun tryPlace(
    viewModel: DialerViewModel,
    number: String,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val result = viewModel.place(number)
    if (result == CallPlacer.PlaceResult.NO_PERMISSION) {
        scope.launch { snackbarHostState.showSnackbar("Grant Call permission to dial.") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentRow(segment: DialerSegment, onChange: (DialerSegment) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        DialerSegment.entries.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = segment == entry,
                onClick = { onChange(entry) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = DialerSegment.entries.size,
                ),
            ) { Text(entry.label()) }
        }
    }
}

private fun DialerSegment.label(): String = when (this) {
    DialerSegment.RECENTS -> "Recents"
    DialerSegment.DIALPAD -> "Dialpad"
}

@Composable
private fun RecentsSegment(
    state: DialerUiState,
    hasPermissions: Boolean,
    onRequest: () -> Unit,
    onCallNumber: (String) -> Unit,
    onOpenContact: (Long) -> Unit,
) {
    if (!hasPermissions) {
        EmptyArea(
            message = "Bondwidth needs the call-log permission to show your recents.",
            actionLabel = "Grant access",
            onAction = onRequest,
        )
        return
    }
    if (!state.recentsLoaded) {
        EmptyArea(message = "Loading…")
        return
    }
    if (state.recents.isEmpty()) {
        EmptyArea(message = "No calls in the last 30 days.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(
            items = state.recents,
            key = { "${it.timestampMillis}-${it.number}" },
        ) { entry ->
            RecentRow(
                entry = entry,
                onCall = { onCallNumber(entry.number) },
                onOpenContact = onOpenContact,
            )
        }
    }
}

@Composable
private fun RecentRow(
    entry: RecentEntry,
    onCall: () -> Unit,
    onOpenContact: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCall)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(
            photoRelativePath = entry.photoRelativePath,
            displayName = entry.displayName ?: entry.number,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = entry.type.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = entry.type.tint(),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = entry.displayName ?: entry.number,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (entry.type == CallType.MISSED) FontWeight.SemiBold
                    else FontWeight.Normal,
                )
                if (entry.isTracked) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Text(
                text = formatTimestamp(entry.timestampMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onCall) {
            Icon(Icons.Filled.Call, contentDescription = "Call back")
        }
    }
}

private fun CallType.icon(): ImageVector = when (this) {
    CallType.INCOMING -> Icons.Filled.CallReceived
    CallType.OUTGOING -> Icons.Filled.CallMade
    CallType.MISSED, CallType.REJECTED -> Icons.Filled.CallMissed
}

@Composable
private fun CallType.tint(): Color = when (this) {
    CallType.INCOMING, CallType.OUTGOING ->
        MaterialTheme.colorScheme.onSurfaceVariant
    CallType.MISSED, CallType.REJECTED ->
        MaterialTheme.colorScheme.error
}

private fun formatTimestamp(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

@Composable
private fun DialpadSegment(
    state: DialerUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onCall: () -> Unit,
    onCallMatch: (DialpadMatch) -> Unit,
    onOpenContact: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        InputBar(input = state.dialpadInput, onBackspace = onBackspace)
        Box(modifier = Modifier.weight(1f)) {
            if (state.matches.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.matches, key = { it.contactId }) { match ->
                        MatchRow(
                            match = match,
                            onCall = { onCallMatch(match) },
                            onOpen = { onOpenContact(match.contactId) },
                        )
                    }
                }
            } else if (state.dialpadInput.isNotEmpty()) {
                EmptyArea(message = "No matching contacts. Hit Call to dial directly.")
            }
        }
        DialpadGrid(onDigit = onDigit)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            FloatingActionButton(onClick = onCall) {
                Icon(Icons.Filled.Call, contentDescription = "Call")
            }
        }
    }
}

@Composable
private fun InputBar(input: String, onBackspace: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = input.ifEmpty { " " },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.End,
        )
        if (input.isNotEmpty()) {
            IconButton(onClick = onBackspace) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                )
            }
        }
    }
}

@Composable
private fun MatchRow(
    match: DialpadMatch,
    onCall: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(
            photoRelativePath = match.photoRelativePath,
            displayName = match.displayName,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(match.displayName, style = MaterialTheme.typography.bodyLarge)
            if (match.matchedNumber.isNotBlank()) {
                Text(
                    text = match.matchedNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onCall) {
            Icon(Icons.Filled.Call, contentDescription = "Call")
        }
    }
}

@Composable
private fun DialpadGrid(onDigit: (Char) -> Unit) {
    val rows = listOf(
        listOf(KeyDef('1', ""), KeyDef('2', "ABC"), KeyDef('3', "DEF")),
        listOf(KeyDef('4', "GHI"), KeyDef('5', "JKL"), KeyDef('6', "MNO")),
        listOf(KeyDef('7', "PQRS"), KeyDef('8', "TUV"), KeyDef('9', "WXYZ")),
        listOf(KeyDef('*', ""), KeyDef('0', "+"), KeyDef('#', "")),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (key in row) {
                    DialpadKey(
                        key = key,
                        modifier = Modifier.weight(1f),
                        onClick = { onDigit(key.digit) },
                    )
                }
            }
        }
    }
}

private data class KeyDef(val digit: Char, val letters: String)

@Composable
private fun DialpadKey(
    key: KeyDef,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = key.digit.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            if (key.letters.isNotEmpty()) {
                Text(
                    text = key.letters,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyArea(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
