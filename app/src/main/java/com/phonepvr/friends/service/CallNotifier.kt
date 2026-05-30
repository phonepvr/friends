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
 * Two channels handle the importance split that Android needs:
 *  - [CHANNEL_RINGING] — IMPORTANCE_HIGH, used only while a call is
 *    actually ringing. Heads-up + full-screen intent fire because the
 *    channel allows it.
 *  - [CHANNEL_ONGOING] — IMPORTANCE_LOW, used the moment the call is
 *    answered. The notification stays sticky in the panel so the user
 *    can swipe back to the call from anywhere, but it does NOT heads-up
 *    over the in-call screen. Single-channel IMPORTANCE_HIGH would cause
 *    the post-pickup re-build to pop a banner over the in-call UI on
 *    every state change.
 *
 * Channel importance is set at creation and the app can't downgrade it
 * after — so picking the right importance up front is the whole game.
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
        ensureChannels()
        val isIncomingRing = snapshot.state == CallSimpleState.RINGING &&
            snapshot.direction == CallDirection.INCOMING

        return if (isIncomingRing) {
            buildIncomingRing(snapshot, callerName)
        } else {
            buildOngoing(snapshot, callerName, audio)
        }
    }

    private fun buildIncomingRing(snapshot: CallSnapshot, callerName: String): Notification {
        val person = Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()

        return NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(activityIntent())
            // The full-screen intent is what rises over the lockscreen — the
            // sanctioned way to launch an Activity from a background event on
            // Android 10+. Requires USE_FULL_SCREEN_INTENT (manifest) AND
            // either ROLE_DIALER or the per-app Settings toggle on 14+.
            .setFullScreenIntent(activityIntent(), true)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    person,
                    broadcast(CallActionReceiver.ACTION_DECLINE, REQ_DECLINE),
                    broadcast(CallActionReceiver.ACTION_ANSWER, REQ_ANSWER),
                ),
            )
            .build()
    }

    private fun buildOngoing(
        snapshot: CallSnapshot,
        callerName: String,
        audio: AudioSnapshot,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(callerName)
            .setContentText(ongoingStatusText(snapshot))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(activityIntent())

        if (snapshot.state == CallSimpleState.ACTIVE && snapshot.connectTimeMillis != null) {
            builder.setWhen(snapshot.connectTimeMillis)
                .setShowWhen(true)
                .setUsesChronometer(true)
        }
        builder.addAction(
            0,
            "Hang up",
            broadcast(CallActionReceiver.ACTION_HANGUP, REQ_HANGUP),
        )
        // Audio output toggle — only when there's more than one route to
        // switch between (e.g. a Bluetooth headset is connected). icon=0
        // keeps it text-only; some OEMs render system drawables as a blank
        // square in notification actions.
        if (audio.availableRoutes.size > 1) {
            builder.addAction(
                0,
                routeLabel(audio.route),
                broadcast(CallActionReceiver.ACTION_TOGGLE_AUDIO, REQ_AUDIO),
            )
        }
        return builder.build()
    }

    private fun ongoingStatusText(snapshot: CallSnapshot): String = when (snapshot.state) {
        CallSimpleState.DIALING, CallSimpleState.CONNECTING -> "Calling…"
        CallSimpleState.HOLDING -> "On hold"
        CallSimpleState.ACTIVE -> "Ongoing call"
        else -> "In call"
    }

    fun post(notification: Notification) {
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun cancel() {
        manager.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val sys = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (sys.getNotificationChannel(CHANNEL_RINGING) == null) {
            val ringing = NotificationChannel(
                CHANNEL_RINGING,
                "Incoming calls",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Heads-up and full-screen ring for incoming calls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // Ringer owns ringtone + vibration for incoming calls
                // (IN_CALL_SERVICE_RINGING="true" means the platform delegates
                // ringing to us). Channel-level sound stays null so the
                // notification itself never makes noise.
                setSound(null, null)
                enableVibration(false)
            }
            sys.createNotificationChannel(ringing)
        }
        if (sys.getNotificationChannel(CHANNEL_ONGOING) == null) {
            val ongoing = NotificationChannel(
                CHANNEL_ONGOING,
                "Ongoing calls",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Sticky notification for an active call"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            sys.createNotificationChannel(ongoing)
        }
        // Delete the legacy "ongoing_call" channel from the earlier
        // single-channel design. It was IMPORTANCE_HIGH for every state, so
        // post-pickup re-builds heads-up'd over the in-call screen.
        runCatching { sys.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
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

    companion object {
        const val NOTIFICATION_ID = 4242
        private const val CHANNEL_RINGING = "calls_incoming"
        private const val CHANNEL_ONGOING = "calls_ongoing"
        private const val LEGACY_CHANNEL_ID = "ongoing_call"
        private const val REQ_OPEN = 1
        private const val REQ_ANSWER = 2
        private const val REQ_DECLINE = 3
        private const val REQ_HANGUP = 4
        private const val REQ_AUDIO = 5
    }
}
