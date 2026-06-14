package com.phonepvr.friends.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.calllog.CallHistoryCsv
import com.phonepvr.friends.data.calllog.CallLogReader
import com.phonepvr.friends.data.contacts.ContactsReader
import com.phonepvr.friends.data.contacts.VCardBuilder
import com.phonepvr.friends.data.contacts.VCardImporter
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

/** Lifecycle of the "import contacts from vCard" flow. */
sealed interface ImportState {
    data object Idle : ImportState
    data object Reading : ImportState
    data class Importing(val done: Int, val total: Int) : ImportState
    data class Done(val imported: Int, val skipped: Int) : ImportState
    data class Error(val message: String) : ImportState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val dialerRoleManager: DialerRoleManager,
    private val contactsReader: ContactsReader,
    private val vCardImporter: VCardImporter,
    private val callLogReader: CallLogReader,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _isDefaultDialer = MutableStateFlow(dialerRoleManager.isDefaultDialer())
    val isDefaultDialer: StateFlow<Boolean> = _isDefaultDialer.asStateFlow()

    /**
     * Re-read after returning from the role picker. Returns the fresh state
     * so the caller can guide recovery when the request didn't take (the
     * Android 13+ restricted-settings block on sideloaded apps).
     */
    fun refreshDialerRoleState(): Boolean {
        val isDefault = dialerRoleManager.isDefaultDialer()
        _isDefaultDialer.value = isDefault
        return isDefault
    }

    /**
     * Returns null when this build / device can't present the picker.
     * Caller launches the intent and calls [refreshDialerRoleState] on
     * the result.
     */
    fun makeAcquireDialerRoleIntent(): Intent? =
        dialerRoleManager.makeAcquireRoleIntent()

    /** Direct link to system Settings → Apps → Default apps. */
    fun makeDefaultAppsSettingsIntent(): Intent =
        dialerRoleManager.makeDefaultAppsSettingsIntent()

    /** Link to this app's "App info" page for the restricted-settings toggle. */
    fun makeAppInfoIntent(): Intent =
        dialerRoleManager.makeAppInfoIntent()

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

    // --- Export call history to CSV ---

    private val _callHistoryExportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val callHistoryExportState: StateFlow<ExportState> = _callHistoryExportState.asStateFlow()

    /**
     * Writes the device call log to [destination] as CSV (see [CallHistoryCsv]).
     * Needs READ_CALL_LOG, which the caller requests before launching the file
     * picker; a missing permission surfaces as an error rather than a crash.
     */
    fun exportCallHistory(destination: Uri) {
        if (_callHistoryExportState.value is ExportState.Running) return
        _callHistoryExportState.value = ExportState.Running
        viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    val calls = try {
                        callLogReader.allCalls()
                    } catch (e: SecurityException) {
                        throw IllegalStateException("Call-log access is needed to export.")
                    }
                    appContext.contentResolver.openOutputStream(destination, "wt")?.use { out ->
                        out.bufferedWriter(Charsets.UTF_8).use { it.write(CallHistoryCsv.toCsv(calls)) }
                    } ?: throw IllegalStateException("Couldn't open the destination file.")
                    calls.size
                }
            }
            _callHistoryExportState.value = outcome.fold(
                onSuccess = { ExportState.Done(it) },
                onFailure = { ExportState.Error(it.message ?: "Export failed") },
            )
        }
    }

    fun acknowledgeCallHistoryExportResult() {
        _callHistoryExportState.value = ExportState.Idle
    }

    fun defaultCallHistoryFileName(): String =
        "bondwidth-call-history-${java.time.LocalDate.now()}.csv"

    // --- Import contacts from a vCard file ---

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    /**
     * Reads [source] as a vCard 3.0 stream and inserts each card as a new
     * system contact via [ContactWriter]. Cards without a usable display
     * name are skipped. Auto-tracking is deliberately not applied — a bulk
     * import of 500 contacts shouldn't drown the Bonds list.
     */
    fun importContactsFromVcf(source: Uri) {
        if (_importState.value !is ImportState.Idle) return
        _importState.value = ImportState.Reading
        viewModelScope.launch {
            val outcome = runCatching {
                vCardImporter.importFrom(source) { index, total ->
                    _importState.value = ImportState.Importing(index, total)
                }
            }
            _importState.value = outcome.fold(
                onSuccess = { ImportState.Done(imported = it.imported, skipped = it.skipped) },
                onFailure = { ImportState.Error(it.message ?: "Import failed") },
            )
        }
    }

    fun acknowledgeImportResult() {
        _importState.value = ImportState.Idle
    }

    private fun runUpdate(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
