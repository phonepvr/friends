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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
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

private data class WidgetItem(
    val id: Long,
    val days: Long,
    val line: String,
    val recencyText: String,
    val cadenceState: CadenceState,
)

private data class WidgetData(val quote: Quote?, val items: List<WidgetItem>)

// Soft cap on the lazy list. Glance LazyColumn is fine with this many on
// stock launchers; OEM hosts occasionally jank past a few hundred entries.
private const val MAX_WIDGET_ITEMS = 80

/**
 * Home-screen widget showing today's quote and every upcoming birthday +
 * anniversary, sorted by days-until and colour-coded by the person's
 * cadence. Glance LazyColumn makes the list scrollable.
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
        val items = people
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
                        id = event.id,
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
            .sortedBy { it.days }
            .take(MAX_WIDGET_ITEMS)
        // Same call the app makes — the cache keeps both in sync across the
        // day, including the in-app shuffle (which invalidates the cache).
        val quote = runCatching { deps.quoteRepository().quoteOfTheDay(today) }.getOrNull()
        return WidgetData(quote = quote, items = items)
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
            // Quote header + a tight inline header label for "Upcoming". The
            // entire block is tappable so the widget still launches the app.
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
        if (data.items.isEmpty()) {
            item {
                Column(modifier = GlanceModifier.fillMaxWidth().clickable(launchActivity)) {
                    Text(
                        text = "No birthdays or anniversaries lined up.",
                        style = TextStyle(color = GlanceTheme.colors.onBackground),
                    )
                }
            }
        } else {
            items(items = data.items, itemId = { it.id }) { item ->
                WidgetEventRow(item = item, onClick = launchActivity)
            }
        }
    }
}

@Composable
private fun WidgetEventRow(item: WidgetItem, onClick: androidx.glance.action.Action) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick),
    ) {
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
