package com.phonepvr.friends.service

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.phonepvr.friends.data.incall.CallSession
import com.phonepvr.friends.ui.incall.InCallActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Bondwidth's in-call service implementation. Bound by the platform's
 * Telecom framework whenever Bondwidth holds ROLE_DIALER and a call is
 * present (incoming, outgoing, or ongoing). Funnels every Call lifecycle
 * event into [CallSession], which the in-call Activity collects to
 * render the Compose UI.
 *
 * v1 single-call assumption: the latest-added Call is the one the UI
 * shows. Held / waiting calls aren't surfaced separately yet.
 */
@AndroidEntryPoint
class BondwidthInCallService : InCallService() {

    @Inject lateinit var callSession: CallSession

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            callSession.publishSnapshot()
            // When the platform marks the call disconnected, also tear down
            // the in-call UI so the user isn't staring at a stale screen.
            // The actual Call object is still alive for a moment; the
            // service unregisters us via onCallRemoved shortly after.
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            callSession.publishSnapshot()
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        callSession.attachService(this)
        callSession.attachCall(call)
        call.registerCallback(callCallback)
        launchInCallUi()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        callSession.detachCall(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        callSession.publishAudio(audioState)
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        if (callSession.hasActiveCall()) launchInCallUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        callSession.detachService(this)
    }

    private fun launchInCallUi() {
        val intent = Intent(this, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
