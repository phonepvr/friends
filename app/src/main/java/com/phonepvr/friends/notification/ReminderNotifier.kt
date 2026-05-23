package com.phonepvr.friends.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.phonepvr.friends.MainActivity
import com.phonepvr.friends.R
import com.phonepvr.friends.domain.model.EventType

/**
 * Builds and posts reminder notifications. Per-event notifications carry a
 * "Mark as wished" action (handled by [MarkAsWishedReceiver]); overdue
 * cadence reminders are aggregated into one summary notification with no
 * inline actions.
 */
object ReminderNotifier {

    private const val CHANNEL_ID = "friends_reminders"
    private const val OVERDUE_NOTIFICATION_ID = 2001
    // Per-person event notifications share an offset so each person gets a
    // stable id. One event per person per lead window is the common case;
    // multiple events for the same person on the same day update in place.
    private const val EVENT_NOTIFICATION_ID_BASE = 2_100_000

    data class EventReminder(
        val personId: Long,
        val personName: String,
        val type: EventType,
        val daysUntil: Long,
    )

    fun postEventReminder(context: Context, reminder: EventReminder) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)
        val notificationId = eventNotificationId(reminder)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPi = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val eventLabel = eventLabel(reminder.type)
        val wishedIntent = Intent(context, MarkAsWishedReceiver::class.java).apply {
            putExtra(MarkAsWishedReceiver.EXTRA_PERSON_ID, reminder.personId)
            putExtra(MarkAsWishedReceiver.EXTRA_EVENT_LABEL, eventLabel)
            putExtra(MarkAsWishedReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val wishedPi = PendingIntent.getBroadcast(
            context,
            notificationId,
            wishedIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(upcomingLine(reminder.personName, reminder.type, reminder.daysUntil))
            .setContentIntent(openPi)
            .setAutoCancel(true)
            // Don't surface person names on the lockscreen unless the device is
            // already unlocked, and don't mirror to companion devices (Wear etc.)
            // — Friends data should stay on this phone.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setLocalOnly(true)
            .addAction(0, "Mark as wished", wishedPi)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted — there is nothing to show.
        }
    }

    fun postOverdueSummary(context: Context, lines: List<String>) {
        if (lines.isEmpty()) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle()
        lines.forEach { style.addLine(it) }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Bondwidth — overdue")
            .setContentText(lines.first())
            .setStyle(style)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setLocalOnly(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(OVERDUE_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted — there is nothing to show.
        }
    }

    private fun ensureChannel(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    private fun eventNotificationId(reminder: EventReminder): Int =
        (EVENT_NOTIFICATION_ID_BASE + reminder.personId).toInt()

    private fun eventLabel(type: EventType): String = when (type) {
        EventType.BIRTHDAY -> "birthday"
        EventType.WEDDING_ANNIVERSARY -> "anniversary"
        EventType.CUSTOM -> "special date"
    }

    private fun upcomingLine(name: String, type: EventType, days: Long): String {
        val occasion = eventLabel(type)
        val whenText = when (days) {
            0L -> "today"
            1L -> "tomorrow"
            else -> "in $days days"
        }
        return "$name's $occasion is $whenText"
    }
}
