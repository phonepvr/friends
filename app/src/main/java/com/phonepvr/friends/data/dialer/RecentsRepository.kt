package com.phonepvr.friends.data.dialer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.phonepvr.friends.data.calllog.CallLogReader
import com.phonepvr.friends.data.calllog.DeviceCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flow wrapper around [CallLogReader] for the dialer's Recents segment.
 * Emits whenever the system CallLog changes so the list never goes stale
 * after an outgoing or incoming call.
 *
 * Without READ_CALL_LOG it emits an empty list instead of failing: on a device
 * querying the call log without that permission throws SecurityException (it does
 * not return null), and not every caller checks first — the call-history screen
 * opened from a missed-call notification, for one.
 */
@Singleton
class RecentsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reader: CallLogReader,
) {
    fun observe(sinceMillis: Long): Flow<List<DeviceCall>> = callbackFlow {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(recentCallsOrEmpty(sinceMillis))
            }
        }
        if (hasCallLogPermission()) {
            runCatching {
                resolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
            }
        }
        trySend(recentCallsOrEmpty(sinceMillis))
        awaitClose { runCatching { resolver.unregisterContentObserver(observer) } }
    }.flowOn(Dispatchers.IO)

    private fun hasCallLogPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    private fun recentCallsOrEmpty(sinceMillis: Long): List<DeviceCall> {
        if (!hasCallLogPermission()) return emptyList()
        return try {
            reader.recentCalls(sinceMillis, includeBlocked = true)
        } catch (e: SecurityException) {
            emptyList()
        }
    }
}
