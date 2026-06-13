package com.phonepvr.friends.ui.incall

import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.MainActivity
import com.phonepvr.friends.data.incall.CallSimpleState
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

    // Drives the compact-vs-full UI swap and tracks PiP transitions so the
    // screen knows which layout to show. Composed-state-friendly because the
    // setContent block reads it.
    private var isInPip by mutableStateOf(false)

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
            val hideFromScreenshots by viewModel.hideFromScreenshots
                .collectAsStateWithLifecycle()

            // Match MainActivity: toggle FLAG_SECURE in lockstep with the
            // user's setting so the in-call screen (caller name / number /
            // photo) stays out of screenshots, screen recordings, the recents
            // preview and casts whenever the user has chosen to hide the app.
            // This is a separate window from MainActivity's, so it has to set
            // the flag itself.
            LaunchedEffect(hideFromScreenshots) {
                if (hideFromScreenshots) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

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
                        isInPip = isInPip,
                        canMinimize = pipSupported(),
                        onMinimize = ::enterPip,
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
     * Auto-enter Picture-in-Picture when the user navigates Home during an
     * active call. Without this, pressing Home over an in-progress call
     * would just leave them with a tiny status-bar icon and no obvious way
     * to do anything with the call while using the rest of Bondwidth.
     * Skipped while the call is still ringing (no point shrinking a screen
     * the user might immediately answer or reject) or already PiP'd.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val snap = viewModel.state.value.snapshot ?: return
        if (snap.state == CallSimpleState.RINGING || isInPip || !pipSupported()) return
        enterPip()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip = isInPictureInPictureMode
    }

    /** True iff the device advertises PiP support — wears, some TVs, and a
     *  handful of OEM builds disable it. */
    private fun pipSupported(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** Enters PiP at a portrait-ish aspect that fits a phone-call card. */
    private fun enterPip() {
        if (!pipSupported()) return
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(3, 4))
            .build()
        runCatching { enterPictureInPictureMode(params) }
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
