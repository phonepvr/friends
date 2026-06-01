package com.phonepvr.friends.ui.incall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.MainActivity
import com.phonepvr.friends.ui.theme.FriendsTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

/**
 * Activity hosting the in-call Compose UI. Started by
 * [com.phonepvr.friends.service.BondwidthInCallService] when a call is
 * added; observes the shared [com.phonepvr.friends.data.incall.CallSession]
 * to render Ringing / Dialing / Ongoing states.
 *
 * Launches over the lockscreen and wakes the device so an incoming call
 * is unmissable.
 */
@AndroidEntryPoint
class InCallActivity : ComponentActivity() {

    private val viewModel: InCallViewModel by viewModels()

    // The power button doesn't deliver a key event to a foreground activity —
    // it turns the screen off. We listen for ACTION_SCREEN_OFF instead and
    // treat it as "user wants the ringer to stop", matching how every
    // stock dialer behaves.
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                viewModel.silenceRinger()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
        // Keep the screen on while in the call regardless of API level.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()

            // After the call ends, hold the "Call ended" screen for a beat
            // so the user sees what happened, then finish so the activity
            // doesn't loiter in recents.
            LaunchedEffect(state.callEnded) {
                if (state.callEnded) {
                    delay(800)
                    finish()
                }
            }

            FriendsTheme {
                Surface {
                    InCallScreen(
                        state = state,
                        onAccept = viewModel::accept,
                        onReject = viewModel::reject,
                        onRejectWith = viewModel::rejectWith,
                        onBlockReject = viewModel::blockAndReject,
                        onEnd = viewModel::end,
                        onToggleMute = viewModel::toggleMute,
                        onSetAudioRoute = viewModel::setAudioRoute,
                        onDtmf = viewModel::pressDtmf,
                        onToggleHold = viewModel::toggleHold,
                        onSwap = viewModel::swap,
                        onMerge = viewModel::merge,
                        onAddCall = ::openDialpadForAddCall,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Receiver only lives while the activity is visible — the moment the
        // user accepts/rejects and we finish, it goes away.
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onStop() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        super.onStop()
    }

    /**
     * Volume up / down on a ringing call silences the ringtone (matching
     * stock-dialer behaviour) instead of changing the music stream. Every
     * other key event passes through, including volume during an active
     * call so the user can still adjust earpiece / speaker level.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        ) {
            val snap = viewModel.state.value.snapshot
            if (snap?.state == com.phonepvr.friends.data.incall.CallSimpleState.RINGING) {
                viewModel.silenceRinger()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Opens MainActivity's dialpad in a separate task so the user can
     * dial a second number. Telecom puts the current call on hold the
     * moment a second leg starts; when that second call lands the
     * InCallService re-launches this activity over the new call.
     */
    private fun openDialpadForAddCall() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_DIAL
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
    }
}
