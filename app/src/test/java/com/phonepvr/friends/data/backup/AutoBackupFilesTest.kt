package com.phonepvr.friends.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class AutoBackupFilesTest {

    private fun name(y: Int, mo: Int, d: Int, h: Int = 12, mi: Int = 0, s: Int = 0) =
        AutoBackupFiles.fileName(LocalDateTime.of(y, mo, d, h, mi, s))

    @Test
    fun fileNamesAreStableAndSortByTime() {
        assertEquals("Bondwidth-backup-20261002-084500.zip", name(2026, 10, 2, 8, 45, 0))
        assertTrue(name(2026, 1, 9) < name(2026, 1, 10))
        assertTrue(name(2025, 12, 31, 23, 59, 59) < name(2026, 1, 1, 0, 0, 0))
    }

    @Test
    fun onlyOurOwnFilesAreRecognised() {
        assertTrue(AutoBackupFiles.isOurs(name(2026, 10, 2)))
        assertFalse(AutoBackupFiles.isOurs("Bondwidth-backup-notes.zip"))
        assertFalse(AutoBackupFiles.isOurs("holiday-photos.zip"))
        assertFalse(AutoBackupFiles.isOurs("Bondwidth-backup-20261002-084500.zip.tmp"))
        assertFalse(AutoBackupFiles.isOurs("Bondwidth-backup-20261002-084500.zip (1)"))
    }

    @Test
    fun keepsTheNewestAndDeletesTheRest() {
        val files = (1..7).map { name(2026, 3, it) }.shuffled()
        val doomed = AutoBackupFiles.toDelete(files, keep = 5)
        assertEquals(setOf(name(2026, 3, 1), name(2026, 3, 2)), doomed.toSet())
        assertEquals(2, doomed.size)
    }

    @Test
    fun neverDeletesFilesItDidNotWrite() {
        val ours = (1..4).map { name(2026, 4, it) }
        val others = listOf("holiday-photos.zip", "Bondwidth-backup-manual.zip", "notes.txt")
        val doomed = AutoBackupFiles.toDelete(ours + others, keep = 2)
        assertEquals(setOf(name(2026, 4, 1), name(2026, 4, 2)), doomed.toSet())
        assertTrue(doomed.none { it in others })
    }

    @Test
    fun nothingIsDeletedWhenUnderTheLimit() {
        assertTrue(AutoBackupFiles.toDelete(listOf(name(2026, 5, 1)), keep = 5).isEmpty())
        assertTrue(AutoBackupFiles.toDelete(emptyList(), keep = 5).isEmpty())
    }

    @Test
    fun keepIsClampedSoTheNewestBackupIsNeverDeleted() {
        val files = (1..3).map { name(2026, 6, it) }
        // keep = 0 (or negative) must still keep one: pruning can never leave the user with none.
        assertEquals(2, AutoBackupFiles.toDelete(files, keep = 0).size)
        assertEquals(2, AutoBackupFiles.toDelete(files, keep = -4).size)
        assertTrue(name(2026, 6, 3) !in AutoBackupFiles.toDelete(files, keep = 0))
        // And an absurd keep just keeps everything.
        assertTrue(AutoBackupFiles.toDelete(files, keep = 999).isEmpty())
    }

    @Test
    fun frequencyParsingFallsBackToWeekly() {
        assertEquals(AutoBackupFrequency.DAILY, AutoBackupFrequency.fromName("DAILY"))
        assertEquals(AutoBackupFrequency.MONTHLY, AutoBackupFrequency.fromName("MONTHLY"))
        assertEquals(AutoBackupFrequency.WEEKLY, AutoBackupFrequency.fromName(null))
        assertEquals(AutoBackupFrequency.WEEKLY, AutoBackupFrequency.fromName("hourly"))
        assertEquals(7, AutoBackupFrequency.DEFAULT.days)
    }

    @Test
    fun folderLabelsAreReadable() {
        assertEquals("Documents/Backups", AutoBackupFiles.folderLabel("primary:Documents/Backups"))
        assertEquals("Backups", AutoBackupFiles.folderLabel("1A2B-3C4D:Backups"))
        assertEquals("a/b", AutoBackupFiles.folderLabel("primary:/a/b/"))
        // No path part (e.g. the root of a volume): show the id rather than nothing.
        assertEquals("primary:", AutoBackupFiles.folderLabel("primary:"))
        assertEquals("opaque-id", AutoBackupFiles.folderLabel("opaque-id"))
    }
}
