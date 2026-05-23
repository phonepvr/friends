package com.phonepvr.friends.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.phonepvr.friends.data.db.FriendsDatabase
import com.phonepvr.friends.data.db.dao.EventDao
import com.phonepvr.friends.data.db.dao.PendingConfirmationDao
import com.phonepvr.friends.data.db.dao.PersonDao
import com.phonepvr.friends.data.db.dao.PhoneNumberDao
import com.phonepvr.friends.data.db.dao.TimelineDao
import com.phonepvr.friends.data.db.entity.EventEntity
import com.phonepvr.friends.data.db.entity.PendingConfirmationEntity
import com.phonepvr.friends.data.db.entity.PersonEntity
import com.phonepvr.friends.data.db.entity.PhoneNumberEntity
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity
import com.phonepvr.friends.data.photo.PhotoStorage
import com.phonepvr.friends.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

/** How many rows of each kind a restore wrote back, shown to the user. */
data class BackupCounts(
    val people: Int,
    val phoneNumbers: Int,
    val events: Int,
    val timelineEntries: Int,
    val pendingConfirmations: Int,
)

/** The chosen file is not a usable Friends backup. */
class InvalidBackupException(message: String) : Exception(message)

/** An encrypted backup was opened with the wrong passphrase. */
class WrongPassphraseException : Exception("The passphrase is incorrect.")

