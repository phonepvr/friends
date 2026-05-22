package com.phonepvr.friends.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phonepvr.friends.data.db.dao.TimelineDao
import com.phonepvr.friends.data.repository.PeopleRepository
import com.phonepvr.friends.data.settings.SettingsRepository
import com.phonepvr.friends.domain.cadence.CadenceCalculator
import com.phonepvr.friends.domain.cadence.CadenceState
import com.phonepvr.friends.domain.model.AnnualDate
import com.phonepvr.friends.domain.model.EventType
import com.phonepvr.friends.notification.ReminderNotifier
import com.phonepvr.friends.widget.UpcomingWidget
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private const val UNIQUE_WORK_NAME = "friends-daily-reminders"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderWorkerEntryPoint {
    fun peopleRepository(): PeopleRepository
    fun timelineDao(): TimelineDao
    fun settingsRepository(): SettingsRepository
}

/**
 * Daily background job that posts reminders for upcoming birthdays and
 * anniversaries, and nudges for people who are overdue versus their cadence.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(
            applicationContext,
            ReminderWorkerEntryPoint::class.java,
        )
        val leadDays = deps.settingsRepository().settings.first()
            .notificationLeadDays.toLong()
        val people = deps.peopleRepository().observeActiveWithDetails().first()
        val timelineDao = deps.timelineDao()
        val today = LocalDate.now()
        val lines = mutableListOf<String>()

        people.forEach { personWithDetails ->
            personWithDetails.events.forEach { event ->
                val days = AnnualDate(event.month, event.day, event.year)
                    .daysUntilNextOccurrence(today)
                if (days in 0..leadDays) {
                    lines.add(upcomingLine(personWithDetails.person.displayName, event.type, days))
                }
            }
        }

        people.forEach { personWithDetails ->
            val target = personWithDetails.person.cadenceTargetDays ?: return@forEach
            val lastContactMillis = timelineDao.latestContactAt(personWithDetails.person.id)
            val status = CadenceCalculator.status(
                lastContact = lastContactMillis?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                },
                cadenceTargetDays = target,
                today = today,
            )
            if (status.state == CadenceState.OVERDUE) {
                lines.add("Reach out to ${personWithDetails.person.displayName} — overdue")
            }
        }

        if (lines.isNotEmpty()) {
            ReminderNotifier.postReminders(applicationContext, lines)
        }
        UpcomingWidget().updateAll(applicationContext)
        return Result.success()
    }
}

private fun upcomingLine(name: String, type: EventType, days: Long): String {
    val occasion = when (type) {
        EventType.BIRTHDAY -> "birthday"
        EventType.WEDDING_ANNIVERSARY -> "anniversary"
        EventType.CUSTOM -> "special date"
    }
    val whenText = when (days) {
        0L -> "today"
        1L -> "tomorrow"
        else -> "in $days days"
    }
    return "$name's $occasion is $whenText"
}

/** Schedules the daily reminder job, keeping any existing schedule. */
fun scheduleReminderWork(context: Context, hour: Int) {
    enqueueReminderWork(context, hour, ExistingPeriodicWorkPolicy.KEEP)
}

/** Re-enqueues the daily reminder job so a changed notification hour applies. */
fun rescheduleReminderWork(context: Context, hour: Int) {
    enqueueReminderWork(context, hour, ExistingPeriodicWorkPolicy.UPDATE)
}

private fun enqueueReminderWork(
    context: Context,
    hour: Int,
    policy: ExistingPeriodicWorkPolicy,
) {
    val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
        .setInitialDelay(initialDelayMillis(hour), TimeUnit.MILLISECONDS)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        UNIQUE_WORK_NAME,
        policy,
        request,
    )
}

private fun initialDelayMillis(hour: Int): Long {
    val now = ZonedDateTime.now()
    var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
    if (!next.isAfter(now)) {
        next = next.plusDays(1)
    }
    return Duration.between(now, next).toMillis()
}
