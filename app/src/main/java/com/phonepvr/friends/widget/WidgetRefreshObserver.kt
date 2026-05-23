package com.phonepvr.friends.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.phonepvr.friends.data.repository.PeopleRepository
import com.phonepvr.friends.data.repository.TimelineRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
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
        // shareIn(replay = 1) so we don't redundantly re-pull data the first
        // time the combine starts up. The initial emission is dropped — the
        // widget's daily worker already paints from a cold cache, so we only
        // care about *subsequent* changes here.
        val people = peopleRepository.observeActiveWithDetails()
            .shareIn(scope, SharingStarted.Eagerly, replay = 1)
        val timeline = timelineRepository.observeAll()
            .shareIn(scope, SharingStarted.Eagerly, replay = 1)
        combine(people, timeline) { _, _ -> System.currentTimeMillis() }
            .drop(1)
            .debounce(500)
            .onEach { UpcomingWidget().updateAll(context) }
            .launchIn(scope)
    }
}
