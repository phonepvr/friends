package com.phonepvr.friends.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.domain.model.ThemeMode
import com.phonepvr.friends.ui.lock.isAppLockAvailable

private val LEAD_DAY_OPTIONS = listOf(1, 2, 3, 5, 7, 14, 30)
private val HOUR_OPTIONS = (0..23).toList()
private val CADENCE_OPTIONS = listOf(7, 14, 30, 45, 60, 90)

private enum class SettingsDialog { THEME, LEAD_DAYS, HOUR, CADENCE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var activeDialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var showLockUnavailable by remember { mutableStateOf(false) }

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
            SectionHeader("Appearance")
            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = { Text(themeLabel(settings.themeMode)) },
                modifier = Modifier.clickable { activeDialog = SettingsDialog.THEME },
            )

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
                    Text("Require fingerprint or screen lock to open Friends")
                },
                trailingContent = {
                    Switch(
                        checked = settings.appLockEnabled,
                        onCheckedChange = toggleAppLock,
                    )
                },
                modifier = Modifier.clickable { toggleAppLock(!settings.appLockEnabled) },
            )

            HorizontalDivider()
            SectionHeader("Data")
            ListItem(
                headlineContent = { Text("Backup & restore") },
                supportingContent = { Text("Export or import all your data") },
                modifier = Modifier.clickable { onOpenBackup() },
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

        null -> Unit
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
