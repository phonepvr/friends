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
import com.phonepvr.friends.data.incall.CallSimpleState
import com.phonepvr.friends.data.incall.CallSnapshot
import com.phonepvr.friends.data.incall.CallDirection
import com.phonepvr.friends.ui.incall.InCallActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the ongoing-call notification so a call is reachable from anywhere —
 * the status bar, the notification panel, and (for incoming calls) a
 * full-screen heads-up that rises over the lock screen. This is also what
 * lets an incoming call surface at all on Android 10+, where an
 * InCallService can't launch its Activity straight from the background; the
 * full-screen intent is the sanctioned path.
 *
 * Uses NotificationCompat.CallStyle so the system renders the proper
 * call affordances (Answer / Decline / Hang up) and treats it as a call.
 */
@Singleton
class CallNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val existing = (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Calls",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Ongoing and incoming calls"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    fun show(snapshot: CallSnapshot, callerName: String) {
        ensureChannel()
        val person = Person.Builder().setName(callerName).build()
        val isIncomingRing = snapshot.state == CallSimpleState.RINGING &&
            snapshot.direction == CallDirection.INCOMING

        val fullScreen = activityIntent()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(activityIntent())

        if (isIncomingRing) {
            builder
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setFullScreenIntent(fullScreen, true)
                .setStyle(
                    NotificationCompat.CallStyle.forIncomingCall(
                        person,
                        broadcast(CallActionReceiver.ACTION_DECLINE, REQ_DECLINE),
                        broadcast(CallActionReceiver.ACTION_ANSWER, REQ_ANSWER),
                    ),
                )
        } else {
            val active = snapshot.state == CallSimpleState.ACTIVE
            if (active && snapshot.connectTimeMillis != null) {
                // Stable base so re-posts (on each details change) don't
                // reset the running timer to 0:00.
                builder.setWhen(snapshot.connectTimeMillis).setUsesChronometer(true)
            } else {
                builder.setUsesChronometer(false)
            }
            builder.setStyle(
                NotificationCompat.CallStyle.forOngoingCall(
                    person,
                    broadcast(CallActionReceiver.ACTION_HANGUP, REQ_HANGUP),
                ),
            )
        }

        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    fun cancel() {
        manager.cancel(NOTIFICATION_ID)
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

    companion object {
        private const val CHANNEL_ID = "ongoing_call"
        private const val NOTIFICATION_ID = 4242
        private const val REQ_OPEN = 1
        private const val REQ_ANSWER = 2
        private const val REQ_DECLINE = 3
        private const val REQ_HANGUP = 4
    }
}
