package com.phonepvr.friends.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.phonepvr.friends.data.incall.AudioSnapshot
import com.phonepvr.friends.data.incall.CallAudioRoute
import com.phonepvr.friends.data.incall.CallDirection
import com.phonepvr.friends.data.incall.CallSimpleState
import com.phonepvr.friends.data.incall.CallSnapshot
import com.phonepvr.friends.ui.incall.InCallActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the ongoing-call notification so a call is reachable from anywhere —
 * the status bar, the notification panel, and (for incoming calls) a
 * full-screen heads-up that rises over the lock screen.
 *
 * Deliberately a plain high-priority ongoing notification rather than
 * NotificationCompat.CallStyle: CallStyle notifications are only rendered as
 * calls when they're attached to a foreground service, and silently dropped
 * otherwise on many devices — which is exactly the "no way back to the call"
 * symptom. A plain notification with a contentIntent into InCallActivity
 * always shows and always returns the user to the call screen.
 */
@Singleton
class CallNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val sys = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (sys.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Calls",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Ongoing and incoming calls"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
        }
        sys.createNotificationChannel(channel)
    }

    fun show(snapshot: CallSnapshot, callerName: String, audio: AudioSnapshot) {
        ensureChannel()
        val isIncomingRing = snapshot.state == CallSimpleState.RINGING &&
            snapshot.direction == CallDirection.INCOMING

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(callerName)
            .setContentText(statusText(snapshot, isIncomingRing))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(activityIntent())

        if (isIncomingRing) {
            builder.setFullScreenIntent(activityIntent(), true)
            builder.addAction(
                0,
                "Decline",
                broadcast(CallActionReceiver.ACTION_DECLINE, REQ_DECLINE),
            )
            builder.addAction(
                0,
                "Answer",
                broadcast(CallActionReceiver.ACTION_ANSWER, REQ_ANSWER),
            )
        } else {
            if (snapshot.state == CallSimpleState.ACTIVE && snapshot.connectTimeMillis != null) {
                builder.setWhen(snapshot.connectTimeMillis)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
            }
            // Audio output toggle — only when there's more than one route to
            // switch between (e.g. a Bluetooth headset is connected). The
            // label names the CURRENT output so it's clear what's active.
            if (audio.availableRoutes.size > 1) {
                builder.addAction(
                    routeIcon(audio.route),
                    routeLabel(audio.route),
                    broadcast(CallActionReceiver.ACTION_TOGGLE_AUDIO, REQ_AUDIO),
                )
            }
            builder.addAction(
                0,
                "Hang up",
                broadcast(CallActionReceiver.ACTION_HANGUP, REQ_HANGUP),
            )
        }

        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    fun cancel() {
        manager.cancel(NOTIFICATION_ID)
    }

    private fun statusText(snapshot: CallSnapshot, isIncomingRing: Boolean): String = when {
        isIncomingRing -> "Incoming call"
        snapshot.state == CallSimpleState.DIALING ||
            snapshot.state == CallSimpleState.CONNECTING -> "Calling…"
        snapshot.state == CallSimpleState.HOLDING -> "On hold"
        snapshot.state == CallSimpleState.ACTIVE -> "Ongoing call"
        else -> "In call"
    }

    private fun activityIntent(): PendingIntent {
        val intent = Intent(context, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            REQ_OPEN,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun broadcast(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, CallActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun routeLabel(route: CallAudioRoute): String = when (route) {
        CallAudioRoute.EARPIECE -> "Earpiece"
        CallAudioRoute.SPEAKER -> "Speaker"
        CallAudioRoute.BLUETOOTH -> "Bluetooth"
        CallAudioRoute.WIRED_HEADSET -> "Headset"
    }

    // Notification actions need a drawable resId; system glyphs keep it
    // dependency-free. The label already names the route, so the icon is
    // just decorative.
    private fun routeIcon(route: CallAudioRoute): Int = when (route) {
        CallAudioRoute.SPEAKER -> android.R.drawable.ic_lock_silent_mode_off
        else -> android.R.drawable.ic_btn_speak_now
    }

    companion object {
        private const val CHANNEL_ID = "ongoing_call"
        private const val NOTIFICATION_ID = 4242
        private const val REQ_OPEN = 1
        private const val REQ_ANSWER = 2
        private const val REQ_DECLINE = 3
        private const val REQ_HANGUP = 4
        private const val REQ_AUDIO = 5
    }
}
