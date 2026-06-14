package com.phonepvr.friends.data.calllog

import android.content.ContentValues
import android.content.Context
import android.provider.CallLog
import com.phonepvr.friends.domain.model.CallType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a call-history import: rows inserted vs skipped (already present). */
data class CallImportResult(val imported: Int, val skipped: Int)

/**
 * Mutates the system call log. Needs WRITE_CALL_LOG, which Bondwidth holds as
 * the default phone app. Each operation catches SecurityException so a missing
 * permission yields a harmless no-op result instead of crashing.
 */
@Singleton
class CallLogWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callLogReader: CallLogReader,
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

    /**
     * Inserts [calls] into the system call log, skipping any whose
     * (timestamp, number) already exists so re-importing the same file doesn't
     * duplicate entries. Returns how many were inserted vs skipped.
     */
    suspend fun importCalls(calls: List<DeviceCall>): CallImportResult =
        withContext(Dispatchers.IO) {
            if (calls.isEmpty()) return@withContext CallImportResult(0, 0)
            // Pre-existing keys (best effort — empty if READ_CALL_LOG is denied).
            val seen = runCatching {
                callLogReader.allCalls()
                    .mapTo(HashSet()) { dedupKey(it.timestampMillis, it.number) }
            }.getOrDefault(HashSet())
            var imported = 0
            var skipped = 0
            for (call in calls) {
                val key = dedupKey(call.timestampMillis, call.number)
                if (key in seen) {
                    skipped++
                    continue
                }
                val ok = runCatching {
                    val values = ContentValues().apply {
                        put(CallLog.Calls.NUMBER, call.number)
                        put(CallLog.Calls.TYPE, callLogType(call.type))
                        put(CallLog.Calls.DATE, call.timestampMillis)
                        put(CallLog.Calls.DURATION, call.durationSeconds)
                    }
                    context.contentResolver.insert(CallLog.Calls.CONTENT_URI, values) != null
                }.getOrDefault(false)
                if (ok) {
                    imported++
                    seen.add(key)
                } else {
                    skipped++
                }
            }
            CallImportResult(imported, skipped)
        }

    private fun dedupKey(date: Long, number: String): String =
        "$date|${number.filter { !it.isWhitespace() }}"

    private fun callLogType(type: CallType): Int = when (type) {
        CallType.INCOMING -> CallLog.Calls.INCOMING_TYPE
        CallType.OUTGOING -> CallLog.Calls.OUTGOING_TYPE
        CallType.MISSED -> CallLog.Calls.MISSED_TYPE
        CallType.REJECTED -> CallLog.Calls.REJECTED_TYPE
    }
}
