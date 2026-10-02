package com.phonepvr.friends.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.backup.AutoBackupFiles
import com.phonepvr.friends.data.backup.AutoBackupFrequency
import com.phonepvr.friends.data.backup.AutoBackupRunner
import com.phonepvr.friends.data.backup.BackupCounts
import com.phonepvr.friends.data.backup.BackupManager
import com.phonepvr.friends.data.backup.InvalidBackupException
import com.phonepvr.friends.data.backup.WrongPassphraseException
import com.phonepvr.friends.data.settings.SettingsRepository
import com.phonepvr.friends.work.cancelAutoBackupWork
import com.phonepvr.friends.work.scheduleAutoBackupWork
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Outcome of the most recent export or restore, shown on the screen. */
sealed interface BackupResult {
    data object Exported : BackupResult
    data class Restored(val counts: BackupCounts) : BackupResult
    data class Failed(val message: String) : BackupResult
}

/** What the Backup screen shows about the scheduled (automatic) backup. */
data class AutoBackupUiState(
    val enabled: Boolean = false,
    /** Readable name of the chosen folder, or null if none is chosen. */
    val folderLabel: String? = null,
    val frequency: AutoBackupFrequency = AutoBackupFrequency.DEFAULT,
    val keep: Int = AutoBackupFiles.DEFAULT_KEEP,
    /** Epoch ms of the last successful backup of any kind. */
    val lastSuccessAt: Long? = null,
    /** Epoch ms of the last scheduled attempt, successful or not. */
    val lastAttemptAt: Long? = null,
    /** Why the last scheduled attempt failed; null if it succeeded or hasn't run. */
    val lastError: String? = null,
    /** True while "Back up now" is running. */
    val running: Boolean = false,
)

data class BackupUiState(
    val busy: Boolean = false,
    /** True while a picked file is encrypted and a passphrase is needed. */
    val awaitingPassphrase: Boolean = false,
    val passphraseError: String? = null,
    val result: BackupResult? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager,
    private val settingsRepository: SettingsRepository,
    private val autoBackupRunner: AutoBackupRunner,
) : ViewModel() {

    private val autoBackupRunning = MutableStateFlow(false)

    val autoBackup: StateFlow<AutoBackupUiState> =
        combine(settingsRepository.settings, autoBackupRunning) { s, running ->
            AutoBackupUiState(
                enabled = s.autoBackupEnabled,
                folderLabel = s.autoBackupFolderUri?.let(::folderLabelOf),
                frequency = s.autoBackupFrequency,
                keep = s.autoBackupKeep,
                lastSuccessAt = s.lastSuccessfulBackupAt,
                lastAttemptAt = s.autoBackupLastRunAt,
                lastError = s.autoBackupLastError,
                running = running,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoBackupUiState())

    /**
     * The user picked a backup folder. Keep the grant across restarts (without it a
     * scheduled write would fail), drop the previous folder's grant so the system's
     * limited pool of persisted grants isn't used up, and turn the schedule on: the
     * switch is what led to the picker.
     */
    fun onFolderPicked(uri: Uri) {
        viewModelScope.launch {
            val resolver = context.contentResolver
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val taken = runCatching { resolver.takePersistableUriPermission(uri, flags) }.isSuccess
            if (!taken) return@launch
            val previous = settingsRepository.settings.first().autoBackupFolderUri
            if (previous != null && previous != uri.toString()) {
                runCatching { resolver.releasePersistableUriPermission(Uri.parse(previous), flags) }
            }
            settingsRepository.setAutoBackupFolderUri(uri.toString())
            settingsRepository.setAutoBackupEnabled(true)
            scheduleAutoBackupWork(context, settingsRepository.settings.first().autoBackupFrequency, replace = true)
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoBackupEnabled(enabled)
            if (enabled) {
                scheduleAutoBackupWork(context, settingsRepository.settings.first().autoBackupFrequency, replace = true)
            } else {
                cancelAutoBackupWork(context)
            }
        }
    }

    fun setAutoBackupFrequency(frequency: AutoBackupFrequency) {
        viewModelScope.launch {
            settingsRepository.setAutoBackupFrequency(frequency)
            if (settingsRepository.settings.first().autoBackupEnabled) {
                scheduleAutoBackupWork(context, frequency, replace = true)
            }
        }
    }

    fun setAutoBackupKeep(keep: Int) {
        viewModelScope.launch { settingsRepository.setAutoBackupKeep(keep) }
    }

    /** Runs the scheduled backup right now, so the user can confirm the folder works. */
    fun backUpNow() {
        if (autoBackupRunning.value) return
        autoBackupRunning.value = true
        viewModelScope.launch {
            try {
                autoBackupRunner.runOnce()
            } finally {
                autoBackupRunning.value = false
            }
        }
    }

    private fun folderLabelOf(uri: String): String = runCatching {
        AutoBackupFiles.folderLabel(DocumentsContract.getTreeDocumentId(Uri.parse(uri)))
    }.getOrDefault(uri)

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** Holds the bytes of an encrypted file between picking it and the user
     *  entering a passphrase, so the file is read only once. */
    private var pendingEncryptedFile: ByteArray? = null

    fun export(uri: Uri, passphrase: String) {
        if (_state.value.busy) return
        _state.value = BackupUiState(busy = true)
        viewModelScope.launch {
            val pass = passphrase.takeIf { it.isNotEmpty() }?.toCharArray()
            try {
                backupManager.export(uri, pass)
                _state.value = BackupUiState(result = BackupResult.Exported)
            } catch (e: Exception) {
                _state.value = BackupUiState(
                    result = BackupResult.Failed(
                        "The backup could not be saved. Please try again.",
                    ),
                )
            } finally {
                pass?.fill(' ')
            }
        }
    }

    fun onFilePicked(uri: Uri) {
        if (_state.value.busy) return
        _state.value = BackupUiState(busy = true)
        viewModelScope.launch {
            try {
                val bytes = backupManager.readFile(uri)
                if (backupManager.isEncrypted(bytes)) {
                    pendingEncryptedFile = bytes
                    _state.value = BackupUiState(awaitingPassphrase = true)
                } else {
                    val counts = backupManager.restore(bytes, null)
                    _state.value = BackupUiState(result = BackupResult.Restored(counts))
                }
            } catch (e: InvalidBackupException) {
                _state.value = failure(e.message)
            } catch (e: Exception) {
                _state.value = failure(null)
            }
        }
    }

    fun submitPassphrase(passphrase: String) {
        val bytes = pendingEncryptedFile ?: return
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, passphraseError = null)
        viewModelScope.launch {
            val pass = passphrase.toCharArray()
            try {
                val counts = backupManager.restore(bytes, pass)
                pendingEncryptedFile = null
                _state.value = BackupUiState(result = BackupResult.Restored(counts))
            } catch (e: WrongPassphraseException) {
                _state.value = _state.value.copy(
                    busy = false,
                    passphraseError = "Incorrect passphrase. Please try again.",
                )
            } catch (e: InvalidBackupException) {
                pendingEncryptedFile = null
                _state.value = failure(e.message)
            } catch (e: Exception) {
                pendingEncryptedFile = null
                _state.value = failure(null)
            } finally {
                pass.fill(' ')
            }
        }
    }

    fun cancelPassphrase() {
        pendingEncryptedFile = null
        _state.value = BackupUiState()
    }

    fun clearResult() {
        _state.value = BackupUiState()
    }

    private fun failure(message: String?): BackupUiState = BackupUiState(
        result = BackupResult.Failed(
            message ?: "The backup could not be restored. Please try again.",
        ),
    )
}
