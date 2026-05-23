package com.phonepvr.friends.ui.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.remember
import com.phonepvr.friends.data.db.entity.EventEntity
import com.phonepvr.friends.data.db.entity.TimelineEntryEntity
import com.phonepvr.friends.data.db.relation.PersonWithDetails
import com.phonepvr.friends.ui.components.PersonAvatar
import com.phonepvr.friends.domain.cadence.CadenceState
import com.phonepvr.friends.domain.cadence.CadenceStatus
import com.phonepvr.friends.domain.model.AnnualDate
import com.phonepvr.friends.domain.model.EventType
import com.phonepvr.friends.domain.model.InteractionType
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onLogInteraction: (Long) -> Unit,
    viewModel: PersonDetailViewModel = hiltViewModel(),
) {
    val person by viewModel.person.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val cadence by viewModel.cadence.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(person?.person?.displayName ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { onEdit(viewModel.personId) }) { Text("Edit") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onLogInteraction(viewModel.personId) },
            ) {
                Text("Log interaction")
            }
        },
    ) { padding ->
        val current = person
        if (current == null) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Loading…")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { PersonHeader(current) }
                item { CadenceCard(cadence) }
                item {
                    InfoSection(
                        person = current,
                        today = today,
                        onMarkWished = viewModel::markAsWished,
                    )
                }
                item { Text("History", style = MaterialTheme.typography.titleMedium) }
                if (timeline.isEmpty()) {
                    item {
                        Text(
                            text = "No interactions logged yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(timeline, key = { it.id }) { entry -> TimelineRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun PersonHeader(person: PersonWithDetails) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        PersonAvatar(
            photoRelativePath = person.person.photoRelativePath,
            displayName = person.person.displayName,
            diameter = 96.dp,
        )
    }
}

@Composable
private fun CadenceCard(cadence: CadenceStatus) {
    val text = when (cadence.state) {
        CadenceState.NOT_TRACKED -> "No contact cadence set"
        CadenceState.NEVER_CONTACTED -> "No interactions logged yet"
        CadenceState.ON_TRACK ->
            "On track" + (cadence.daysSinceLastContact?.let { " · last contact $it days ago" }
                ?: "")
        CadenceState.DUE_SOON ->
            "Due soon" + (cadence.daysUntilDue?.let { " · $it days left" } ?: "")
        CadenceState.OVERDUE ->
            "Overdue" + (cadence.daysUntilDue?.let { " by ${-it} days" } ?: "")
    }
    val containerColor = when (cadence.state) {
        CadenceState.OVERDUE -> MaterialTheme.colorScheme.errorContainer
        CadenceState.DUE_SOON -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun InfoSection(
    person: PersonWithDetails,
    today: LocalDate,
    onMarkWished: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        person.person.relationshipTag
            ?.takeIf { it.isNotBlank() }
            ?.let { InfoRow("Relationship", it) }
        person.phoneNumbers.forEach { phone ->
            InfoRow(phone.label?.takeIf { it.isNotBlank() } ?: "Phone", phone.rawNumber)
        }
        person.events.forEach { event ->
            EventRow(
                event = event,
                today = today,
                onMarkWished = { onMarkWished(eventLabel(event.type).lowercase()) },
            )
        }
        person.person.cadenceTargetDays?.let {
            InfoRow("Stay in touch", "every $it days")
        }
        person.person.notes
            ?.takeIf { it.isNotBlank() }
            ?.let { InfoRow("Notes", it) }
    }
}

@Composable
private fun EventRow(
    event: EventEntity,
    today: LocalDate,
    onMarkWished: () -> Unit,
) {
    val daysUntil = AnnualDate(event.month, event.day, event.year).daysUntilNextOccurrence(today)
    // Show the action when the event is today, in the next 7 days, or in the
    // past 7 days (where daysUntil wraps to a high value near 365).
    val showWishButton = daysUntil <= 7L || daysUntil >= 358L
    if (!showWishButton) {
        InfoRow(eventLabel(event.type), formatEventDate(event))
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eventLabel(event.type),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = formatEventDate(event), style = MaterialTheme.typography.bodyLarge)
        }
        OutlinedButton(onClick = onMarkWished) { Text("Mark as wished") }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TimelineRow(entry: TimelineEntryEntity) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${interactionLabel(entry.type)} · ${formatTimestamp(entry.occurredAt)}",
                style = MaterialTheme.typography.titleSmall,
            )
            val note = entry.note
            if (!note.isNullOrBlank()) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun interactionLabel(type: InteractionType): String =
    type.name.lowercase().replaceFirstChar { it.uppercase() }

private fun eventLabel(type: EventType): String = when (type) {
    EventType.BIRTHDAY -> "Birthday"
    EventType.WEDDING_ANNIVERSARY -> "Anniversary"
    EventType.CUSTOM -> "Date"
}

private fun formatEventDate(event: EventEntity): String {
    val monthName = Month.of(event.month).getDisplayName(TextStyle.FULL, Locale.getDefault())
    return buildString {
        append(monthName)
        append(' ')
        append(event.day)
        event.year?.let { append(", ").append(it) }
    }
}

private fun formatTimestamp(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val monthName = date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$monthName ${date.dayOfMonth}, ${date.year}"
}
