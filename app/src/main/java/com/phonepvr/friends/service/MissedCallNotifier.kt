package com.phonepvr.friends.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.phonepvr.friends.MainActivity
import com.phonepvr.friends.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts a "Missed call" notification with Call back / Message actions, the
 * way every stock dialer does. Fired by [BondwidthInCallService] when an
 * incoming call disconnects with DisconnectCause.MISSED.
 *
 * One notification per number (id derived from the digits) so repeated
 * misses from the same person update in place, while different callers
 * stack. Tapping the body opens that number's call history; the actions
 * route through [CallActionReceiver] (call back) and the SMS app (message).
 */
@Singleton
class MissedCallNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun notifyMissedCall(number: String?, callerName: String?) {
        ensureChannel()
        val title = callerName?.takeIf { it.isNotBlank() }
            ?: number?.takeIf { it.isNotBlank() }
            ?: "Unknown caller"
        val id = notificationId(number)

        val builder = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle(title)
            .setContentText("Missed call")
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setContentIntent(openHistoryIntent(number, id))

        // Call back / Message only make sense when we actually have a number
        // (withheld / private callers give us nothing to act on).
        if (!number.isNullOrBlank()) {
            builder.addAction(
                android.R.drawable.sym_action_call,
                "Call back",
                callBackIntent(number, id),
            )
            // "Message" targets the user's default SMS app explicitly — an
            // implicit SENDTO intent inside a PendingIntent would be a leak
            // (CWE-927). Hide the action when there's no default SMS app to
            // address.
            defaultSmsPackage()?.let { smsPackage ->
                builder.addAction(
                    android.R.drawable.sym_action_email,
                    "Message",
                    messageIntent(number, smsPackage),
                )
            }
        }
        runCatching { manager.notify(id, builder.build()) }
    }

    /** Dismiss the missed-call notification for [number] (e.g. after callback). */
    fun cancel(number: String?) {
        manager.cancel(notificationId(number))
    }

    private fun openHistoryIntent(number: String?, notifId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (!number.isNullOrBlank()) {
            intent.putExtra(MainActivity.EXTRA_OPEN_CALL_HISTORY, number)
        }
        return PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun callBackIntent(number: String, notifId: Int): PendingIntent {
        val intent = Intent(context, CallActionReceiver::class.java)
            .setAction(CallActionReceiver.ACTION_CALL_BACK)
            .setPackage(context.packageName)
            .putExtra(CallActionReceiver.EXTRA_NUMBER, number)
        return PendingIntent.getBroadcast(
            context,
            notifId + REQ_CALL_BACK_OFFSET,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun messageIntent(number: String, smsPackage: String): PendingIntent {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
            .setPackage(smsPackage)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            notificationId(number) + REQ_MESSAGE_OFFSET,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Package name of the user's default SMS app, or null if none is set. */
    private fun defaultSmsPackage(): String? =
        Telephony.Sms.getDefaultSmsPackage(context)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val sys = context.getSystemService(NotificationManager::class.java) ?: return
        if (sys.getNotificationChannel(CHANNEL_MISSED) == null) {
            sys.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MISSED,
                    "Missed calls",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Alerts for calls you didn't answer."
                },
            )
        }
    }

    /** Stable per-number id (last 9 digits) so re-misses update in place. */
    private fun notificationId(number: String?): Int {
        val key = number?.filter(Char::isDigit)?.takeLast(9).orEmpty()
        return BASE_ID + (if (key.isEmpty()) 0 else key.hashCode() and 0xFFFF)
    }

    companion object {
        private const val CHANNEL_MISSED = "missed_calls"
        // Offset well clear of CallNotifier.NOTIFICATION_ID (4242).
        private const val BASE_ID = 5000
        private const val REQ_CALL_BACK_OFFSET = 100_000
        private const val REQ_MESSAGE_OFFSET = 200_000
    }
}
