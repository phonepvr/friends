package com.phonepvr.friends.ui.calls

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.domain.model.CallType
import com.phonepvr.friends.ui.common.formatTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationQueueScreen(
    bottomBar: @Composable () -> Unit,
    viewModel: ConfirmationQueueViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val queue by viewModel.items.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionRequested = true
    }

    // Scan automatically whenever the screen is shown with permission granted.
    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.scan()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Calls to review") }) },
        bottomBar = bottomBar,
        floatingActionButton = {
            if (hasPermission) {
                ExtendedFloatingActionButton(onClick = { viewModel.scan() }) {
                    Text(if (scanning) "Scanning…" else "Scan call log")
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                !hasPermission -> PermissionPrompt(
                    denied = permissionRequested,
                    onRequest = {
                        permissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                    },
                )

                queue.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (scanning) {
                            "Scanning…"
                        } else {
                            "No calls to review. Tap Scan call log."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(queue, key = { it.confirmation.id }) { item ->
                        ConfirmationCard(
                            item = item,
                            onConfirm = { personId -> viewModel.confirm(item, personId) },
                            onDismiss = { viewModel.dismiss(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmationCard(
    item: ConfirmationItem,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${callTypeLabel(item.confirmation.callType)} · " +
                    formatTimestamp(item.confirmation.callTimestamp),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = if (item.candidates.size == 1) {
                    "with ${candidateLabel(item.candidates.first())}"
                } else {
                    "Matches multiple people:"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            item.candidates.forEach { candidate ->
                Button(
                    onClick = { onConfirm(candidate.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (item.candidates.size == 1) {
                            "Log it"
                        } else {
                            "Log as ${candidateLabel(candidate)}"
                        },
                    )
                }
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun PermissionPrompt(denied: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Call log permission needed", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (denied) {
                "Permission was denied. Grant it in system settings, " +
                    "or go back and log interactions manually."
            } else {
                "Friends can match recent calls to your people so you can log them " +
                    "with one tap. Nothing leaves your device."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Grant access") }
    }
}

private fun callTypeLabel(type: CallType): String = when (type) {
    CallType.INCOMING -> "Incoming call"
    CallType.OUTGOING -> "Outgoing call"
    CallType.MISSED -> "Missed call"
    CallType.REJECTED -> "Rejected call"
}

private fun candidateLabel(candidate: PersonRef): String =
    if (candidate.matchedPhoneLabel.isNullOrBlank()) {
        candidate.name
    } else {
        "${candidate.name} — ${candidate.matchedPhoneLabel}"
    }
