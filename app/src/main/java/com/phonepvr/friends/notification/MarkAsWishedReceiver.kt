package com.phonepvr.friends.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.phonepvr.friends.data.db.dao.TimelineDao
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity
import com.phonepvr.friends.domain.model.EntrySource
import com.phonepvr.friends.domain.model.InteractionType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Background handler for the "Mark as wished" notification action.
 *
 * Logs a `Wished – <label>` MANUAL entry against the person so the cadence
 * timer resets and the timeline reflects the acknowledgement, then cancels
 * the notification that triggered the action.
 *
 * Registered as a non-exported manifest receiver; uses [EntryPointAccessors]
 * rather than `@AndroidEntryPoint` so the dependency story matches the rest
 * of this codebase (workers, widget) without pulling in the Hilt component
 * for receivers.
 */
class MarkAsWishedReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MarkAsWishedEntryPoint {
        fun timelineDao(): TimelineDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        val personId = intent.getLongExtra(EXTRA_PERSON_ID, -1L)
        val eventLabel = intent.getStringExtra(EXTRA_EVENT_LABEL).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (personId <= 0L) return

        val deps = EntryPointAccessors.fromApplication(
            context.applicationContext,
            MarkAsWishedEntryPoint::class.java,
        )
        val now = System.currentTimeMillis()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                deps.timelineDao().insert(
                    TimelineEntryEntity(
                        personId = personId,
                        occurredAt = now,
                        type = InteractionType.OTHER,
                        note = "Wished – $eventLabel",
                        source = EntrySource.MANUAL,
                        countsAsContact = true,
                        callDedupKey = null,
                        createdAt = now,
                    ),
                )
                if (notificationId >= 0) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_PERSON_ID = "personId"
        const val EXTRA_EVENT_LABEL = "eventLabel"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
    }
}
