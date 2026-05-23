package com.phonepvr.friends.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.domain.model.InteractionType
import com.phonepvr.friends.domain.review.GapStat
import com.phonepvr.friends.domain.review.ReviewYear
import com.phonepvr.friends.ui.common.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearInReviewScreen(
    onBack: () -> Unit,
    viewModel: YearInReviewViewModel = hiltViewModel(),
) {
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val availableYears by viewModel.availableYears.collectAsStateWithLifecycle()
    val includeSilent by viewModel.includeSilent.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Year in review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            YearChipRow(
                years = availableYears,
                selected = selectedYear,
                onSelect = viewModel::setYear,
            )
            when (val s = state) {
                ReviewUiState.Loading -> Unit
                is ReviewUiState.Sparse -> SparsePlaceholder(year = s.year)
                is ReviewUiState.Loaded -> ReviewBody(
                    review = s.review,
                    includeSilent = includeSilent,
                    onIncludeSilentChange = viewModel::setIncludeSilent,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearChipRow(
    years: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
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
