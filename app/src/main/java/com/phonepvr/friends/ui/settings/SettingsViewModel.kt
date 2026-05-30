package com.phonepvr.friends.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.contacts.ContactsReader
import com.phonepvr.friends.data.contacts.VCardBuilder
import com.phonepvr.friends.data.settings.AppSettings
import com.phonepvr.friends.data.settings.SettingsRepository
import com.phonepvr.friends.domain.model.ThemeMode
import com.phonepvr.friends.role.DialerRoleManager
import com.phonepvr.friends.work.rescheduleReminderWork
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Lifecycle of the "export all contacts" flow, surfaced to the UI. */
sealed interface ExportState {
    data object Idle : ExportState
    data object Running : ExportState
    data class Done(val count: Int) : ExportState
    data class Error(val message: String) : ExportState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val dialerRoleManager: DialerRoleManager,
    private val contactsReader: ContactsReader,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _isDefaultDialer = MutableStateFlow(dialerRoleManager.isDefaultDialer())
    val isDefaultDialer: StateFlow<Boolean> = _isDefaultDialer.asStateFlow()

    fun refreshDialerRoleState() {
        _isDefaultDialer.value = dialerRoleManager.isDefaultDialer()
    }

    /**
     * Returns null when this build / device can't present the picker.
     * Caller launches the intent and calls [refreshDialerRoleState] on
     * the result.
     */
    fun makeAcquireDialerRoleIntent(): Intent? =
        dialerRoleManager.makeAcquireRoleIntent()

    fun setThemeMode(mode: ThemeMode) = runUpdate {
        settingsRepository.setThemeMode(mode)
    }

    fun setDynamicColorEnabled(enabled: Boolean) = runUpdate {
        settingsRepository.setDynamicColorEnabled(enabled)
    }

    fun setNotificationLeadDays(days: Int) = runUpdate {
        settingsRepository.setNotificationLeadDays(days)
    }

    fun setNotificationHour(hour: Int) = runUpdate {
        settingsRepository.setNotificationHour(hour)
        // The hour affects the schedule, so the daily job is re-enqueued.
        rescheduleReminderWork(appContext, hour)
    }

    fun setDefaultCadenceDays(days: Int) = runUpdate {
        settingsRepository.setDefaultCadenceDays(days)
    }

    fun setAppLockEnabled(enabled: Boolean) = runUpdate {
        settingsRepository.setAppLockEnabled(enabled)
    }

    fun setHideFromScreenshots(enabled: Boolean) = runUpdate {
        settingsRepository.setHideFromScreenshots(enabled)
    }

    fun setBackupNudgeIntervalDays(days: Int) = runUpdate {
        settingsRepository.setBackupNudgeIntervalDays(days)
    }

    // --- Export all contacts to a single .vcf ---

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    /**
     * Writes every system contact as a concatenated vCard 3.0 stream into
     * [destination]. The output is one valid file that any other contacts
     * app can import back. Runs off the main thread; UI watches
     * [exportState] for progress / outcome.
     */
    fun exportContactsToVcf(destination: Uri) {
        if (_exportState.value is ExportState.Running) return
        _exportState.value = ExportState.Running
        viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    val contacts = contactsReader.listContacts()
                    appContext.contentResolver.openOutputStream(destination, "wt")?.use { out ->
                        val writer = out.bufferedWriter(Charsets.UTF_8)
                        var written = 0
                        for (contact in contacts) {
                            val details = contactsReader.readDetails(contact.contactId)
                                ?: continue
                            writer.write(VCardBuilder.build(details))
                            // Each vCard's END:VCARD has no trailing newline,
                            // so add the separator between cards here.
                            writer.write("\r\n")
                            written++
                        }
                        writer.flush()
                        written
                    } ?: throw IllegalStateException("Couldn't open the destination file.")
                }
            }
            _exportState.value = outcome.fold(
                onSuccess = { ExportState.Done(it) },
                onFailure = { ExportState.Error(it.message ?: "Export failed") },
            )
        }
    }

    fun acknowledgeExportResult() {
        _exportState.value = ExportState.Idle
    }

    fun defaultExportFileName(): String {
        val date = java.time.LocalDate.now().toString()
        return "bondwidth-contacts-$date.vcf"
    }

    private fun runUpdate(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
