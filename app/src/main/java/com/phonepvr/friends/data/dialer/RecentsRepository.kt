package com.phonepvr.friends.data.dialer

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
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
 * No permission check here — [CallLogReader.recentCalls] handles a missing
 * READ_CALL_LOG gracefully (the resolver query returns null → empty list).
 * The dialer screen guards with its own permission prompt before
 * subscribing.
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
                trySend(reader.recentCalls(sinceMillis))
            }
        }
        runCatching {
            resolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        }
        trySend(reader.recentCalls(sinceMillis))
        awaitClose { runCatching { resolver.unregisterContentObserver(observer) } }
    }.flowOn(Dispatchers.IO)
}
