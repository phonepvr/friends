package com.phonepvr.friends.ui.review

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.domain.cadence.CadenceState
import com.phonepvr.friends.domain.model.InteractionType
import com.phonepvr.friends.domain.review.BondHealth
import com.phonepvr.friends.domain.review.GapStat
import com.phonepvr.friends.domain.review.HealthSnapshot
import com.phonepvr.friends.domain.review.HealthWithTrend
import com.phonepvr.friends.domain.review.Momentum
import com.phonepvr.friends.domain.review.MomentumDay
import com.phonepvr.friends.domain.review.ReviewYear
import com.phonepvr.friends.domain.review.SlippingBond
import com.phonepvr.friends.ui.common.formatDate
import com.phonepvr.friends.ui.components.PersonAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidthDashboardScreen(
    onOpenPerson: (Long) -> Unit,
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    dashboardViewModel: WidthDashboardViewModel = hiltViewModel(),
    reviewViewModel: YearInReviewViewModel = hiltViewModel(),
) {
    val health by dashboardViewModel.health.collectAsStateWithLifecycle()
    val needsYou by dashboardViewModel.needsYou.collectAsStateWithLifecycle()
    val momentum by dashboardViewModel.momentum.collectAsStateWithLifecycle()
    val slipping by dashboardViewModel.slipping.collectAsStateWithLifecycle()

    val selectedYear by reviewViewModel.selectedYear.collectAsStateWithLifecycle()
    val availableYears by reviewViewModel.availableYears.collectAsStateWithLifecycle()
    val includeSilent by reviewViewModel.includeSilent.collectAsStateWithLifecycle()
    val reviewState by reviewViewModel.uiState.collectAsStateWithLifecycle()
    var reviewExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Width") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- Phase 1: relationship-health hero + needs-you carousel ---
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    HealthHeroCard(health = health)
                }
            }
            if (needsYou.isNotEmpty()) {
                item {
                    NeedsYouCarousel(
                        bonds = needsYou,
                        onOpenPerson = onOpenPerson,
                    )
                }
            }
            if (slipping.isNotEmpty()) {
                item {
                    GoingQuietSection(
                        bonds = slipping,
                        onOpenPerson = onOpenPerson,
                    )
                }
            }
            momentum?.takeIf { it.hasActivity }?.let { m ->
                item { MomentumSection(momentum = m) }
            }

            // --- Existing call analytics, untouched ---
            item {
                SectionHeader(text = "Call activity")
            }
            item { CallAnalyticsSection(onOpenPerson = onOpenPerson) }

            // --- Year in review: a reflective deep-dive, tucked behind a
            // "More" expander so the actionable dashboard above stays the
            // focus. Collapsed by default. ---
            item {
                ExpanderHeader(
                    text = "Year in review",
                    expanded = reviewExpanded,
                    onToggle = { reviewExpanded = !reviewExpanded },
                )
            }
            if (reviewExpanded) {
                item {
                    YearChipRow(
                        years = availableYears,
                        selected = selectedYear,
                        onSelect = reviewViewModel::setYear,
                    )
                }
                when (val s = reviewState) {
                    ReviewUiState.Loading -> Unit
                    is ReviewUiState.Sparse -> item { SparsePlaceholder(year = s.year) }
                    is ReviewUiState.Loaded -> item {
                        ReviewBody(
                            review = s.review,
                            includeSilent = includeSilent,
                            onIncludeSilentChange = reviewViewModel::setIncludeSilent,
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// Connection Health hero
// ============================================================

@Composable
private fun HealthHeroCard(health: HealthWithTrend?) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "CONNECTION HEALTH",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (health == null) {
                // First-load placeholder reads as "loading" without a spinner.
                Text(
                    text = "—",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Gathering your bonds…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            HealthScoreRow(snapshot = health.now, delta = health.deltaScore)
            Spacer(Modifier.height(12.dp))
            HealthBar(score = health.now.score)
            Spacer(Modifier.height(12.dp))
            HealthBreakdownRow(snapshot = health.now)
        }
    }
}

@Composable
private fun HealthScoreRow(snapshot: HealthSnapshot, delta: Int) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = snapshot.score.toString(),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = " / 100",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Spacer(Modifier.weight(1f))
        // Trend chip is only meaningful once there's data to compare against.
        if (snapshot.trackedCount > 0) {
            TrendChip(delta = delta)
        }
    }
}

@Composable
private fun TrendChip(delta: Int) {
    val (label, container, content, icon) = when {
        delta > 0 -> TrendStyle(
            label = "+$delta vs last month",
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = Icons.Filled.ArrowUpward,
        )
        delta < 0 -> TrendStyle(
            label = "$delta vs last month",
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            icon = Icons.Filled.ArrowDownward,
        )
        else -> TrendStyle(
            label = "Same as last month",
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = Icons.Filled.TrendingFlat,
        )
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
            )
        }
    }
}

