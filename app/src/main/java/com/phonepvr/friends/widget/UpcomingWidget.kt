package com.phonepvr.friends.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.phonepvr.friends.MainActivity
import com.phonepvr.friends.data.db.dao.TimelineDao
import com.phonepvr.friends.data.repository.PeopleRepository
import com.phonepvr.friends.domain.cadence.CadenceCalculator
import com.phonepvr.friends.domain.cadence.CadenceState
import com.phonepvr.friends.domain.model.AnnualDate
import com.phonepvr.friends.domain.model.EventType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpcomingWidgetEntryPoint {
    fun peopleRepository(): PeopleRepository
    fun timelineDao(): TimelineDao
}

private data class WidgetItem(
    val days: Long,
    val line: String,
    val recencyText: String,
    val cadenceState: CadenceState,
)

/** Home-screen widget listing birthdays and anniversaries in the next 30 days. */
class UpcomingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = loadUpcoming(context)
        provideContent {
            WidgetContent(items)
        }
    }

    private suspend fun loadUpcoming(context: Context): List<WidgetItem> {
        val deps = EntryPointAccessors.fromApplication(
            context,
            UpcomingWidgetEntryPoint::class.java,
        )
        val people = deps.peopleRepository().observeActiveWithDetails().first()
        val timelineDao = deps.timelineDao()
        val today = LocalDate.now()
        return people
            .flatMap { personWithDetails ->
                val lastContactDate = timelineDao
                    .latestContactAt(personWithDetails.person.id)
                    ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                val daysSinceContact = lastContactDate
                    ?.let { ChronoUnit.DAYS.between(it, today) }
                val cadenceState = CadenceCalculator.status(
                    lastContact = lastContactDate,
                    cadenceTargetDays = personWithDetails.person.cadenceTargetDays,
                    today = today,
                ).state
                personWithDetails.events.map { event ->
                    val days = AnnualDate(event.month, event.day, event.year)
                        .daysUntilNextOccurrence(today)
                    WidgetItem(
                        days = days,
                        line = widgetLine(
                            personWithDetails.person.displayName,
                            event.type,
                            days,
                        ),
                        recencyText = recencyText(daysSinceContact),
                        cadenceState = cadenceState,
                    )
                }
            }
            .filter { it.days in 0..30 }
            .sortedBy { it.days }
            .take(5)
    }
}

@Composable
private fun WidgetContent(items: List<WidgetItem>) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(12.dp)
            .clickable(
                actionStartActivity(ComponentName(context, MainActivity::class.java)),
            ),
    ) {
        Text(
            text = "Upcoming",
            style = TextStyle(
                color = GlanceTheme.colors.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(8.dp))
        if (items.isEmpty()) {
            Text(
                text = "No birthdays or anniversaries in the next 30 days.",
                style = TextStyle(color = GlanceTheme.colors.onBackground),
            )
        } else {
            items.forEach { item ->
                Column(modifier = GlanceModifier.padding(vertical = 2.dp)) {
                    Text(
                        text = item.line,
                        style = TextStyle(color = GlanceTheme.colors.onBackground),
                    )
                    Text(
                        text = item.recencyText,
                        style = TextStyle(
                            color = when (item.cadenceState) {
                                CadenceState.OVERDUE ->
                                    GlanceTheme.colors.error
                                CadenceState.DUE_SOON, CadenceState.NEVER_CONTACTED ->
                                    GlanceTheme.colors.primary
                                CadenceState.ON_TRACK, CadenceState.NOT_TRACKED ->
                                    GlanceTheme.colors.onBackground
                            },
                            fontSize = 12.sp,
                        ),
                    )
                }
            }
        }
    }
}

private fun widgetLine(name: String, type: EventType, days: Long): String {
    val occasion = when (type) {
        EventType.BIRTHDAY -> "birthday"
        EventType.WEDDING_ANNIVERSARY -> "anniversary"
        EventType.CUSTOM -> "date"
    }
    val whenText = when (days) {
        0L -> "today"
        1L -> "tomorrow"
        else -> "in $days days"
    }
    return "$name — $occasion $whenText"
}

private fun recencyText(daysSinceContact: Long?): String = when {
    daysSinceContact == null -> "never logged"
    daysSinceContact <= 0L -> "last spoke today"
    daysSinceContact == 1L -> "last spoke yesterday"
    else -> "last spoke $daysSinceContact days ago"
}
