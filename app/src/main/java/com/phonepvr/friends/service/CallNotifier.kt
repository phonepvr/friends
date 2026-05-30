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
import androidx.core.app.Person
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
 * Uses NotificationCompat.CallStyle so the OS renders it as a proper
 * call-shaped notification (round answer / decline buttons, heads-up,
 * lock-screen visibility). CallStyle only renders correctly when the
 * posting service is in the foreground — [BondwidthInCallService] handles
 * that side, calling startForeground with this notification at the start
 * of every call.
 */
@Singleton
class CallNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    /**
     * Builds the call notification without posting it. The InCallService
     * passes the first instance to startForeground; subsequent updates go
     * via [post].
     */
    fun build(snapshot: CallSnapshot, callerName: String, audio: AudioSnapshot): Notification {
        ensureChannel()
        val isIncomingRing = snapshot.state == CallSimpleState.RINGING &&
            snapshot.direction == CallDirection.INCOMING

        val person = Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(activityIntent())

        if (isIncomingRing) {
            // The full-screen intent is what rises over the lockscreen — the
            // sanctioned way to launch an Activity from a background event on
            // Android 10+. Requires USE_FULL_SCREEN_INTENT (manifest) AND
            // either ROLE_DIALER or the per-app Settings toggle on 14+.
            builder.setFullScreenIntent(activityIntent(), true)
            builder.setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    person,
                    broadcast(CallActionReceiver.ACTION_DECLINE, REQ_DECLINE),
                    broadcast(CallActionReceiver.ACTION_ANSWER, REQ_ANSWER),
                ),
            )
        } else {
            builder.setStyle(
                NotificationCompat.CallStyle.forOngoingCall(
                    person,
                    broadcast(CallActionReceiver.ACTION_HANGUP, REQ_HANGUP),
                ),
            )
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
        }

        return builder.build()
    }

    fun post(notification: Notification) {
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun cancel() {
        manager.cancel(NOTIFICATION_ID)
    }

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
            // Ringer owns ringtone + vibration for incoming calls
            // (IN_CALL_SERVICE_RINGING="true" means the platform delegates
            // ringing to us). Channel-level sound stays null so the
            // notification itself never makes noise.
            setSound(null, null)
            enableVibration(false)
        }
        sys.createNotificationChannel(channel)
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
        const val NOTIFICATION_ID = 4242
        private const val CHANNEL_ID = "ongoing_call"
        private const val REQ_OPEN = 1
        private const val REQ_ANSWER = 2
        private const val REQ_DECLINE = 3
        private const val REQ_HANGUP = 4
        private const val REQ_AUDIO = 5
    }
}
