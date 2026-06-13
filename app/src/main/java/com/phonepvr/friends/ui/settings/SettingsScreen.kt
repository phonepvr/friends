package com.phonepvr.friends.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.core.net.toUri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.phonepvr.friends.BuildConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.domain.model.ThemeMode
import com.phonepvr.friends.ui.lock.isAppLockAvailable
import com.phonepvr.friends.ui.permissions.RestrictedDialerSettingsDialog

private val LEAD_DAY_OPTIONS = listOf(1, 2, 3, 5, 7, 14, 30)
private val HOUR_OPTIONS = (0..23).toList()
private val CADENCE_OPTIONS = listOf(1, 3, 7, 14, 30, 45, 60, 90, 120, 180, 270, 360)
private val BACKUP_NUDGE_OPTIONS = listOf(7, 14, 21, 30, 60, 90)

private enum class SettingsDialog {
    THEME, LEAD_DAYS, HOUR, CADENCE, BACKUP_NUDGE, IMPORT_BACKUP_WARN,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenMyQuotes: () -> Unit,
    onOpenQuickReplies: () -> Unit,
    onOpenSpeedDial: () -> Unit,
    onOpenMergeDuplicates: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isDefaultDialer by viewModel.isDefaultDialer.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    var activeDialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var showLockUnavailable by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showRestrictedSettingsDialog by remember { mutableStateOf(false) }

