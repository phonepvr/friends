package com.phonepvr.friends.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.phonepvr.friends.data.repository.PeopleRepository
import com.phonepvr.friends.data.repository.TimelineRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes the home-screen widget to refresh any time the underlying data
 * changes — events, timeline entries, cadence updates, contact imports, all
 * of it. Subscribes to the existing repository Flows (which Room's
 * InvalidationTracker re-emits on every relevant table mutation) and pings
 * the widget once per change, debounced so a bulk import only triggers a
 * single redraw.
 *
 * The refresh fires an explicit ACTION_APPWIDGET_UPDATE broadcast to
 * UpcomingWidgetReceiver rather than calling Glance's `updateAll()` —
 * `updateAll` is observed not to redraw the existing widget on some
 * launchers, while the receiver broadcast reliably routes through
 * GlanceAppWidgetReceiver.onUpdate → glanceAppWidget.update → fresh
 * provideGlance for each live instance.
 *
 * The daily worker stays in place as a midnight-rollover safety net.
 */
@Singleton
class WidgetRefreshObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val peopleRepository: PeopleRepository,
    private val timelineRepository: TimelineRepository,
) {
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        combine(
            peopleRepository.observeActiveWithDetails(),
            timelineRepository.observeAll(),
        ) { _, _ -> System.currentTimeMillis() }
            .drop(1)
            // Short debounce so single-row edits feel instant; bulk
            // imports still coalesce because Room's InvalidationTracker
            // fires per-transaction.
            .debounce(150)
            .onEach { runCatching { refreshUpcomingWidgets(context) } }
            .launchIn(scope)
    }
}

/**
 * Shared refresh entrypoint for the home-screen widget. Sends an explicit
 * ACTION_APPWIDGET_UPDATE broadcast to [UpcomingWidgetReceiver], which
 * Glance's base class routes through to `glanceAppWidget.update(...)` for
 * every live instance — bypassing `GlanceAppWidget.updateAll(context)`,
 * which fails to redraw on some launchers.
 */
fun refreshUpcomingWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val component = ComponentName(context, UpcomingWidgetReceiver::class.java)
    val ids = manager.getAppWidgetIds(component)
    if (ids.isEmpty()) return
    val intent = Intent(context, UpcomingWidgetReceiver::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
    }
    context.sendBroadcast(intent)
}
