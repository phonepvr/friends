package com.phonepvr.friends.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.data.backup.AutoBackupFrequency
import com.phonepvr.friends.data.backup.BackupCounts
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val auto by viewModel.autoBackup.collectAsStateWithLifecycle()
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingExportPassphrase by remember { mutableStateOf("") }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val passphrase = pendingExportPassphrase
        pendingExportPassphrase = ""
        if (uri != null) viewModel.export(uri, passphrase)
    }
    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.onFilePicked(uri)
    }
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) viewModel.onFolderPicked(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & restore") },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            ActionCard(
                title = "Export a backup",
                body = "Saves everyone, their important dates, your timeline and " +
                    "the call review queue to a single file you choose. Add a " +
                    "passphrase to encrypt it. Nothing leaves your device.",
                buttonText = "Export backup",
                enabled = !state.busy,
                onClick = { showExportDialog = true },
            )
            AutoBackupCard(
                state = auto,
                onToggle = { on ->
                    // Switching on needs a folder first; picking one turns it on.
                    if (on && auto.folderLabel == null) {
                        pickFolder.launch(null)
                    } else {
                        viewModel.setAutoBackupEnabled(on)
                    }
                },
                onChooseFolder = { pickFolder.launch(null) },
                onFrequency = viewModel::setAutoBackupFrequency,
                onKeep = viewModel::setAutoBackupKeep,
                onBackUpNow = viewModel::backUpNow,
            )
            ActionCard(
                title = "Restore from a backup",
                body = "Replaces everything in Bondwidth with the contents of a " +
                    "backup file. Use this when moving to a new phone.",
                buttonText = "Import backup",
                enabled = !state.busy,
                onClick = { showImportConfirm = true },
            )
            state.result?.let { result ->
                ResultCard(result = result, onDismiss = viewModel::clearResult)
            }
        }
    }

    if (showExportDialog) {
        ExportOptionsDialog(
            onDismiss = { showExportDialog = false },
            onConfirm = { passphrase ->
                showExportDialog = false
                pendingExportPassphrase = passphrase
                val extension = if (passphrase.isEmpty()) "zip" else "fbk"
                createDocument.launch("friends-backup-${LocalDate.now()}.$extension")
            },
        )
    }
    if (showImportConfirm) {
        ImportConfirmDialog(
            onDismiss = { showImportConfirm = false },
            onConfirm = {
                showImportConfirm = false
                openDocument.launch(arrayOf("*/*"))
            },
        )
    }
    if (state.awaitingPassphrase) {
        ImportPassphraseDialog(
            error = state.passphraseError,
            busy = state.busy,
            onSubmit = viewModel::submitPassphrase,
            onCancel = viewModel::cancelPassphrase,
        )
    }
}

@Composable
private fun ActionCard(
    title: String,
    body: String,
    buttonText: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(buttonText)
            }
        }
    }
}

private val AUTO_BACKUP_KEEP_CHOICES = listOf(3, 5, 10, 20)

private fun frequencyLabel(frequency: AutoBackupFrequency): String = when (frequency) {
    AutoBackupFrequency.DAILY -> "Daily"
    AutoBackupFrequency.WEEKLY -> "Weekly"
    AutoBackupFrequency.MONTHLY -> "Monthly"
}

private val autoBackupTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

private fun formatBackupTime(epochMillis: Long): String =
    autoBackupTimeFormat.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/**
 * Opt-in scheduled backup: saves a backup file to a folder the user picks, on a
 * schedule, keeping the last few. Entirely on-device; nothing is uploaded. Shows
 * when the last backup happened and, if the last attempt failed, why.
 */
@Composable
private fun AutoBackupCard(
    state: AutoBackupUiState,
    onToggle: (Boolean) -> Unit,
    onChooseFolder: () -> Unit,
    onFrequency: (AutoBackupFrequency) -> Unit,
    onKeep: (Int) -> Unit,
    onBackUpNow: () -> Unit,
) {
    val active = state.enabled && state.folderLabel != null
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Automatic backup",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = active, onCheckedChange = onToggle)
            }
            Text(
                text = "Saves a backup file to a folder you choose, on a schedule, and keeps " +
                    "the most recent few. It never leaves this phone unless you point the " +
                    "folder at a sync tool such as Syncthing. Backups here are not encrypted; " +
                    "use Export for an encrypted copy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.folderLabel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Folder: ${state.folderLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onChooseFolder) { Text("Change") }
                }
            }
            if (active) {
                Text("How often", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoBackupFrequency.entries.forEach { f ->
                        FilterChip(
                            selected = state.frequency == f,
                            onClick = { onFrequency(f) },
                            label = { Text(frequencyLabel(f)) },
                        )
                    }
                }
                Text("Backups to keep", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AUTO_BACKUP_KEEP_CHOICES.forEach { n ->
                        FilterChip(
                            selected = state.keep == n,
                            onClick = { onKeep(n) },
                            label = { Text(n.toString()) },
                        )
                    }
                }
                Text(
                    text = state.lastSuccessAt
                        ?.let { "Last backup: ${formatBackupTime(it)}" }
                        ?: "No backup yet",
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.lastError?.let { error ->
                    Text(
                        text = "Last attempt failed" +
                            (state.lastAttemptAt?.let { " (${formatBackupTime(it)})" } ?: "") +
                            ": $error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = onBackUpNow,
                    enabled = !state.running,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(if (state.running) "Backing up…" else "Back up now")
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: BackupResult, onDismiss: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (result) {
                BackupResult.Exported -> {
                    Text("Backup saved", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Your backup was written to the location you chose.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is BackupResult.Restored -> {
                    Text("Restore complete", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Everything from the backup is now in Bondwidth.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RestoreCounts(result.counts)
                }

                is BackupResult.Failed -> {
                    Text(
                        text = "Something went wrong",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = result.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun RestoreCounts(counts: BackupCounts) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CountRow("People", counts.people)
        CountRow("Phone numbers", counts.phoneNumbers)
        CountRow("Important dates", counts.events)
        CountRow("Timeline entries", counts.timelineEntries)
    }
}

@Composable
private fun CountRow(label: String, value: Int) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun ExportOptionsDialog(
    onDismiss: () -> Unit,
    onConfirm: (passphrase: String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    val mismatch = passphrase.isNotEmpty() && confirmPassphrase != passphrase
    val canExport = passphrase.isEmpty() || confirmPassphrase == passphrase

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Leave the passphrase blank for a plain backup, or set " +
                        "one to encrypt the file. You will need the same " +
                        "passphrase to restore it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Passphrase (optional)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (passphrase.isNotEmpty()) {
                    OutlinedTextField(
                        value = confirmPassphrase,
                        onValueChange = { confirmPassphrase = it },
                        label = { Text("Confirm passphrase") },
                        singleLine = true,
                        isError = mismatch,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (mismatch) {
                        Text(
                            text = "Passphrases do not match.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(passphrase) },
                enabled = canExport,
            ) {
                Text("Choose file")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ImportConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
        title = { Text("Replace all data?") },
        text = {
            Text(
                text = "Restoring a backup deletes everything currently in " +
                    "Bondwidth and replaces it with the backup's contents. " +
                    "This cannot be undone.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Choose file") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ImportPassphraseDialog(
    error: String?,
    busy: Boolean,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text("Encrypted backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "This backup is encrypted. Enter the passphrase used " +
                        "when it was created.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Passphrase") },
                    singleLine = true,
                    isError = error != null,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(passphrase) },
                enabled = passphrase.isNotEmpty() && !busy,
            ) {
                Text("Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel") }
        },
    )
}
