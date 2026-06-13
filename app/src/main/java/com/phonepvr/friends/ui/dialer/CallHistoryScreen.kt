package com.phonepvr.friends.ui.dialer

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.data.calllog.DeviceCall
import com.phonepvr.friends.data.dialer.CallPlacer
import com.phonepvr.friends.domain.model.CallType
import com.phonepvr.friends.ui.components.CallTypeBadge
import com.phonepvr.friends.ui.components.PersonAvatar
import com.phonepvr.friends.ui.theme.callColor
import com.phonepvr.friends.ui.permissions.PermissionRationaleSheet
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    onBack: () -> Unit,
    onOpenContact: (Long) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onSaveNumber: (String) -> Unit,
    viewModel: CallHistoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var hasCallPhone by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var showCallRationale by remember { mutableStateOf(false) }

    // Shows the SIM chooser first on a multi-SIM device with no default.
    val simLauncher = rememberSimCallLauncher(
        accounts = viewModel::callCapableAccounts,
        needsChoice = viewModel::needsSimChoice,
        call = { _, account ->
            val result = viewModel.placeCall(account)
            if (result == CallPlacer.PlaceResult.NO_PERMISSION) {
                scope.launch { snackbarState.showSnackbar("Grant Call permission to dial.") }
            }
        },
    )

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCallPhone = granted
        if (granted) simLauncher.launch(viewModel.number)
    }

    LaunchedEffect(state.placeError) {
        state.placeError?.let { msg ->
            snackbarState.showSnackbar(msg)
            viewModel.dismissPlaceError()
        }
    }

    if (showCallRationale) {
        PermissionRationaleSheet(
            title = "Permission to call",
            body = "Bondwidth needs your phone's Call permission to dial. " +
                "Stays on the device.",
            manualFallback = "Skip and we'll hand off to your existing phone app.",
            grantLabel = "Grant access",
            manualLabel = "Use system dialer",
            onGrant = {
                showCallRationale = false
                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            },
            onManualFallback = {
                showCallRationale = false
                val intent = Intent(Intent.ACTION_DIAL, "tel:${state.number}".toUri())
                runCatching { context.startActivity(intent) }
            },
            onDismiss = { showCallRationale = false },
        )
    }

    val onCallTap: () -> Unit = {
        if (hasCallPhone) {
            simLauncher.launch(viewModel.number)
        } else {
            showCallRationale = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.displayName ?: state.number) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) { Snackbar(it) } },
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Header(
                state = state,
                onCall = onCallTap,
                onMessage = { openSms(context, state.number) },
                onCopy = {
                    copyToClipboard(context, state.number)
                    scope.launch { snackbarState.showSnackbar("Number copied") }
                },
                onOpenContact = state.contactId?.let { id -> { onOpenContact(id) } },
                onAddToContacts = if (state.contactId == null) {
                    { onSaveNumber(state.number) }
                } else {
                    null
                },
                onOpenPerson = state.bondedPersonId?.let { id -> { onOpenPerson(id) } },
            )
            HorizontalDivider()
            CallsSection(calls = state.calls)
        }
    }
}

@Composable
private fun Header(
    state: CallHistoryUiState,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onCopy: () -> Unit,
    onOpenContact: (() -> Unit)?,
    onAddToContacts: (() -> Unit)?,
    onOpenPerson: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.photoRelativePath != null || state.photoUri != null) {
            PersonAvatar(
                photoRelativePath = state.photoRelativePath,
                displayName = state.displayName ?: state.number,
                diameter = 96.dp,
                photoUri = state.photoUri,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (state.displayName ?: state.number)
                        .trim().firstOrNull()?.uppercase() ?: "?",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = state.displayName ?: state.number,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        if (state.isBonded) {
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Bonded",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        if (state.displayName != null && state.number.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.number,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            HeaderAction(Icons.Filled.Call, "Call", onCall)
            HeaderAction(Icons.AutoMirrored.Filled.Message, "Message", onMessage)
            HeaderAction(Icons.Filled.ContentCopy, "Copy", onCopy)
            onAddToContacts?.let {
                HeaderAction(Icons.Filled.PersonAdd, "Save", it)
            }
            onOpenContact?.let {
                HeaderAction(Icons.Filled.Person, "Contact", it)
            }
            onOpenPerson?.let {
                HeaderAction(Icons.Filled.Favorite, "Profile", it)
            }
        }
    }
}

@Composable
private fun HeaderAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CallsSection(calls: List<DeviceCall>) {
    if (calls.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No call history in the last 365 days.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    Column {
        Text(
            text = if (calls.size == 1) "1 call" else "${calls.size} calls",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp),
        )
        LazyColumn {
            items(calls, key = { "${it.timestampMillis}-${it.type}" }) { call ->
                CallRow(call)
            }
        }
    }
}

@Composable
private fun CallRow(call: DeviceCall) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CallTypeBadge(type = call.type, size = 36.dp, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = call.type.label() +
                    if (call.durationSeconds > 0) " · ${formatDuration(call.durationSeconds)}" else "",
                style = MaterialTheme.typography.bodyLarge,
                color = callColor(call.type),
            )
            Text(
                text = historyTimestampFormat.format(Date(call.timestampMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun CallType.label(): String = when (this) {
    CallType.INCOMING -> "Incoming"
    CallType.OUTGOING -> "Outgoing"
    CallType.MISSED -> "Missed"
    CallType.REJECTED -> "Rejected"
}

private fun formatDuration(seconds: Long): String {
    val mins = TimeUnit.SECONDS.toMinutes(seconds)
    val secs = seconds - TimeUnit.MINUTES.toSeconds(mins)
    return if (mins > 0) {
        String.format(Locale.US, "%d:%02d", mins, secs)
    } else {
        "$secs sec"
    }
}

private val historyTimestampFormat: DateFormat by lazy {
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
}

private fun openSms(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_SENDTO, "smsto:$number".toUri())
    runCatching { context.startActivity(intent) }
}

private fun copyToClipboard(context: Context, number: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Phone number", number))
}

