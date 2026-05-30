package com.phonepvr.friends.data.incall

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the system default ringtone and vibrates while an incoming call is
 * ringing. Bondwidth's InCallService is declared with
 * IN_CALL_SERVICE_RINGING="true", which means the platform stops ringing on
 * our behalf — we own playback. Without this, an incoming call lands silent.
 *
 * Honours the user's ringer mode: silent → nothing, vibrate → vibrate only,
 * normal → ringtone + vibrate.
 */
@Singleton
class Ringer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    // 0ms wait, 1s buzz, 1s silence — the canonical incoming-call cadence.
    private val vibrationPattern = longArrayOf(0L, 1000L, 1000L)
    private val vibrationAmps = intArrayOf(0, 255, 0)

    @Synchronized
    fun start() {
        if (player != null) return
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val mode = audioManager.ringerMode
        if (mode == AudioManager.RINGER_MODE_SILENT) return
        if (mode == AudioManager.RINGER_MODE_NORMAL) playRingtone()
        startVibration()
    }

    @Synchronized
    fun stop() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun playRingtone() {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(
            context,
            RingtoneManager.TYPE_RINGTONE,
        ) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) ?: return
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!v.hasVibrator()) return
        runCatching {
            val effect = VibrationEffect.createWaveform(vibrationPattern, vibrationAmps, 1)
            v.vibrate(effect)
            vibrator = v
        }
    }
}
