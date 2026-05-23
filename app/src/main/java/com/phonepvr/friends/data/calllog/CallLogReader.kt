package com.phonepvr.friends.data.calllog

import android.content.Context
import android.provider.CallLog
import com.phonepvr.friends.domain.model.CallType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceCall(
    val number: String,
    val type: CallType,
    val timestampMillis: Long,
    /** Call length in seconds; 0 for missed/rejected. */
    val durationSeconds: Long,
)

/** Read-only access to the device call log via the CallLog content provider. */
@Singleton
class CallLogReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun recentCalls(sinceMillis: Long): List<DeviceCall> {
        val calls = mutableListOf<DeviceCall>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
            ),
            "${CallLog.Calls.DATE} >= ?",
            arrayOf(sinceMillis.toString()),
            "${CallLog.Calls.DATE} DESC",
        )?.use { cursor ->
            val numberColumn = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val typeColumn = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateColumn = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durationColumn = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (cursor.moveToNext()) {
                val type = mapCallType(cursor.getInt(typeColumn)) ?: continue
                calls.add(
                    DeviceCall(
                        number = cursor.getString(numberColumn).orEmpty(),
                        type = type,
                        timestampMillis = cursor.getLong(dateColumn),
                        durationSeconds = cursor.getLong(durationColumn).coerceAtLeast(0L),
                    ),
                )
            }
        }
        return calls
    }

    private fun mapCallType(value: Int): CallType? = when (value) {
        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
        else -> null
    }
}
