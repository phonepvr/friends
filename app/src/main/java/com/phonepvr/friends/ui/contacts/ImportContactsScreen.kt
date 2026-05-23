package com.phonepvr.friends.ui.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.ui.permissions.PermissionRationaleSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportContactsScreen(
    onDone: () -> Unit,
    viewModel: ImportContactsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rationaleAlreadyShown by viewModel.rationaleAlreadyShown.collectAsStateWithLifecycle()

    val requestedPermissions = remember {
        arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG)
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        // Contacts is the only one that gates this screen; call log is best-
        // effort so the auto-sync can find anything to pull. Declining call
        // log doesn't break the import.
        hasPermission = grants[Manifest.permission.READ_CONTACTS] == true
        permissionRequested = true
    }

    val onRequestPermission: () -> Unit = {
        if (rationaleAlreadyShown) {
            permissionLauncher.launch(requestedPermissions)
        } else {
            showRationale = true
        }
    }

    if (showRationale) {
        PermissionRationaleSheet(
            title = "Two doors, asked once",
            body = "Contacts — to pull names, numbers, photos, birthdays " +
                "and anniversaries straight in instead of typing them. " +
                "Call log — so calls with the people you import auto-fill " +
                "the timeline in the background, no per-profile tapping. " +
                "Both reads, both stay on this phone, nothing leaves.",
            manualFallback = "Decline either or both and the app still " +
                "works. You'll just add people by hand and log calls " +
                "yourself.",
            grantLabel = "Grant access",
            manualLabel = "I'll do it by hand",
            onGrant = {
                viewModel.markRationaleShown()
                showRationale = false
                permissionLauncher.launch(requestedPermissions)
            },
            onManualFallback = {
                viewModel.markRationaleShown()
                showRationale = false
            },
            onDismiss = {
                viewModel.markRationaleShown()
                showRationale = false
            },
        )
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.loadContacts()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import from contacts") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (hasPermission && state.selectedIds.isNotEmpty() && !state.importing) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.importSelected(onDone) },
                ) {
                    Text("Import ${state.selectedIds.size}")
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                !hasPermission -> PermissionPrompt(
                    denied = permissionRequested,
                    onRequest = onRequestPermission,
                )

                state.loading || state.importing -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                state.contacts.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No contacts found on this device.")
                }

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("Search by name or phone") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        trailingIcon = if (state.query.isNotEmpty()) {
                            {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                }
                            }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    if (state.filtered.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No contacts match “${state.query}”",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(state.filtered, key = { it.contactId }) { contact ->
                                ContactRow(
                                    name = contact.displayName,
                                    subtitle = contact.phoneNumbers.firstOrNull(),
                                    selected = contact.contactId in state.selectedIds,
                                    onToggle = { viewModel.toggleSelection(contact.contactId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    name: String,
    subtitle: String?,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionPrompt(denied: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Contacts permission needed", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (denied) {
                "Permission was denied. Grant it in system settings, " +
                    "or go back and add people manually."
            } else {
                "Bondwidth needs read access to your contacts to import people. " +
                    "Nothing leaves your device."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Grant access") }
    }
}
