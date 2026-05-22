package com.phonepvr.friends.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonepvr.friends.data.backup.BackupCounts
import com.phonepvr.friends.data.backup.BackupManager
import com.phonepvr.friends.data.backup.InvalidBackupException
import com.phonepvr.friends.data.backup.WrongPassphraseException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Outcome of the most recent export or restore, shown on the screen. */
sealed interface BackupResult {
    data object Exported : BackupResult
    data class Restored(val counts: BackupCounts) : BackupResult
    data class Failed(val message: String) : BackupResult
}

data class BackupUiState(
    val busy: Boolean = false,
    /** True while a picked file is encrypted and a passphrase is needed. */
    val awaitingPassphrase: Boolean = false,
    val passphraseError: String? = null,
    val result: BackupResult? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
) : ViewModel() {

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