private data class TrendStyle(
    val label: String,
    val container: Color,
    val content: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
private fun HealthBar(score: Int) {
    val target = (score.coerceIn(0, 100)) / 100f
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(700),
        label = "healthbar",
    )
    val color = when {
        score >= 75 -> MaterialTheme.colorScheme.primary
        score >= 50 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(color),
        )
    }
}

@Composable
private fun HealthBreakdownRow(snapshot: HealthSnapshot) {
    if (snapshot.trackedCount == 0) {
        Text(
            text = "Set a cadence on a bond to start tracking.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        BreakdownPill(
            count = snapshot.onTrackCount,
            label = "on track",
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        BreakdownPill(
            count = snapshot.dueSoonCount,
            label = "due soon",
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(6.dp))
        BreakdownPill(
            count = snapshot.overdueCount,
            label = "overdue",
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun BreakdownPill(count: Int, label: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ============================================================
// "Bonds need you" carousel
// ============================================================

@Composable
private fun NeedsYouCarousel(
    bonds: List<BondHealth>,
    onOpenPerson: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BlockTitle(
            text = when (bonds.size) {
                1 -> "1 bond needs you"
                else -> "${bonds.size} bonds need you"
            },
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(bonds, key = { it.personId }) { bond ->
                NeedsYouCard(bond = bond, onClick = { onOpenPerson(bond.personId) })
            }
        }
    }
}

@Composable
private fun NeedsYouCard(bond: BondHealth, onClick: () -> Unit) {
    val (urgencyLabel, urgencyTint) = urgencyFor(bond)
    ElevatedCard(
        modifier = Modifier
            .width(132.dp)
            // One spoken node ("Alice, 5d overdue") with a clear tap action,
            // instead of TalkBack reading avatar/name/chip as separate items.
            .semantics(mergeDescendants = true) {
                contentDescription = "${bond.displayName}, $urgencyLabel"
            }
            .clickable(
                onClickLabel = "Open ${bond.displayName}",
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PersonAvatar(
                photoRelativePath = bond.photoRelativePath,
                displayName = bond.displayName,
                diameter = 56.dp,
            )
            Text(
                text = bond.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Surface(
                color = urgencyTint.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = urgencyLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = urgencyTint,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun urgencyFor(bond: BondHealth): Pair<String, Color> {
    val days = bond.cadence.daysUntilDue
    return when (bond.cadence.state) {
        CadenceState.OVERDUE -> {
            val over = -(days ?: 0L)
            val text = when {
                over <= 0L -> "Due today"
                over == 1L -> "1d overdue"
                else -> "${over}d overdue"
            }
            text to MaterialTheme.colorScheme.error
        }
        CadenceState.DUE_SOON -> {
            val text = when (days) {
                null, 0L -> "Due today"
                1L -> "Due tomorrow"
                else -> "Due in ${days}d"
            }
            text to MaterialTheme.colorScheme.tertiary
        }
        CadenceState.NEVER_CONTACTED -> "Never spoken" to MaterialTheme.colorScheme.primary
        CadenceState.ON_TRACK -> "On track" to MaterialTheme.colorScheme.primary
        CadenceState.NOT_TRACKED -> "Not tracked" to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

// ============================================================
// "Going quiet" early-warning section
// ============================================================

@Composable
private fun GoingQuietSection(
    bonds: List<SlippingBond>,
    onOpenPerson: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BlockTitle(text = "Going quiet")
        Spacer(Modifier.height(8.dp))
        ElevatedCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
        ) {
            Column {
                bonds.forEachIndexed { index, bond ->
                    SlippingRow(bond = bond, onClick = { onOpenPerson(bond.personId) })
                    if (index < bonds.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 68.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SlippingRow(bond: SlippingBond, onClick: () -> Unit) {
    val detail = "Usually every ~${bond.baselineGapDays}d · quiet ${bond.openGapDays}d"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${bond.displayName}, $detail"
            }
            .clickable(
                onClickLabel = "Open ${bond.displayName}",
                role = Role.Button,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(
            photoRelativePath = bond.photoRelativePath,
            displayName = bond.displayName,
            diameter = 40.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bond.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ============================================================
// Momentum strip (last 14 days)
// ============================================================

@Composable
private fun MomentumSection(momentum: Momentum) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BlockTitle(text = "Your momentum")
        Spacer(Modifier.height(8.dp))
        ElevatedCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                MomentumBars(momentum)
                Spacer(Modifier.height(12.dp))
                MomentumStats(momentum)
            }
        }
    }
}

@Composable
private fun MomentumBars(momentum: Momentum) {
    val maxCount = momentum.maxCount.coerceAtLeast(1)
    val spoken = momentumA11yLabel(momentum)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            // The bars are a visual; give TalkBack one concise summary instead.
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        momentum.days.forEach { day ->
            MomentumBar(
                day = day,
                fraction = day.count.toFloat() / maxCount.toFloat(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MomentumBar(day: MomentumDay, fraction: Float, modifier: Modifier) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "momentumBar",
    )
    val barColor = when {
        day.count == 0 -> MaterialTheme.colorScheme.surfaceVariant
        day.isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    }
    // Empty days keep a thin track so the rhythm is still legible.
    val heightFraction = if (day.count == 0) 0.06f else (0.12f + 0.88f * animated)
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(heightFraction)
                .clip(RoundedCornerShape(3.dp))
                .background(barColor),
        )
    }
}

@Composable
private fun MomentumStats(momentum: Momentum) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "${momentum.last7Count} this week",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (momentum.weekDelta != 0) {
            Spacer(Modifier.width(8.dp))
            WeekDeltaChip(momentum.weekDelta)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "Active ${momentum.activeDays}/${momentum.windowDays}d",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeekDeltaChip(delta: Int) {
    val up = delta > 0
    val tint = if (up) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (up) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = "${if (up) "+" else ""}$delta vs last week",
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}

private fun momentumA11yLabel(m: Momentum): String {
    val deltaPart = when {
        m.weekDelta > 0 -> ", up ${m.weekDelta} from the week before"
        m.weekDelta < 0 -> ", down ${-m.weekDelta} from the week before"
        else -> ""
    }
    return "Contact activity, last ${m.windowDays} days: " +
        "${m.last7Count} in the last week$deltaPart. " +
        "Active on ${m.activeDays} of ${m.windowDays} days."
}

// ============================================================
// Shared layout pieces
// ============================================================

@Composable
private fun BlockTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

/** A [SectionHeader] that toggles a collapsible block; chevron flips on expand. */
@Composable
private fun ExpanderHeader(text: String, expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(250),
        label = "expanderChevron",
    )
    Column {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = if (expanded) "Collapse $text" else "Expand $text",
                    role = Role.Button,
                    onClick = onToggle,
                )
                .defaultMinSize(minHeight = 48.dp)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

// ============================================================
// Year in Review (was the second segmented mode; now a section)
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearChipRow(
    years: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(years, key = { it }) { year ->
            FilterChip(
                selected = year == selected,
                onClick = { onSelect(year) },
                label = { Text(year.toString()) },
            )
        }
    }
}

@Composable
private fun SparsePlaceholder(year: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Not enough data yet for $year", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Log a few more interactions and check back later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReviewBody(
    review: ReviewYear,
    includeSilent: Boolean,
    onIncludeSilentChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            label = "Interactions logged",
            value = review.totalInteractions.toString(),
            supporting = "${review.contactInteractions} counted toward staying in touch",
        )
        review.mostConnected?.let {
            StatCard(
                label = "Most connected",
                value = it.displayName,
                supporting = "${it.count} interactions",
            )
        }
        StatCardWithToggle(
            label = "Least connected (tracked)",
            value = review.leastConnectedTracked?.let { "${it.displayName} (${it.count})" }
                ?: "—",
            toggleLabel = "Include never-contacted",
            toggleChecked = includeSilent,
            onToggle = onIncludeSilentChange,
        )
        review.longestGap?.let { LongestGapCard(it) }
        if (review.totalEvents > 0) {
            StatCard(
                label = "Events",
                value = "${review.acknowledgedEventCount} of ${review.totalEvents}",
                supporting = "acknowledged with a same-day interaction",
            )
        }
        TypeBreakdownCard(review.typeBreakdown, total = review.totalInteractions)
    }
}

@Composable
private fun StatCard(label: String, value: String, supporting: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatCardWithToggle(
    label: String,
    value: String,
    toggleLabel: String,
    toggleChecked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    toggleLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = toggleChecked, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
private fun LongestGapCard(gap: GapStat) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Longest gap",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("${gap.days} days", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "${gap.displayName} — ${formatDate(gap.fromDate)}" +
                    " → ${formatDate(gap.toDate)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TypeBreakdownCard(breakdown: Map<InteractionType, Int>, total: Int) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "By type",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            InteractionType.entries.forEach { type ->
                val count = breakdown[type] ?: 0
                val pct = if (total > 0) count * 100 / total else 0
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = typeLabel(type),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "$count   ${pct}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private fun typeLabel(type: InteractionType): String = when (type) {
    InteractionType.CALL -> "Calls"
    InteractionType.MEET -> "Meet-ups"
    InteractionType.MESSAGE -> "Messages"
    InteractionType.OTHER -> "Other"
}
