package com.phonepvr.friends.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.phonepvr.friends.data.incall.CallDirection
import com.phonepvr.friends.data.incall.CallSession
import com.phonepvr.friends.data.incall.CallSimpleState
import com.phonepvr.friends.data.incall.CallerIdentityResolver
import com.phonepvr.friends.data.incall.Ringer
import com.phonepvr.friends.ui.incall.InCallActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Bondwidth's in-call service implementation. Bound by the platform's
 * Telecom framework whenever Bondwidth holds ROLE_DIALER and a call is
 * present (incoming, outgoing, or ongoing). Funnels every Call lifecycle
 * event into [CallSession], which the in-call Activity collects to
 * render the Compose UI, and posts the ongoing-call notification so the
 * call is reachable from anywhere.
 *
 * Runs as a foreground service with type=phoneCall while at least one
 * call exists. That's what makes the incoming-call full-screen intent
 * reliably fire on idle / locked devices and on aggressive OEMs (Xiaomi
 * HyperOS, Samsung One UI) that otherwise drop call notifications.
 *
 * Multi-call lifecycle: CallSession is the source of truth for the list
 * of live calls. This service is only responsible for forwarding Telecom
 * events (onCallAdded / onCallRemoved / Call.Callback per entry) and for
 * the foreground-service bookkeeping (start on first call, stop when the
 * last call is gone).
 */
@AndroidEntryPoint
class BondwidthInCallService : InCallService() {

    @Inject lateinit var callSession: CallSession
    @Inject lateinit var callNotifier: CallNotifier
    @Inject lateinit var callerIdentityResolver: CallerIdentityResolver
    @Inject lateinit var ringer: Ringer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var inForeground = false

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            callSession.publishSnapshot()
            refreshNotification()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            callSession.publishSnapshot()
            refreshNotification()
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        callSession.attachService(this)
        callSession.attachCall(call)
        call.registerCallback(callCallback)
        ensureForeground()
        refreshNotification()
        // FOREGROUND_SERVICE_TYPE_PHONE_CALL exempts us from the Android 14+
        // background-activity-launch restrictions, so launching the in-call
        // UI directly works for both outgoing AND incoming-ring states.
        // The notification's full-screen intent stays as a backup path for
        // OEMs that still drop the direct launch.
        launchInCallUi()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        callSession.detachCall(call)
        if (!callSession.hasActiveCall()) {
            ringer.stop()
            if (inForeground) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
                inForeground = false
            }
            callNotifier.cancel()
        } else {
            refreshNotification()
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        callSession.publishAudio(audioState)
        // Re-post so the notification's audio button reflects the new route.
        refreshNotification()
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        if (callSession.hasActiveCall()) launchInCallUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        ringer.stop()
        callSession.detachService(this)
        callNotifier.cancel()
        scope.cancel()
    }

    /**
     * Promotes the service to foreground with a best-effort initial
     * notification, synchronously. The caller name will likely still be
     * just the raw number at this point — [refreshNotification] re-posts
     * with the resolved name a beat later. Synchronous is the point: the
     * platform requires startForeground within ~5s of a call arriving, and
     * the async caller lookup can't be in that critical path.
     */
    private fun ensureForeground() {
        if (inForeground) return
        val snapshot = callSession.snapshot.value ?: return
        val initialName = snapshot.callerDisplayName
            ?: snapshot.number.takeIf { it.isNotBlank() }
            ?: "Call"
        val notification = callNotifier.build(snapshot, initialName, callSession.audio.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                CallNotifier.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
            )
        } else {
            startForeground(CallNotifier.NOTIFICATION_ID, notification)
        }
        inForeground = true
    }

    private fun refreshNotification() {
        val snapshot = callSession.snapshot.value
        if (snapshot == null || snapshot.state == CallSimpleState.DISCONNECTED) {
            return
        }
        // Drive the ringtone + vibration from the state machine. Re-entering
        // start() while already playing is a no-op, so it's safe to call on
        // every state-change re-post.
        val isIncomingRing = snapshot.state == CallSimpleState.RINGING &&
            snapshot.direction == CallDirection.INCOMING
        if (isIncomingRing) ringer.start() else ringer.stop()

        // Resolve the caller's name off the main thread, then re-post.
        scope.launch {
            val identity = callerIdentityResolver.resolve(snapshot.number)
            val name = identity.displayName
                ?: snapshot.callerDisplayName
                ?: snapshot.number.ifBlank { "Unknown" }
            val notification = callNotifier.build(snapshot, name, callSession.audio.value)
            callNotifier.post(notification)
        }
    }

    private fun launchInCallUi() {
        val intent = Intent(this, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }
}
