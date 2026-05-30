package com.phonepvr.friends.ui.dialer

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.data.dialer.CallPlacer
import com.phonepvr.friends.domain.model.CallType
import com.phonepvr.friends.ui.components.PersonAvatar
import com.phonepvr.friends.ui.permissions.PermissionRationaleSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    onOpenContact: (Long) -> Unit,
    onOpenDialpad: () -> Unit,
    onOpenHistory: (number: String) -> Unit,
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

    var sheetEntry by remember { mutableStateOf<RecentEntry?>(null) }

    sheetEntry?.let { entry ->
        RecentActionsSheet(
            entry = entry,
            onDismiss = { sheetEntry = null },
            onCall = {
                sheetEntry = null
                placeCall(entry.number)
            },
            onMessage = {
                sheetEntry = null
                openSms(context, entry.number)
            },
            onCopy = {
                sheetEntry = null
                copyToClipboard(context, entry.number)
                scope.launch { snackbarState.showSnackbar("Number copied") }
            },
            onOpenContact = entry.contactId?.let { id ->
                { sheetEntry = null; onOpenContact(id) }
            },
            onAddContact = if (entry.contactId == null) {
                { sheetEntry = null; openAddContact(context, entry.number) }
            } else {
                null
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Calls") }) },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarState) { Snackbar(it) } },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenDialpad) {
                Icon(Icons.Filled.Dialpad, contentDescription = "Open dialpad")
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            RecentsContent(
                state = state,
                hasPermission = hasCallLog,
                onRequest = { showRationale = true },
                onCallNumber = placeCall,
                onOpenHistory = onOpenHistory,
                onAddToContacts = { number -> openAddContact(context, number) },
                onLongPress = { entry -> sheetEntry = entry },
            )
        }
    }
}

private fun openSms(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_SENDTO, "smsto:$number".toUri())
    runCatching { context.startActivity(intent) }
}

private fun copyToClipboard(context: Context, number: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Phone number", number))
}

private fun openAddContact(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_INSERT_OR_EDIT)
        .setType("vnd.android.cursor.item/contact")
        .putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, number)
    runCatching { context.startActivity(intent) }
}

private fun tryPlace(
    viewModel: DialerViewModel,
    number: String,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
) {
    val result = viewModel.place(number)
    if (result == CallPlacer.PlaceResult.NO_PERMISSION) {
        scope.launch { snackbarHostState.showSnackbar("Grant Call permission to dial.") }
    }
}

@Composable
private fun RecentsContent(
    state: DialerUiState,
    hasPermission: Boolean,
    onRequest: () -> Unit,
    onCallNumber: (String) -> Unit,
    onOpenHistory: (String) -> Unit,
    onAddToContacts: (String) -> Unit,
    onLongPress: (RecentEntry) -> Unit,
) {
    if (!hasPermission) {
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
            contentType = { "recent" },
        ) { entry ->
            RecentRow(
                entry = entry,
                onCall = { onCallNumber(entry.number) },
                onOpenHistory = { onOpenHistory(entry.number) },
                onAddToContacts = { onAddToContacts(entry.number) },
                onLongPress = { onLongPress(entry) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(
    entry: RecentEntry,
    onCall: () -> Unit,
    onOpenHistory: () -> Unit,
    onAddToContacts: () -> Unit,
    onLongPress: () -> Unit,
) {
    // Whole row is tap-history / long-press-sheet; the right-side icons
    // catch their own clicks so the row's onClick doesn't fire for them.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpenHistory, onLongClick = onLongPress)
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
        // Unknown numbers get a quick "+" → system's create-new / save-to-existing
        // chooser; known numbers don't need it.
        if (entry.contactId == null) {
            IconButton(onClick = onAddToContacts) {
                Icon(
                    Icons.Filled.PersonAdd,
                    contentDescription = "Add to contacts",
                )
            }
        }
        IconButton(onClick = onCall) {
            Icon(Icons.Filled.Call, contentDescription = "Call back")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentActionsSheet(
    entry: RecentEntry,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onCopy: () -> Unit,
    onOpenContact: (() -> Unit)?,
    onAddContact: (() -> Unit)?,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonAvatar(
                    photoRelativePath = entry.photoRelativePath,
                    displayName = entry.displayName ?: entry.number,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = entry.displayName ?: entry.number,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (entry.displayName != null) {
                        Text(
                            text = entry.number,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider()
            SheetAction(Icons.Filled.Call, "Call", onCall)
            SheetAction(Icons.AutoMirrored.Filled.Message, "Message", onMessage)
            SheetAction(Icons.Filled.ContentCopy, "Copy number", onCopy)
            onOpenContact?.let { SheetAction(Icons.Filled.Person, "View contact", it) }
            onAddContact?.let { SheetAction(Icons.Filled.PersonAdd, "Add to contacts", it) }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
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

// Cached so we don't allocate a DateFormat per Recents row per recomposition.
// DateFormat instances aren't thread-safe, but Compose recomposition runs on
// the main thread so a single shared instance is fine here.
private val timestampFormat: DateFormat by lazy {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
}

private fun formatTimestamp(millis: Long): String = timestampFormat.format(Date(millis))

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
