package com.phonepvr.friends.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.phonepvr.friends.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes one scheduled backup into the folder the user picked (via the system
 * folder picker, so Bondwidth never needs broad storage access and never touches
 * the network), then deletes the oldest of its own backups beyond the keep limit.
 *
 * The result — success time, or why it failed — is stored in settings so the
 * Backup screen can show it. A failure never throws and never retries on its own:
 * a broken folder would otherwise be hammered forever.
 */
@Singleton
class AutoBackupRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager,
) {
    sealed interface Outcome {
        data class Saved(val fileName: String) : Outcome
        data class Failed(val reason: String) : Outcome
        data object Disabled : Outcome
    }

    suspend fun runOnce(): Outcome = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settings.first()
        if (!settings.autoBackupEnabled) return@withContext Outcome.Disabled
        val outcome = try {
            write(settings.autoBackupFolderUri, settings.autoBackupKeep)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.Failed("The backup could not be written. ${e.message.orEmpty()}".trim())
        }
        settingsRepository.recordAutoBackupAttempt(
            atMillis = System.currentTimeMillis(),
            error = (outcome as? Outcome.Failed)?.reason,
        )
        outcome
    }

    private suspend fun write(folder: String?, keep: Int): Outcome {
        if (folder.isNullOrBlank()) return Outcome.Failed("No backup folder is chosen.")
        val tree = Uri.parse(folder)
        val resolver = context.contentResolver
        // The system folder picker's grant must have been persisted; if the user
        // revoked it, or the folder's provider forgot it, writing would just throw.
        val writable = resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        if (!writable) {
            return Outcome.Failed("Bondwidth no longer has access to the backup folder. Choose it again.")
        }
        val treeDocument = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        val name = AutoBackupFiles.fileName(LocalDateTime.now())
        val file = DocumentsContract.createDocument(resolver, treeDocument, AutoBackupFiles.MIME_TYPE, name)
            ?: return Outcome.Failed("The backup folder doesn't accept new files.")
        try {
            backupManager.export(file, passphrase = null)
        } catch (e: Exception) {
            // Don't leave a half-written backup behind to be mistaken for a good one.
            runCatching { DocumentsContract.deleteDocument(resolver, file) }
            throw e
        }
        // Pruning is housekeeping: a failure here must not turn a good backup into an error.
        runCatching { prune(tree, keep) }
        return Outcome.Saved(name)
    }

    private fun prune(tree: Uri, keep: Int) {
        val resolver = context.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val idByName = HashMap<String, String>()
        resolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId),
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) idByName[cursor.getString(1).orEmpty()] = cursor.getString(0)
        }
        for (name in AutoBackupFiles.toDelete(idByName.keys.toList(), keep)) {
            val id = idByName[name] ?: continue
            runCatching {
                DocumentsContract.deleteDocument(
                    resolver,
                    DocumentsContract.buildDocumentUriUsingTree(tree, id),
                )
            }
        }
    }
}
