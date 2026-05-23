package com.phonepvr.friends.data.calllog

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.phonepvr.friends.data.repository.CallLogAutoSync
import com.phonepvr.friends.widget.refreshUpcomingWidgets
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches the system call log and re-runs [CallLogAutoSync.syncAllPeople]
 * any time it changes, so calls placed while the process is alive land in
 * the timeline (and the widget) within a couple of seconds instead of
 * waiting for the next cold launch.
 *
 * The CallLog provider fires onChange multiple times per call (every
 * status update during the call, not just on row insert), so the
 * notifications are funneled through a debounced [MutableSharedFlow]
 * that only runs the suspend body once the dust settles.
 *
 * No-op when [Manifest.permission.READ_CALL_LOG] is not granted:
 * registering an observer against `CallLog.Calls` throws
 * `SecurityException` on some OEMs without the permission. The sync
 * itself is also a safe no-op without the permission via
 * `CallLogRepository.scanForPerson` swallowing the same exception.
 */
@Singleton
class CallLogChangeObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callLogAutoSync: CallLogAutoSync,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var observer: ContentObserver? = null
    private val triggers = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        if (observer != null) return
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CALL_LOG,
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        triggers
            .debounce(1_000)
            .onEach {
                runCatching { callLogAutoSync.syncAllPeople() }
                // Belt-and-braces: WidgetRefreshObserver already redraws on
                // the timeline write, but force a refresh here too in case
                // the observer is somehow stuck.
                runCatching { refreshUpcomingWidgets(context) }
            }
            .flowOn(Dispatchers.IO)
            .launchIn(scope)
        val obs = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                triggers.tryEmit(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI, true, obs,
        )
        observer = obs
    }

    fun stop() {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
    }
}
