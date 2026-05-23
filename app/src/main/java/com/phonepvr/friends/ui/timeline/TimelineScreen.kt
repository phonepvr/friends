package com.phonepvr.friends.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.domain.model.InteractionType
import com.phonepvr.friends.ui.common.formatTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onOpenPerson: (Long) -> Unit,
    bottomBar: @Composable () -> Unit,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val feedItems by viewModel.items.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val filterId by viewModel.filterPersonId.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Timeline") }) },
        bottomBar = bottomBar,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                val filterName = people.firstOrNull { it.id == filterId }?.displayName
                AssistChip(
                    onClick = { menuOpen = true },
                    label = { Text("Showing: ${filterName ?: "Everyone"}") },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Everyone") },
                        onClick = {
                            viewModel.setFilter(null)
                            menuOpen = false
                        },
                    )
                    people.forEach { person ->
                        DropdownMenuItem(
                            text = { Text(person.displayName) },
                            onClick = {
                                viewModel.setFilter(person.id)
                                menuOpen = false
                            },
                        )
                    }
                }
            }

            if (feedItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No interactions logged yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(feedItems, key = { it.entry.id }) { item ->
                        TimelineFeedRow(
                            item = item,
                            onClick = { onOpenPerson(item.entry.personId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineFeedRow(item: TimelineItem, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.personName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${interactionLabel(item.entry.type)} · " +
                    formatTimestamp(item.entry.occurredAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val note = item.entry.note
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
