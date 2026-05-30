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

private val LEAD_DAY_OPTIONS = listOf(1, 2, 3, 5, 7, 14, 30)
private val HOUR_OPTIONS = (0..23).toList()
private val CADENCE_OPTIONS = listOf(1, 3, 7, 14, 30, 45, 60, 90, 120, 180, 270, 360)
private val BACKUP_NUDGE_OPTIONS = listOf(7, 14, 21, 30, 60, 90)

private enum class SettingsDialog { THEME, LEAD_DAYS, HOUR, CADENCE, BACKUP_NUDGE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenMyQuotes: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isDefaultDialer by viewModel.isDefaultDialer.collectAsStateWithLifecycle()
    var activeDialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var showLockUnavailable by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showRestrictedSettingsDialog by remember { mutableStateOf(false) }

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
    ) { viewModel.refreshDialerRoleState() }

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
                                "Using Friends' warm palette"
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

            HorizontalDivider()
            SectionHeader("New people")
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

            HorizontalDivider()
            SectionHeader("Personalisation")
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

            HorizontalDivider()
            SectionHeader("About")
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

        null -> Unit
    }

    if (showRestrictedSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showRestrictedSettingsDialog = false },
            title = { Text("Allow restricted settings") },
            text = {
                Column {
                    Text(
                        "Android 13 and newer block apps installed outside " +
                            "the Play Store from becoming the default phone " +
                            "app. The dialog you saw — “App was denied " +
                            "access to be the default Phone app” — is " +
                            "Android's standard refusal, not a Bondwidth " +
                            "limit. Same wording on Samsung, Xiaomi, Pixel.",
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("To allow it, just once:")
                    Spacer(Modifier.height(6.dp))
                    Text("1.  Tap “Open app info” below.")
                    Text("2.  Tap the ⋮ menu in the top-right corner of that screen.")
                    Text("3.  Tap “Allow restricted settings” and confirm.")
                    Text("4.  Come back here and tap “Default phone app” again.")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "If you don't see the ⋮ menu, the manufacturer " +
                            "has hidden it. On some Xiaomi / HyperOS builds " +
                            "you also have to turn off Developer options → " +
                            "Turn on MIUI optimization, then reboot. Calls " +
                            "still place fine without the role — only the " +
                            "in-call screen falls back to your existing one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestrictedSettingsDialog = false
                        openAppInfo(context)
                    },
                ) { Text("Open app info") }
            },
            dismissButton = {
                TextButton(onClick = { showRestrictedSettingsDialog = false }) {
                    Text("Close")
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
}

private const val RELEASES_URL = "https://github.com/phonepvr/friends/releases"

private fun openReleasesPage(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_VIEW, RELEASES_URL.toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openAppInfo(context: android.content.Context) {
    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData("package:${context.packageName}".toUri())
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
