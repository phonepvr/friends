package com.phonepvr.friends.ui.review

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.domain.review.BucketSummary
import com.phonepvr.friends.domain.review.CallAnalyticsResult
import com.phonepvr.friends.domain.review.CallBucket
import com.phonepvr.friends.domain.review.CallPartyStat
import com.phonepvr.friends.ui.components.PersonAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallAnalyticsSection(
    onOpenPerson: (Long) -> Unit,
    viewModel: CallAnalyticsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val window by viewModel.selectedWindow.collectAsStateWithLifecycle()

    var hasCallLog by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCallLog = granted
        viewModel.onCallLogPermission(granted)
    }
    androidx.compose.runtime.LaunchedEffect(hasCallLog) {
        viewModel.onCallLogPermission(hasCallLog)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(AnalyticsWindow.entries.toList(), key = { it.name }) { w ->
                FilterChip(
                    selected = w == window,
                    onClick = { viewModel.setWindow(w) },
                    label = { Text(w.label) },
                )
            }
        }

        when (val s = state) {
            CallAnalyticsUiState.Loading -> Unit
            CallAnalyticsUiState.NoPermission -> PermissionCard(
                onGrant = { permissionLauncher.launch(Manifest.permission.READ_CALL_LOG) },
            )
            is CallAnalyticsUiState.Ready -> {
                if (s.result.isEmpty) {
                    EmptyCard(window)
                } else {
                    Cards(s.result, onOpenPerson)
                }
            }
        }
    }
}

@Composable
private fun Cards(result: CallAnalyticsResult, onOpenPerson: (Long) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TalkTimeCard(result)
        BondsVsOthersCard(result)
        BondedReachCard(result)
        if (result.topParties.isNotEmpty()) TopCallersCard(result.topParties, onOpenPerson)
        DirectionAndMissedCard(result, onOpenPerson)
        OthersBreakdownCard(result)
    }
}