    // Storage-Access-Framework picker: user chooses where the .vcf goes.
    // No storage permission needed because the URI grants per-document access.
    val exportContactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/x-vcard"),
    ) { destination ->
        if (destination != null) viewModel.exportContactsToVcf(destination)
    }

    // Mime-type filter is lenient (*/*) because many devices report .vcf
    // files as application/octet-stream and the picker would hide them
    // otherwise. The parser validates the content itself.
    val importContactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { source ->
        if (source != null) viewModel.importContactsFromVcf(source)
    }

    // Re-read the default-dialer state every time Settings becomes
    // foreground: if the user toggled the role in another app between
    // visits, the row reflects it without a restart.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDialerRoleState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val dialerRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val granted = viewModel.refreshDialerRoleState()
        // On Android 13+, a sideloaded app's role request is silently blocked
        // by "restricted settings" — it returns without granting and with no
        // error. Detect the no-op and open the guided fix automatically rather
        // than relying on the user to find the "Can't set as default?" row.
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            showRestrictedSettingsDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(rememberScrollState()),
        ) {
            VersionUpdateCard(
                versionName = BuildConfig.VERSION_NAME,
                onTap = { showUpdateDialog = true },
            )

            SectionHeader("Appearance")
            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = { Text(themeLabel(settings.themeMode)) },
                modifier = Modifier.clickable { activeDialog = SettingsDialog.THEME },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text("Match my wallpaper's colours") },
                    supportingContent = {
                        Text(
                            if (settings.dynamicColorEnabled) {
                                "Using Android's wallpaper-derived palette"
                            } else {
                                "Using Bondwidth's warm palette"
                            },
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.dynamicColorEnabled,
                            onCheckedChange = viewModel::setDynamicColorEnabled,
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.setDynamicColorEnabled(!settings.dynamicColorEnabled)
                    },
                )
            }

            HorizontalDivider()
            SectionHeader("Reminders")
            ListItem(
                headlineContent = { Text("Notify me before events") },
                supportingContent = {
                    Text("${daysLabel(settings.notificationLeadDays)} ahead")
                },
                modifier = Modifier.clickable { activeDialog = SettingsDialog.LEAD_DAYS },
            )
            ListItem(
                headlineContent = { Text("Daily reminder time") },
                supportingContent = { Text(formatHour(settings.notificationHour)) },
                modifier = Modifier.clickable { activeDialog = SettingsDialog.HOUR },
            )
            ListItem(
                headlineContent = { Text("Default check-in cadence") },
                supportingContent = {
                    Text("Every ${daysLabel(settings.defaultCadenceDays)}")
                },
                modifier = Modifier.clickable { activeDialog = SettingsDialog.CADENCE },
            )

            HorizontalDivider()
            SectionHeader("Phone app")
            ListItem(
                headlineContent = { Text("Default phone app") },
                supportingContent = {
                    Text(
                        if (isDefaultDialer) {
                            "Bondwidth handles calls on this device."
                        } else {
                            "Tap to choose Bondwidth as your phone app."
                        },
                    )
                },
                modifier = Modifier.clickable {
                    val intent = viewModel.makeAcquireDialerRoleIntent() ?: return@clickable
                    dialerRoleLauncher.launch(intent)
                },
            )
            if (!isDefaultDialer) {
                ListItem(
                    headlineContent = { Text("Open phone-app settings") },
                    supportingContent = {
                        Text(
                            "Direct link to system Settings → Default apps. " +
                                "Pick Bondwidth under “Phone app” there if the " +
                                "in-app picker doesn't work.",
                        )
                    },
                    modifier = Modifier.clickable {
                        runCatching {
                            context.startActivity(viewModel.makeDefaultAppsSettingsIntent())
                        }
                    },
                )
                ListItem(
                    headlineContent = { Text("Can't set Bondwidth as default?") },
                    supportingContent = {
                        Text(
                            "Android 13+ blocks sideloaded apps from becoming " +
                                "the default phone app by default. Tap to see " +
                                "how to allow it.",
                        )
                    },
                    modifier = Modifier.clickable { showRestrictedSettingsDialog = true },
                )
            }
            ListItem(
                headlineContent = { Text("Blocked numbers") },
                supportingContent = {
                    Text("Manage the numbers that can't reach you. Opens the system blocked-numbers list.")
                },
                modifier = Modifier.clickable { openBlockedNumbers(context) },
            )
            ListItem(
                headlineContent = { Text("Quick replies") },
                supportingContent = {
                    Text("Pre-written messages for declining a call when you can't talk.")
                },
                modifier = Modifier.clickable { onOpenQuickReplies() },
            )
            ListItem(
                headlineContent = { Text("Speed dial") },
                supportingContent = {
                    Text("Assign contacts to keys 1–9; long-press on the dialpad to call.")
                },
                modifier = Modifier.clickable { onOpenSpeedDial() },
            )

            HorizontalDivider()
            SectionHeader("Security")
            val toggleAppLock: (Boolean) -> Unit = { wantOn ->
                if (wantOn && !isAppLockAvailable(context)) {
                    showLockUnavailable = true
                } else {
                    viewModel.setAppLockEnabled(wantOn)
                }
            }
            ListItem(
                headlineContent = { Text("App lock") },
                supportingContent = {
                    Text("Require fingerprint or screen lock to open Bondwidth")
                },
                trailingContent = {
                    Switch(
                        checked = settings.appLockEnabled,
                        onCheckedChange = toggleAppLock,
                    )
                },
                modifier = Modifier.clickable { toggleAppLock(!settings.appLockEnabled) },
            )
            ListItem(
                headlineContent = { Text("Hide from screenshots & recents") },
                supportingContent = {
                    Text(
                        "Blocks screenshots, screen recording, casting, and the " +
                            "preview tile in the recents view. Useful when " +
                            "lending your phone.",
                    )
                },
                trailingContent = {
                    Switch(
                        checked = settings.hideFromScreenshots,
                        onCheckedChange = viewModel::setHideFromScreenshots,
                    )
                },
                modifier = Modifier.clickable {
                    viewModel.setHideFromScreenshots(!settings.hideFromScreenshots)
                },
            )

            HorizontalDivider()
            SectionHeader("Data")
            ListItem(
                headlineContent = { Text("Backup & restore") },
                supportingContent = { Text("Export or import all your data") },
                modifier = Modifier.clickable { onOpenBackup() },
            )
            ListItem(
                headlineContent = { Text("Backup reminder interval") },
                supportingContent = {
                    Text("Nudge me every ${daysLabel(settings.backupNudgeIntervalDays)}")
                },
                modifier = Modifier.clickable { activeDialog = SettingsDialog.BACKUP_NUDGE },
            )
            ListItem(
                headlineContent = { Text("Export contacts to vCard") },
                supportingContent = {
                    Text(
                        "Save every contact on this phone as a single .vcf file. " +
                            "Any contacts app can import it back.",
                    )
                },
                modifier = Modifier.clickable {
                    exportContactsLauncher.launch(viewModel.defaultExportFileName())
                },
            )
            ListItem(
                headlineContent = { Text("Import contacts from vCard") },
                supportingContent = {
                    Text(
                        "Pick a .vcf file (from another phone, an email, a backup) " +
                            "and add every card in it to your contacts. Won't touch " +
                            "the ones you already have.",
                    )
                },
                // Importing can change your on-device data and there's no
                // cloud copy (the app is fully offline), so nudge a backup
                // first rather than launching the picker straight away.
                modifier = Modifier.clickable {
                    activeDialog = SettingsDialog.IMPORT_BACKUP_WARN
                },
            )
            ListItem(
                headlineContent = { Text("Merge duplicate contacts") },
                supportingContent = {
                    Text("Find contacts saved more than once and link them into one.")
                },
                modifier = Modifier.clickable { onOpenMergeDuplicates() },
            )

            HorizontalDivider()
            SectionHeader("About & more")
            ListItem(
                headlineContent = { Text("My quotes") },
                supportingContent = {
                    Text("See today's quote and add your own")
                },
                modifier = Modifier.clickable { onOpenMyQuotes() },
            )
            ListItem(
                headlineContent = { Text("Replay intro") },
                supportingContent = {
                    Text("See the welcome screens again")
                },
                modifier = Modifier.clickable { onReplayOnboarding() },
            )
            ListItem(
                headlineContent = { Text("About Bondwidth") },
                supportingContent = {
                    Text("What it is, how it works, your privacy")
                },
                modifier = Modifier.clickable { onOpenAbout() },
            )
        }
    }

    when (activeDialog) {
        SettingsDialog.THEME -> ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries,
            selected = settings.themeMode,
            labelOf = ::themeLabel,
            onSelect = { viewModel.setThemeMode(it) },
            onDismiss = { activeDialog = null },
        )

        SettingsDialog.LEAD_DAYS -> ChoiceDialog(
            title = "Notify me before events",
            options = LEAD_DAY_OPTIONS,
            selected = settings.notificationLeadDays,
            labelOf = { "${daysLabel(it)} ahead" },
            onSelect = { viewModel.setNotificationLeadDays(it) },
            onDismiss = { activeDialog = null },
        )

        SettingsDialog.HOUR -> ChoiceDialog(
            title = "Daily reminder time",
            options = HOUR_OPTIONS,
            selected = settings.notificationHour,
            labelOf = ::formatHour,
            onSelect = { viewModel.setNotificationHour(it) },
            onDismiss = { activeDialog = null },
        )

        SettingsDialog.CADENCE -> ChoiceDialog(
            title = "Default check-in cadence",
            options = CADENCE_OPTIONS,
            selected = settings.defaultCadenceDays,
            labelOf = { "Every ${daysLabel(it)}" },
            onSelect = { viewModel.setDefaultCadenceDays(it) },
            onDismiss = { activeDialog = null },
        )

        SettingsDialog.BACKUP_NUDGE -> ChoiceDialog(
            title = "Backup reminder interval",
            options = BACKUP_NUDGE_OPTIONS,
            selected = settings.backupNudgeIntervalDays,
            labelOf = { "Every ${daysLabel(it)}" },
            onSelect = { viewModel.setBackupNudgeIntervalDays(it) },
            onDismiss = { activeDialog = null },
        )

        SettingsDialog.IMPORT_BACKUP_WARN -> AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = { Text("Back up first?") },
            text = {
                Text(
                    "Bondwidth keeps everything on this device — there's no cloud " +
                        "copy. Importing can change your data, so it's safest to " +
                        "export a backup before you do.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    activeDialog = null
                    onOpenBackup()
                }) { Text("Back up first") }
            },
            dismissButton = {
                TextButton(onClick = {
                    activeDialog = null
                    importContactsLauncher.launch(
                        arrayOf("text/x-vcard", "text/vcard", "text/directory", "*/*"),
                    )
                }) { Text("Import anyway") }
            },
        )

        null -> Unit
    }

    if (showRestrictedSettingsDialog) {
        RestrictedDialerSettingsDialog(
            onDismiss = { showRestrictedSettingsDialog = false },
            onOpenAppInfo = {
                runCatching { context.startActivity(viewModel.makeAppInfoIntent()) }
            },
            onOpenDefaultApps = {
                runCatching {
                    context.startActivity(viewModel.makeDefaultAppsSettingsIntent())
                }
            },
        )
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Updating Bondwidth") },
            text = {
                Column {
                    Text(
                        "Bondwidth never asks for the INTERNET permission, " +
                            "so it can't fetch updates on its own. New builds " +
                            "land on GitHub Releases as signed APKs and you " +
                            "install them by hand.",
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("To update:")
                    Spacer(Modifier.height(4.dp))
                    Text("1.  Tap “Open releases page” below — this opens GitHub in your browser.")
                    Text("2.  Pick the newest Bondwidth-v*.apk under Assets.")
                    Text("3.  Open the downloaded file from your notifications or your file manager and tap Install. Android applies the update on top of your existing app.")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Every release uses the same signing key, so updates " +
                            "install cleanly without touching your bonds, " +
                            "timeline or settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUpdateDialog = false
                        openReleasesPage(context)
                    },
                ) { Text("Open releases page") }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) { Text("Close") }
            },
        )
    }

    if (showLockUnavailable) {
        AlertDialog(
            onDismissRequest = { showLockUnavailable = false },
            title = { Text("App lock unavailable") },
            text = {
                Text(
                    "Set up a fingerprint, PIN, pattern, or password in your " +
                        "device settings, then turn on app lock here.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showLockUnavailable = false }) { Text("OK") }
            },
        )
    }

    when (val s = exportState) {
        ExportState.Idle -> Unit
        ExportState.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Exporting contacts") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).width(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Reading and writing the vCard…")
                }
            },
            confirmButton = {},
        )
        is ExportState.Done -> AlertDialog(
            onDismissRequest = { viewModel.acknowledgeExportResult() },
            title = { Text("Export complete") },
            text = {
                Text(
                    if (s.count == 1) "1 contact written." else "${s.count} contacts written.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.acknowledgeExportResult() }) { Text("OK") }
            },
        )
        is ExportState.Error -> AlertDialog(
            onDismissRequest = { viewModel.acknowledgeExportResult() },
            title = { Text("Export failed") },
            text = { Text(s.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.acknowledgeExportResult() }) { Text("OK") }
            },
        )
    }

    when (val s = importState) {
        ImportState.Idle -> Unit
        ImportState.Reading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Reading vCard file") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).width(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Parsing the file…")
                }
            },
            confirmButton = {},
        )
        is ImportState.Importing -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Importing contacts") },
            text = {
                Column {
                    Text("Adding ${s.done + 1} of ${s.total}…")
                }
            },
            confirmButton = {},
        )
        is ImportState.Done -> AlertDialog(
            onDismissRequest = { viewModel.acknowledgeImportResult() },
            title = { Text("Import complete") },
            text = {
                Column {
                    Text(
                        if (s.imported == 1) {
                            "1 contact added."
                        } else {
                            "${s.imported} contacts added."
                        },
                    )
                    if (s.skipped > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${s.skipped} skipped " +
                                "(usually because they had no name or your contacts app refused).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.acknowledgeImportResult() }) { Text("OK") }
            },
        )
        is ImportState.Error -> AlertDialog(
            onDismissRequest = { viewModel.acknowledgeImportResult() },
            title = { Text("Import failed") },
            text = { Text(s.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.acknowledgeImportResult() }) { Text("OK") }
            },
        )
    }
}

private const val RELEASES_URL = "https://github.com/phonepvr/friends/releases"

private fun openReleasesPage(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_VIEW, RELEASES_URL.toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openBlockedNumbers(context: android.content.Context) {
    val telecom = context.getSystemService(android.content.Context.TELECOM_SERVICE)
        as? android.telecom.TelecomManager
    val intent = telecom?.createManageBlockedNumbersIntent()
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent != null) {
        runCatching { context.startActivity(intent) }
    }
}

@Composable
private fun VersionUpdateCard(versionName: String, onTap: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .clickable(onClick = onTap),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bondwidth v$versionName",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Tap to update",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { option ->
                    val choose = {
                        onSelect(option)
                        onDismiss()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = choose)
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = choose,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(labelOf(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Follow system"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun daysLabel(days: Int): String = if (days == 1) "1 day" else "$days days"

private fun formatHour(hour: Int): String {
    val period = if (hour < 12) "AM" else "PM"
    val twelveHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$twelveHour:00 $period"
}
