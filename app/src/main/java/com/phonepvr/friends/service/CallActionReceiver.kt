package com.phonepvr.friends.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.phonepvr.friends.data.incall.CallSession
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles the Answer / Decline / Hang up actions tapped on the ongoing-call
 * notification. Routes straight into the shared [CallSession], which holds
 * the live Telecom Call.
 */
@AndroidEntryPoint
class CallActionReceiver : BroadcastReceiver() {

    @Inject lateinit var callSession: CallSession

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ANSWER -> callSession.accept()
            ACTION_DECLINE -> callSession.reject()
            ACTION_HANGUP -> callSession.end()
            ACTION_TOGGLE_AUDIO -> callSession.cycleAudioRoute()
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.phonepvr.friends.call.ANSWER"
        const val ACTION_DECLINE = "com.phonepvr.friends.call.DECLINE"
        const val ACTION_HANGUP = "com.phonepvr.friends.call.HANGUP"
        const val ACTION_TOGGLE_AUDIO = "com.phonepvr.friends.call.TOGGLE_AUDIO"
    }
}
