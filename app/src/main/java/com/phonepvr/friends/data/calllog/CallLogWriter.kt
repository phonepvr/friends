package com.phonepvr.friends.data.calllog

import android.content.Context
import android.provider.CallLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mutates the system call log. Needs WRITE_CALL_LOG, which Bondwidth holds as
 * the default phone app. Each operation catches SecurityException so a missing
 * permission yields a harmless no-op result instead of crashing.
 */
@Singleton
class CallLogWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Deletes every entry from the device call log; returns the row count removed. */
    suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
        }.getOrDefault(0)
    }

    /** Deletes the single call-log entry with provider [id]. */
    suspend fun deleteCall(id: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} = ?",
                arrayOf(id.toString()),
            ) > 0
        }.getOrDefault(false)
    }
}
