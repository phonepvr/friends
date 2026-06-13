package com.phonepvr.friends.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.data.contacts.DuplicateFinder
import com.phonepvr.friends.ui.components.Haptic
import com.phonepvr.friends.ui.components.rememberHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeDuplicatesScreen(
    onBack: () -> Unit,
    viewModel: MergeDuplicatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarState = remember { SnackbarHostState() }
    val haptics = rememberHaptics()
    var pendingMerge by remember { mutableStateOf<DuplicateFinder.Cluster?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Merge duplicates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.clusters.isEmpty() -> Text(
                    text = "No duplicates found.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            text = "Contacts that share a name. Merging links them into " +
                                "one entry; you can unlink later in your contacts app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(state.clusters, key = { it.displayName + it.contactIds }) { cluster ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = cluster.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "${cluster.members.size} copies to merge",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                // Show each copy with its number(s) so the user
                                // can confirm these really are the same person.
                                cluster.members.forEach { member ->
                                    val numbers = member.numbers
                                        .takeIf { it.isNotEmpty() }
                                        ?.joinToString(", ")
                                        ?: "no number"
                                    Text(
                                        text = "• ${member.displayName} — $numbers",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                Button(
                                    onClick = { pendingMerge = cluster },
                                    enabled = !state.merging,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                ) { Text("Merge") }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingMerge?.let { cluster ->
        AlertDialog(
            onDismissRequest = { pendingMerge = null },
            title = { Text("Merge ${cluster.members.size} contacts?") },
            text = {
                Column {
                    Text(
                        "These will be linked into one contact (separable later " +
                            "from your contacts app):",
                    )
                    Spacer(Modifier.height(8.dp))
                    cluster.members.forEach { member ->
                        val numbers = member.numbers
                            .takeIf { it.isNotEmpty() }
                            ?.joinToString(", ")
                            ?: "no number"
                        Text(
                            "• ${member.displayName} — $numbers",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.perform(Haptic.Confirm)
                        viewModel.merge(cluster)
                        pendingMerge = null
                    },
                ) { Text("Merge") }
            },
            dismissButton = {
                TextButton(onClick = { pendingMerge = null }) { Text("Cancel") }
            },
        )
    }
}
