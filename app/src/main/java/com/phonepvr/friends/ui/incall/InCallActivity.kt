package com.phonepvr.friends.ui.incall

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                        onEnd = viewModel::end,
                        onToggleMute = viewModel::toggleMute,
                        onToggleSpeaker = viewModel::toggleSpeaker,
                    )
                }
            }
        }
    }
}
