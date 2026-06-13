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

/** Low-priority nudge that fires when no backup has been exported in a while. */
object BackupNudgeNotifier {

    const val CHANNEL_ID = "friends_backup_nudge"
    private const val NOTIFICATION_ID = 2002

    fun post(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Backup reminders",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )

        val intent = Intent(context, MainActivity::class.java)
            .setPackage(context.packageName)
            .putExtra(MainActivity.EXTRA_OPEN_BACKUP, true)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_BACKUP,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time to back up Bondwidth")
            .setContentText("It's been a while since your last backup. Tap to export now.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS was not granted — there is nothing to show.
        }
    }

    private const val REQUEST_OPEN_BACKUP = 1001
}
