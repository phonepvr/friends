package com.phonepvr.friends.service

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.phonepvr.friends.data.incall.CallDirection
import com.phonepvr.friends.data.incall.CallSession
import com.phonepvr.friends.data.incall.CallSimpleState
import com.phonepvr.friends.data.incall.CallerIdentityResolver
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
 * v1 single-call assumption: the latest-added Call is the one the UI
 * shows. Held / waiting calls aren't surfaced separately yet.
 */
@AndroidEntryPoint
class BondwidthInCallService : InCallService() {

    @Inject lateinit var callSession: CallSession
    @Inject lateinit var callNotifier: CallNotifier
    @Inject lateinit var callerIdentityResolver: CallerIdentityResolver

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
        refreshNotification()
        // For an outgoing/active call there's a foreground token from the
        // user's dial action, so opening the Activity directly is allowed.
        // For an incoming ring the notification's full-screen intent is the
        // sanctioned path (a background Activity launch would be blocked on
        // Android 10+), so we don't startActivity here.
        val snapshot = callSession.snapshot.value
        val isIncomingRing = snapshot?.state == CallSimpleState.RINGING &&
            snapshot.direction == CallDirection.INCOMING
        if (!isIncomingRing) launchInCallUi()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        callSession.detachCall(call)
        callNotifier.cancel()
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
        callSession.detachService(this)
        callNotifier.cancel()
        scope.cancel()
    }

    private fun refreshNotification() {
        val snapshot = callSession.snapshot.value
        if (snapshot == null || snapshot.state == CallSimpleState.DISCONNECTED) {
            callNotifier.cancel()
            return
        }
        // Resolve the caller's name off the main thread, then post.
        scope.launch {
            val identity = callerIdentityResolver.resolve(snapshot.number)
            val name = identity.displayName
                ?: snapshot.callerDisplayName
                ?: snapshot.number.ifBlank { "Unknown" }
            callNotifier.show(snapshot, name, callSession.audio.value)
        }
    }

    private fun launchInCallUi() {
        val intent = Intent(this, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }
}
