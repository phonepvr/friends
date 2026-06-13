package com.phonepvr.friends.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.phonepvr.friends.data.dialer.CallPlacer
import com.phonepvr.friends.data.incall.CallSession
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles the Answer / Decline / Hang up actions tapped on the ongoing-call
 * notification, plus Call back from the missed-call notification. The call
 * actions route into the shared [CallSession] (which holds the live Telecom
 * Call); Call back places a fresh call through [CallPlacer] — there's no
 * live call by then.
 */
@AndroidEntryPoint
class CallActionReceiver : BroadcastReceiver() {

    @Inject lateinit var callSession: CallSession
    @Inject lateinit var callPlacer: CallPlacer
    @Inject lateinit var missedCallNotifier: MissedCallNotifier

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ANSWER -> callSession.accept()
            ACTION_DECLINE -> callSession.reject()
            ACTION_HANGUP -> callSession.end()
            ACTION_TOGGLE_AUDIO -> callSession.cycleAudioRoute()
            ACTION_CALL_BACK -> {
                val number = intent.getStringExtra(EXTRA_NUMBER)
                if (!number.isNullOrBlank()) {
                    // As default dialer we hold CALL_PHONE; placeCall returns
                    // the in-call UI via the InCallService. Dismiss the
                    // missed-call notification either way so it doesn't linger.
                    callPlacer.place(number)
                    missedCallNotifier.cancel(number)
                }
            }
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.phonepvr.friends.call.ANSWER"
        const val ACTION_DECLINE = "com.phonepvr.friends.call.DECLINE"
        const val ACTION_HANGUP = "com.phonepvr.friends.call.HANGUP"
        const val ACTION_TOGGLE_AUDIO = "com.phonepvr.friends.call.TOGGLE_AUDIO"
        const val ACTION_CALL_BACK = "com.phonepvr.friends.call.CALL_BACK"
        const val EXTRA_NUMBER = "com.phonepvr.friends.call.EXTRA_NUMBER"
    }
}
