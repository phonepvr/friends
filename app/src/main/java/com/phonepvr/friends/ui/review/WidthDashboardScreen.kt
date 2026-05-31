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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.phonepvr.friends.domain.review.ReviewYear
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

    val selectedYear by reviewViewModel.selectedYear.collectAsStateWithLifecycle()
    val availableYears by reviewViewModel.availableYears.collectAsStateWithLifecycle()
    val includeSilent by reviewViewModel.includeSilent.collectAsStateWithLifecycle()
    val reviewState by reviewViewModel.uiState.collectAsStateWithLifecycle()

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

            // --- Existing call analytics, untouched ---
            item {
                SectionHeader(text = "Call activity")
            }
            item { CallAnalyticsSection() }

            // --- Existing reflection content, no longer behind a toggle ---
            item {
                SectionHeader(text = "Year in review")
            }
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (bonds.size) {
                    1 -> "1 bond needs you"
                    else -> "${bonds.size} bonds need you"
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
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
            .clickable(onClick = onClick),
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
// Shared layout pieces
// ============================================================

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
