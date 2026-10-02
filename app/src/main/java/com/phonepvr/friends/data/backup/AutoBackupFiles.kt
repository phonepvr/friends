package com.phonepvr.friends.data.backup

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Naming and pruning rules for the files the scheduled backup writes into the
 * folder the user picked. Pure logic, so it can be tested without a device.
 *
 * Files are named `Bondwidth-backup-yyyyMMdd-HHmmss.zip`. Sealed and unsealed
 * backups share that name because the backup format carries its own encryption
 * marker. The timestamp in the name is what retention sorts on — never the file's
 * modified time, which some storage providers rewrite on sync.
 */
object AutoBackupFiles {

    const val PREFIX = "Bondwidth-backup-"
    const val EXTENSION = ".zip"
    const val MIME_TYPE = "application/zip"

    /** The most backups the user can ask to keep, and the least. */
    const val MIN_KEEP = 1
    const val MAX_KEEP = 30
    const val DEFAULT_KEEP = 5

    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val pattern = Regex("^${Regex.escape(PREFIX)}(\\d{8}-\\d{6})${Regex.escape(EXTENSION)}$")

    fun fileName(at: LocalDateTime): String = PREFIX + stamp.format(at) + EXTENSION

    /**
     * A readable name for the picked folder, from its SAF tree document id
     * (`primary:Documents/Backups` becomes `Documents/Backups`; an SD card or cloud
     * provider id such as `1A2B-3C4D:Backups` becomes `Backups`). Falls back to the
     * whole id when there is no path part, so something is always shown.
     */
    fun folderLabel(treeDocumentId: String): String {
        val path = treeDocumentId.substringAfter(':', missingDelimiterValue = treeDocumentId)
        return path.trim('/').ifBlank { treeDocumentId }
    }

    /** True for files this feature wrote, so pruning never touches anything else in the folder. */
    fun isOurs(name: String): Boolean = pattern.matches(name)

    /**
     * Which of [names] to delete so that only the newest [keep] of *our* backups remain.
     * Files that don't match our naming are never returned, whatever their age.
     */
    fun toDelete(names: List<String>, keep: Int): List<String> {
        val limit = keep.coerceIn(MIN_KEEP, MAX_KEEP)
        return names
            .filter(::isOurs)
            .sortedDescending() // the timestamp sorts lexicographically, newest first
            .drop(limit)
    }
}

/** How often the scheduled backup runs. */
enum class AutoBackupFrequency(val days: Int) {
    DAILY(1),
    WEEKLY(7),
    MONTHLY(30),
    ;

    companion object {
        val DEFAULT = WEEKLY

        fun fromName(name: String?): AutoBackupFrequency =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