/**
 * Exports and restores the whole app as a single self-contained file.
 *
 * A backup is a ZIP holding [ENTRY_BACKUP_JSON] (every table, versioned) and a
 * [ENTRY_PHOTOS_DIR] folder with the contact photos. The ZIP may be optionally
 * sealed with a passphrase (see [BackupCrypto]). The database restore is
 * atomic — cleared and refilled inside one transaction, keeping the original
 * row ids so foreign keys stay valid on a new device. Photos live on the file
 * system, so they are restored on a best-effort basis after that transaction.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: FriendsDatabase,
    private val personDao: PersonDao,
    private val phoneNumberDao: PhoneNumberDao,
    private val eventDao: EventDao,
    private val timelineDao: TimelineDao,
    private val pendingConfirmationDao: PendingConfirmationDao,
    private val photoStorage: PhotoStorage,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Writes a backup of the whole app to [uri], encrypting it when a
     *  non-empty [passphrase] is given. */
    suspend fun export(uri: Uri, passphrase: CharArray?): Unit = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val backup = BackupFile(
            schemaVersion = BACKUP_SCHEMA_VERSION,
            exportedAt = now,
            people = personDao.getAll().map { it.toBackup() },
            phoneNumbers = phoneNumberDao.getAll().map { it.toBackup() },
            events = eventDao.getAll().map { it.toBackup() },
            timelineEntries = timelineDao.getAll().map { it.toBackup() },
            pendingConfirmations = pendingConfirmationDao.getAll().map { it.toBackup() },
            settings = settingsRepository.snapshot(),
        )
        val zipBytes = buildZip(
            jsonText = json.encodeToString(BackupFile.serializer(), backup),
            photos = photoStorage.allPhotos(),
        )
        val payload = if (passphrase != null && passphrase.isNotEmpty()) {
            BackupCrypto.encrypt(zipBytes, passphrase)
        } else {
            zipBytes
        }
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("The selected file could not be opened for writing.")
        output.use { it.write(payload) }
        // Stamp the timestamp only after the bytes are durably written, so a
        // failed write does not reset the nudge timer.
        settingsRepository.setLastSuccessfulBackupAt(now)
    }

    /** Reads the chosen file fully into memory so it can be inspected for
     *  encryption before a passphrase is requested. Backups are small. */
    suspend fun readFile(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("The selected file could not be opened.")
        input.use { it.readBytes() }
    }

    fun isEncrypted(data: ByteArray): Boolean = BackupCrypto.isEncrypted(data)

    /** Replaces the entire app contents with those of [data]. */
    suspend fun restore(data: ByteArray, passphrase: CharArray?): BackupCounts =
        withContext(Dispatchers.IO) {
            val archive = readArchive(decrypt(data, passphrase))
            val parsed = parseJson(archive.json)
            val entities = toEntities(parsed)
            database.withTransaction {
                database.clearAllTables()
                personDao.insertAll(entities.people)
                phoneNumberDao.insertAll(entities.phoneNumbers)
                eventDao.insertAll(entities.events)
                timelineDao.insertAll(entities.timelineEntries)
                pendingConfirmationDao.insertAll(entities.pendingConfirmations)
            }
            // Photos are files, not database rows; a photo failure must not
            // undo the (already committed) restore of the data itself.
            runCatching { photoStorage.replaceAll(archive.photos) }
            // Settings: v2+ backups carry a snapshot; v1 backups have null and
            // the restored device keeps whatever preferences it already had.
            parsed.settings?.let { settingsRepository.restore(it) }
            entities.counts()
        }

    private fun decrypt(data: ByteArray, passphrase: CharArray?): ByteArray {
        if (!BackupCrypto.isEncrypted(data)) return data
        val pass = passphrase
            ?: throw InvalidBackupException("This backup is encrypted.")
        return try {
            BackupCrypto.decrypt(data, pass)
        } catch (e: AEADBadTagException) {
            throw WrongPassphraseException()
        } catch (e: GeneralSecurityException) {
            throw InvalidBackupException("The encrypted backup could not be read.")
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("The encrypted backup is damaged.")
        }
    }

    private fun parseJson(jsonText: String): BackupFile {
        val backup = try {
            json.decodeFromString(BackupFile.serializer(), jsonText)
        } catch (e: Exception) {
            throw InvalidBackupException("The backup file could not be read.")
        }
        if (backup.schemaVersion > BACKUP_SCHEMA_VERSION) {
            throw InvalidBackupException(
                "This backup was created by a newer version of Friends.",
            )
        }
        return backup
    }

    private fun toEntities(backup: BackupFile): RestoreEntities = try {
        RestoreEntities(
            people = backup.people.map { it.toEntity() },
            phoneNumbers = backup.phoneNumbers.map { it.toEntity() },
            events = backup.events.map { it.toEntity() },
            timelineEntries = backup.timelineEntries.map { it.toEntity() },
            pendingConfirmations = backup.pendingConfirmations.map { it.toEntity() },
        )
    } catch (e: IllegalArgumentException) {
        throw InvalidBackupException("The backup contains an unrecognised value.")
    }

    private fun buildZip(jsonText: String, photos: List<File>): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_BACKUP_JSON))
            zip.write(jsonText.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(ENTRY_PHOTOS_DIR))
            zip.closeEntry()
            photos.forEach { photo ->
                zip.putNextEntry(ZipEntry("$ENTRY_PHOTOS_DIR${photo.name}"))
                photo.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return buffer.toByteArray()
    }

    private fun readArchive(zipBytes: ByteArray): Archive {
        var jsonText: String? = null
        val photos = mutableMapOf<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory) {
                        val bytes = zip.readBytes()
                        when {
                            name == ENTRY_BACKUP_JSON -> jsonText = bytes.toString(Charsets.UTF_8)
                            isSafePhotoEntry(name) -> photos[name] = bytes
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: ZipException) {
            throw InvalidBackupException("The backup file is not a valid archive.")
        } catch (e: IOException) {
            throw InvalidBackupException("The backup file could not be read.")
        }
        return Archive(
            json = jsonText ?: throw InvalidBackupException(
                "The backup is missing its data file.",
            ),
            photos = photos,
        )
    }

    /** Guards against ZIP path traversal: a photo must be a plain file name. */
    private fun isSafePhotoEntry(name: String): Boolean =
        name.startsWith(ENTRY_PHOTOS_DIR) &&
            name.substring(ENTRY_PHOTOS_DIR.length).matches(SAFE_PHOTO_NAME)

    private class Archive(
        val json: String,
        val photos: Map<String, ByteArray>,
    )

    private class RestoreEntities(
        val people: List<PersonEntity>,
        val phoneNumbers: List<PhoneNumberEntity>,
        val events: List<EventEntity>,
        val timelineEntries: List<TimelineEntryEntity>,
        val pendingConfirmations: List<PendingConfirmationEntity>,
    ) {
        fun counts(): BackupCounts = BackupCounts(
            people = people.size,
            phoneNumbers = phoneNumbers.size,
            events = events.size,
            timelineEntries = timelineEntries.size,
            pendingConfirmations = pendingConfirmations.size,
        )
    }

    private companion object {
        const val ENTRY_BACKUP_JSON = "backup.json"
        const val ENTRY_PHOTOS_DIR = "photos/"
        val SAFE_PHOTO_NAME = Regex("[A-Za-z0-9-]+\\.jpg")
    }
}
