package com.phonepvr.friends.data.blocking

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import com.phonepvr.friends.role.DialerRoleManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps [BlockedNumberContract] so the dialer / contact surfaces can block
 * and unblock numbers without each call site having to remember the
 * permission rules. Reading and writing the blocked-numbers table requires
 * either ROLE_DIALER (which Bondwidth holds) or ROLE_SMS — without it the
 * provider throws SecurityException, so [canBlock] reflects both system
 * support AND the runtime default-dialer state.
 */
@Singleton
class BlockedNumberManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dialerRoleManager: DialerRoleManager,
) {

    /**
     * True iff the user can actually block a number from inside Bondwidth
     * right now: phones (not tablets / watches) AND we're the default
     * dialer. Surfaces / hide the Block action by this.
     */
    fun canBlock(): Boolean {
        if (!dialerRoleManager.isDefaultDialer()) return false
        return runCatching {
            BlockedNumberContract.canCurrentUserBlockNumbers(context)
        }.getOrDefault(false)
    }

    /**
     * The static [BlockedNumberContract.isBlocked] helper is API 31+ and
     * returns an int reason code, so do a manual digit-suffix match
     * against the table — works the same on minSdk 26 and matches the
     * format-insensitive comparison the rest of the app uses.
     */
    suspend fun isBlocked(number: String): Boolean = withContext(Dispatchers.IO) {
        if (number.isBlank()) return@withContext false
        val target = number.filter(Char::isDigit).takeLast(9)
        if (target.length < 4) return@withContext false
        runCatching {
            context.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val raw = cursor.getString(0) ?: continue
                    if (raw.filter(Char::isDigit).takeLast(9) == target) {
                        return@use true
                    }
                }
                false
            } ?: false
        }.getOrDefault(false)
    }

    /**
     * Inserts [number] into the system block list. Inserting a number that
     * was already blocked is a no-op as far as the platform is concerned —
     * we still return true so the UI shows a confirmation either way.
     */
    suspend fun block(number: String): Boolean = withContext(Dispatchers.IO) {
        if (number.isBlank()) return@withContext false
        runCatching {
            val values = ContentValues().apply {
                put(
                    BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
                    number,
                )
            }
            context.contentResolver.insert(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                values,
            ) != null
        }.getOrDefault(false)
    }

    /**
     * Removes [number] from the system block list. The contract's
     * [BlockedNumberContract.unblock] helper isn't safe to call below
     * API 28, so query + delete-by-_ID ourselves and match on the last 9
     * digits — the same suffix the rest of the app uses for number
     * comparison, which handles "+44 7…" vs "07…" cleanly.
     */
    suspend fun unblock(number: String): Boolean = withContext(Dispatchers.IO) {
        if (number.isBlank()) return@withContext false
        val targetDigits = number.filter(Char::isDigit).takeLast(9)
        if (targetDigits.length < 4) return@withContext false
        runCatching {
            val ids = mutableListOf<Long>()
            context.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(
                    BlockedNumberContract.BlockedNumbers.COLUMN_ID,
                    BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val raw = cursor.getString(1) ?: continue
                    val digits = raw.filter(Char::isDigit).takeLast(9)
                    if (digits == targetDigits) ids += cursor.getLong(0)
                }
            }
            var deleted = 0
            for (id in ids) {
                val uri = ContentUris.withAppendedId(
                    BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    id,
                )
                deleted += context.contentResolver.delete(uri, null, null)
            }
            deleted > 0
        }.getOrDefault(false)
    }
}
