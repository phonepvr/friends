package com.phonepvr.friends.ui.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.ui.components.PersonAvatar
import com.phonepvr.friends.ui.permissions.PermissionRationaleSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsBrowserScreen(
    onOpenContact: (contactId: Long, lookupKey: String) -> Unit,
    onCreateContact: () -> Unit,
    bottomBar: @Composable () -> Unit,
    viewModel: ContactsBrowserViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var showRationale by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        permissionDenied = !granted
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(hasPermission) {
        viewModel.onPermissionResult(hasPermission)
    }

    if (showRationale) {
        PermissionRationaleSheet(
            title = "Contacts, so this whole tab works",
            body = "Bondwidth reads your address book to show every contact " +
                "here, and to power search across them when you dial. " +
                "Nothing leaves the device.",
            manualFallback = "If you'd rather not, the People tab still works " +
                "with people you add by hand.",
            grantLabel = "Grant access",
            manualLabel = "Not now",
            onGrant = {
                showRationale = false
                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            },
            onManualFallback = { showRationale = false },
            onDismiss = { showRationale = false },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Contacts") }) },
        bottomBar = bottomBar,
        floatingActionButton = {
            if (hasPermission) {
                FloatingActionButton(onClick = onCreateContact) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = "New contact")
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
                    denied = permissionDenied,
                    onRequest = { showRationale = true },
                )
                state.loading && state.contacts.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                else -> ContactList(
                    state = state,
                    onQueryChange = viewModel::onQueryChange,
                    onFilterChange = viewModel::onFilterChange,
                    onOpenContact = onOpenContact,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactList(
    state: ContactsBrowserUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (ContactsFilterMode) -> Unit,
    onOpenContact: (Long, String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search by name or phone") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (state.query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.filterMode == ContactsFilterMode.ALL,
                onClick = { onFilterChange(ContactsFilterMode.ALL) },
                label = { Text("All (${state.totalCount})") },
            )
            FilterChip(
                selected = state.filterMode == ContactsFilterMode.TRACKED,
                onClick = { onFilterChange(ContactsFilterMode.TRACKED) },
                label = { Text("Bonded (${state.bondedCount})") },
            )
        }
        Spacer(Modifier.height(8.dp))
        if (state.filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val msg = when {
                    state.query.isNotBlank() -> "No contacts match “${state.query}”"
                    state.filterMode == ContactsFilterMode.TRACKED ->
                        "No bonds yet. Open any contact and tap " +
                            "“Add to Bonds” to start."
                    else -> "No contacts on this device."
                }
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        } else {
            // Group the already name-sorted list into A · B · C … sections.
            // groupBy preserves encounter order, so sections come out
            // alphabetically without a re-sort.
            val grouped = remember(state.filtered) {
                state.filtered.groupBy { sectionLetter(it.displayName) }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                grouped.forEach { (letter, contacts) ->
                    stickyHeader(key = "header_$letter") {
                        SectionHeader(letter)
                    }
                    items(
                        items = contacts,
                        key = { it.lookupKey.ifBlank { it.contactId.toString() } },
                        contentType = { "contact" },
                    ) { contact ->
                        ContactRow(
                            contact = contact,
                            onClick = { onOpenContact(contact.contactId, contact.lookupKey) },
                        )
                    }
                }
            }
        }
    }
}

/** First letter of the display name, uppercased; "#" for anything else. */
private fun sectionLetter(displayName: String): String {
    val first = displayName.trim().firstOrNull() ?: return "#"
    return if (first.isLetter()) first.uppercaseChar().toString() else "#"
}

@Composable
private fun SectionHeader(letter: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun ContactRow(contact: BrowseContact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(
            photoRelativePath = contact.photoRelativePath,
            displayName = contact.displayName,
            photoUri = contact.photoUri,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.displayName, style = MaterialTheme.typography.bodyLarge)
            if (!contact.primaryNumber.isNullOrBlank()) {
                Text(
                    text = contact.primaryNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (contact.isTracked) BondedChip()
    }
}

@Composable
private fun BondedChip() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                modifier = Modifier.height(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Bonded",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
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
                "Permission was denied. Grant it in system settings to see " +
                    "your contacts here."
            } else {
                "Bondwidth needs read access to your contacts to show this " +
                    "tab. Nothing leaves the device."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onRequest) { Text("Grant access") }
    }
}
