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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontStyle
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
import com.phonepvr.friends.domain.quotes.Quote
import com.phonepvr.friends.domain.quotes.QuoteRepository
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
    fun quoteRepository(): QuoteRepository
}

/** One occasion (birthday OR anniversary) belonging to a person row. */
private data class WidgetOccasion(val type: EventType, val days: Long, val whenText: String)

/**
 * One row on the widget — a person with their (up to two) upcoming events
 * shown side-by-side. Each row gets a recency line beneath, coloured by the
 * person's cadence.
 */
private data class WidgetPersonRow(
    val personId: Long,
    val name: String,
    val soonest: Long,
    val occasions: List<WidgetOccasion>,
    val recencyText: String,
    val cadenceState: CadenceState,
)

private data class WidgetData(val quote: Quote?, val rows: List<WidgetPersonRow>)

// Soft cap on the lazy list. Glance LazyColumn is fine with this many on
// stock launchers; OEM hosts occasionally jank past a few hundred entries.
private const val MAX_WIDGET_ROWS = 80

/**
 * Home-screen widget showing today's quote and every upcoming birthday /
 * anniversary, grouped one person per row (so the same contact's two events
 * sit side-by-side), sorted by soonest-upcoming-event, and colour-coded by
 * the person's cadence. Glance LazyColumn makes the list scrollable.
 *
 * Auto-refreshes on app data changes via WidgetRefreshObserver (kicked off
 * from FriendsApplication.onCreate) — the daily worker stays as a belt-and-
 * braces backstop.
 */
class UpcomingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        provideContent {
            WidgetContent(data)
        }
    }

    private suspend fun loadData(context: Context): WidgetData {
        val deps = EntryPointAccessors.fromApplication(
            context,
            UpcomingWidgetEntryPoint::class.java,
        )
        val people = deps.peopleRepository().observeActiveWithDetails().first()
        val timelineDao = deps.timelineDao()
        val today = LocalDate.now()

        val rows = people
            .mapNotNull { personWithDetails ->
                val occasions = personWithDetails.events.mapNotNull { event ->
                    val days = AnnualDate(event.month, event.day, event.year)
                        .daysUntilNextOccurrence(today)
                    if (event.type != EventType.BIRTHDAY &&
                        event.type != EventType.WEDDING_ANNIVERSARY
                    ) {
                        null
                    } else {
                        WidgetOccasion(
                            type = event.type,
                            days = days,
                            whenText = whenLabel(event.type, days),
                        )
                    }
                }
                if (occasions.isEmpty()) return@mapNotNull null
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
                // Birthday on the left when both exist; otherwise whichever
                // one the person has.
                val ordered = occasions.sortedBy { if (it.type == EventType.BIRTHDAY) 0 else 1 }
                WidgetPersonRow(
                    personId = personWithDetails.person.id,
                    name = personWithDetails.person.displayName,
                    soonest = occasions.minOf { it.days },
                    occasions = ordered,
                    recencyText = recencyText(daysSinceContact),
                    cadenceState = cadenceState,
                )
            }
            .sortedBy { it.soonest }
            .take(MAX_WIDGET_ROWS)
        val quote = runCatching { deps.quoteRepository().quoteOfTheDay(today) }.getOrNull()
        return WidgetData(quote = quote, rows = rows)
    }
}

@Composable
private fun WidgetContent(data: WidgetData) {
    val context = LocalContext.current
    val launchActivity = actionStartActivity(ComponentName(context, MainActivity::class.java))
    LazyColumn(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(12.dp),
    ) {
        item {
            Column(modifier = GlanceModifier.fillMaxWidth().clickable(launchActivity)) {
                data.quote?.let { quote ->
                    Text(
                        text = quote.text,
                        maxLines = 2,
                        style = TextStyle(
                            color = GlanceTheme.colors.onBackground,
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                        ),
                    )
                    quote.attribution?.let { attribution ->
                        Text(
                            text = "— $attribution",
                            maxLines = 1,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 11.sp,
                            ),
                        )
                    }
                    Spacer(GlanceModifier.height(8.dp))
                }
                Text(
                    text = "Upcoming",
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.height(4.dp))
            }
        }
        if (data.rows.isEmpty()) {
            item {
                Column(modifier = GlanceModifier.fillMaxWidth().clickable(launchActivity)) {
                    Text(
                        text = "No birthdays or anniversaries lined up.",
                        style = TextStyle(color = GlanceTheme.colors.onBackground),
                    )
                }
            }
        } else {
            items(items = data.rows, itemId = { it.personId }) { row ->
                WidgetPersonCard(row = row, onClick = launchActivity)
            }
        }
    }
}

@Composable
private fun WidgetPersonCard(row: WidgetPersonRow, onClick: androidx.glance.action.Action) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick),
    ) {
        Text(
            text = row.name,
            style = TextStyle(
                color = GlanceTheme.colors.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp)) {
            row.occasions.forEachIndexed { index, occasion ->
                if (index > 0) Spacer(GlanceModifier.width(12.dp))
                Text(
                    text = "${occasionLabel(occasion.type)} ${occasion.whenText}",
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = 12.sp,
                    ),
                )
            }
        }
        Text(
            text = row.recencyText,
            style = TextStyle(
                color = when (row.cadenceState) {
                    CadenceState.OVERDUE ->
                        GlanceTheme.colors.error
                    CadenceState.DUE_SOON, CadenceState.NEVER_CONTACTED ->
                        GlanceTheme.colors.primary
                    CadenceState.ON_TRACK, CadenceState.NOT_TRACKED ->
                        GlanceTheme.colors.onBackground
                },
                fontSize = 11.sp,
            ),
        )
    }
}

private fun occasionLabel(type: EventType): String = when (type) {
    EventType.BIRTHDAY -> "🎂 Birthday"
    EventType.WEDDING_ANNIVERSARY -> "💞 Anniversary"
    EventType.CUSTOM -> "Date"
}

private fun whenLabel(@Suppress("UNUSED_PARAMETER") type: EventType, days: Long): String =
    when (days) {
        0L -> "today"
        1L -> "tomorrow"
        else -> "in ${days}d"
    }

private fun recencyText(daysSinceContact: Long?): String = when {
    daysSinceContact == null -> "never logged"
    daysSinceContact <= 0L -> "last spoke today"
    daysSinceContact == 1L -> "last spoke yesterday"
    else -> "last spoke $daysSinceContact days ago"
}