@Composable
private fun TalkTimeCard(result: CallAnalyticsResult) {
    // Total seconds across the analytics window, split bond vs other so the
    // user can see where the minutes actually went. All from existing fields
    // on CallAnalyticsResult — no new computation.
    val others = combine(result.contact, result.unknown)
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardLabel("Talk time")
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatTalkTime(result.totalDurationSec),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "across ${result.totalCalls} calls",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiniStat(
                    label = "Bonded",
                    value = formatTalkTime(result.bond.totalDurationSec),
                    modifier = Modifier.weight(1f),
                )
                MiniStat(
                    label = "Other",
                    value = formatTalkTime(others.totalDurationSec),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** "4h 23m", "12m", "44s" — friendly, no padding zeroes. */
private fun formatTalkTime(seconds: Long): String {
    if (seconds <= 0) return "0s"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> if (minutes == 0L) "${hours}h" else "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${secs}s"
    }
}

@Composable
private fun BondsVsOthersCard(result: CallAnalyticsResult) {
    val others = combine(result.contact, result.unknown)
    val maxDuration = maxOf(result.bond.totalDurationSec, others.totalDurationSec, 1L)
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardLabel("Bonds vs everyone else")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Talk time over the window",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ComparisonBar(
                label = "Bonds",
                valueLabel = "${formatTalk(result.bond.totalDurationSec)} · " +
                    "${result.bond.callCount} calls",
                fraction = result.bond.totalDurationSec.toFloat() / maxDuration,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            ComparisonBar(
                label = "Others",
                valueLabel = "${formatTalk(others.totalDurationSec)} · " +
                    "${others.callCount} calls",
                fraction = others.totalDurationSec.toFloat() / maxDuration,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(12.dp))
            val bondShare = if (result.totalDurationSec > 0) {
                (result.bond.totalDurationSec * 100 / result.totalDurationSec).toInt()
            } else {
                0
            }
            Text(
                text = "$bondShare% of your talk time was with your bonds.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ComparisonBar(
    label: String,
    valueLabel: String,
    fraction: Float,
    color: Color,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "bar",
    )
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun BondedReachCard(result: CallAnalyticsResult) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardLabel("Bonded reach")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${result.bondedReachedCount} of ${result.bondedTotalCount} bonds",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            val fraction = if (result.bondedTotalCount > 0) {
                result.bondedReachedCount.toFloat() / result.bondedTotalCount
            } else {
                0f
            }
            val animated by animateFloatAsState(
                targetValue = fraction.coerceIn(0f, 1f),
                animationSpec = tween(600),
                label = "reach",
            )
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(8.dp))
            val silent = (result.bondedTotalCount - result.bondedReachedCount).coerceAtLeast(0)
            Text(
                text = if (silent == 0) {
                    "You called every bond in this window. "
                } else {
                    "$silent ${if (silent == 1) "bond hasn't" else "bonds haven't"} " +
                        "heard from you by call in this window."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TopCallersCard(parties: List<CallPartyStat>, onOpenPerson: (Long) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardLabel("Top callers")
            Spacer(Modifier.height(8.dp))
            parties.forEach { party ->
                val name = party.displayName ?: party.number
                // Bonded callers carry a personId → tap through to their
                // profile. Unknown numbers stay static (nowhere to go).
                val personId = party.personId
                val rowModifier = if (personId != null) {
                    Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$name, ${party.callCount} calls"
                        }
                        .clickable(
                            onClickLabel = "Open $name",
                            role = Role.Button,
                            onClick = { onOpenPerson(personId) },
                        )
                        .padding(vertical = 6.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                }
                Row(
                    modifier = rowModifier,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PersonAvatar(
                        photoRelativePath = party.photoRelativePath,
                        displayName = party.displayName ?: party.number,
                        diameter = 36.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = party.displayName ?: party.number,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (party.bucket == CallBucket.BOND) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Filled.Favorite,
                                    contentDescription = "Bonded",
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(
                            text = "${party.callCount} calls",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatTalk(party.totalDurationSec),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectionAndMissedCard(result: CallAnalyticsResult, onOpenPerson: (Long) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardLabel("Calls in & out")
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MiniStat("Incoming", result.incomingTotal.toString(), Modifier.weight(1f))
                MiniStat("Outgoing", result.outgoingTotal.toString(), Modifier.weight(1f))
                MiniStat("Missed", result.missedTotal.toString(), Modifier.weight(1f))
            }
            // Only bonds whose *latest* call is still an unreturned miss show
            // here — once you've called back or spoken since, they drop off.
            if (result.bondsToCallBack.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Worth a call back",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(6.dp))
                result.bondsToCallBack.forEach { bond ->
                    CallBackRow(bond = bond, onOpenPerson = onOpenPerson)
                }
            }
        }
    }
}

@Composable
private fun CallBackRow(bond: CallPartyStat, onOpenPerson: (Long) -> Unit) {
    val name = bond.displayName ?: bond.number
    val rowModifier = bond.personId?.let { id ->
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$name, missed call worth returning"
            }
            .clickable(
                onClickLabel = "Open $name",
                role = Role.Button,
                onClick = { onOpenPerson(id) },
            )
    } ?: Modifier.fillMaxWidth()
    Row(
        modifier = rowModifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(
            photoRelativePath = bond.photoRelativePath,
            displayName = name,
            diameter = 36.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.CallMissed,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun OthersBreakdownCard(result: CallAnalyticsResult) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardLabel("Who you're spending it with")
            Spacer(Modifier.height(8.dp))
            BreakdownRow("Bonds", result.bond)
            BreakdownRow("Saved contacts", result.contact)
            BreakdownRow("Unknown numbers", result.unknown)
        }
    }
}

@Composable
private fun BreakdownRow(label: String, summary: BucketSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text = "${formatTalk(summary.totalDurationSec)} · ${summary.callCount} calls",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CardLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PermissionCard(onGrant: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Call analytics need the call-log permission.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Everything's computed on-device — nothing leaves your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onGrant) { Text("Grant access") }
        }
    }
}

@Composable
private fun EmptyCard(window: AnalyticsWindow) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "No calls in the last ${window.label.lowercase()}.",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun combine(a: BucketSummary, b: BucketSummary): BucketSummary = BucketSummary(
    callCount = a.callCount + b.callCount,
    totalDurationSec = a.totalDurationSec + b.totalDurationSec,
    uniqueParties = a.uniqueParties + b.uniqueParties,
)

private fun formatTalk(seconds: Long): String {
    if (seconds <= 0) return "0m"
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "${hours}h ${mins}m"
        mins > 0 -> "${mins}m"
        else -> "${secs}s"
    }
}
