package com.phonepvr.friends.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.domain.cadence.CadenceState
import com.phonepvr.friends.domain.cadence.CadenceStatus
import com.phonepvr.friends.domain.quotes.Quote
import com.phonepvr.friends.ui.components.PersonAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleListScreen(
    onAddPerson: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    onImportContacts: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBackup: () -> Unit,
    bottomBar: @Composable () -> Unit,
    viewModel: PeopleListViewModel = hiltViewModel(),
) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showBackupNudge by viewModel.showBackupNudge.collectAsStateWithLifecycle()
    val todayQuote by viewModel.todayQuote.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friends") },
                actions = {
                    TextButton(onClick = onImportContacts) { Text("Import") }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPerson) {
                Icon(Icons.Filled.Add, contentDescription = "Add person")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (showBackupNudge) {
                BackupNudgeBanner(
                    onOpenBackup = onOpenBackup,
                    onDismiss = viewModel::dismissBackupNudge,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                label = { Text("Search") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                todayQuote?.let { quote ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        QuoteCard(quote)
                    }
                }
                if (people.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState()
                    }
                } else {
                    items(people, key = { it.person.person.id }) { item ->
                        PersonCard(
                            item = item,
                            onClick = { onOpenPerson(item.person.person.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuoteCard(quote: Quote) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = quote.text,
                style = MaterialTheme.typography.bodyLarge,
            )
            quote.attribution?.let { attribution ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "— $attribution",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PersonCard(item: PersonListItem, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PersonAvatar(
                photoRelativePath = item.person.person.photoRelativePath,
                displayName = item.person.person.displayName,
                diameter = 56.dp,
            )
            Text(
                text = item.person.person.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            CadenceSubtitle(item.cadence)
        }
    }
}

@Composable
private fun CadenceSubtitle(cadence: CadenceStatus) {
    val (text, color) = when (cadence.state) {
        CadenceState.OVERDUE ->
            ("Overdue by ${-(cadence.daysUntilDue ?: 0)}d" to MaterialTheme.colorScheme.error)
        CadenceState.DUE_SOON ->
            ("Due in ${cadence.daysUntilDue ?: 0}d" to MaterialTheme.colorScheme.tertiary)
        CadenceState.ON_TRACK ->
            ("On track · ${cadence.daysSinceLastContact ?: 0}d ago" to
                MaterialTheme.colorScheme.primary)
        CadenceState.NEVER_CONTACTED ->
            ("Not yet" to MaterialTheme.colorScheme.primary)
        CadenceState.NOT_TRACKED ->
            ("No cadence" to MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BackupNudgeBanner(
    onOpenBackup: () -> Unit,
    onDismiss: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onOpenBackup)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Time to back up", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "It's been a while since your last backup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpenBackup) { Text("Back up") }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No people yet", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Tap + to add someone you want to stay in touch with.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
