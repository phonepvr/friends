package com.phonepvr.friends.ui.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.data.contacts.ParsedVCard
import com.phonepvr.friends.ui.permissions.PermissionRationaleSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportVCardScreen(
    onDone: () -> Unit,
    viewModel: ImportVCardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_CONTACTS,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var showRationale by remember { mutableStateOf(false) }
    var importAfterGrant by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (granted && importAfterGrant) {
            importAfterGrant = false
            viewModel.confirmImport()
        }
    }

    val startImport: () -> Unit = {
        if (hasPermission) {
            viewModel.confirmImport()
        } else {
            importAfterGrant = true
            showRationale = true
        }
    }

    // Auto-close a beat after a successful import so the user lands back where
    // they came from (the email / file app) rather than on a dead screen.
    LaunchedEffect(state) {
        if (state is ImportVCardState.Done) {
            kotlinx.coroutines.delay(1200)
            onDone()
        }
    }

    if (showRationale) {
        PermissionRationaleSheet(
            title = "Write contacts, to import these",
            body = "Bondwidth needs permission to add contacts to your phone. " +
                "The imported entries appear in your system Contacts app too. " +
                "Nothing leaves the device.",
            manualFallback = "If you'd rather not, close this and import the " +
                "file in your usual contacts app instead.",
            grantLabel = "Grant access",
            manualLabel = "Cancel",
            onGrant = {
                showRationale = false
                permissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
            },
            onManualFallback = {
                showRationale = false
                importAfterGrant = false
            },
            onDismiss = {
                showRationale = false
                importAfterGrant = false
            },
        )
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Import contacts") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (val s = state) {
                ImportVCardState.Loading -> CenterStatus("Reading file…", spinner = true)
                is ImportVCardState.Error -> CenterStatus(s.message, spinner = false)
                is ImportVCardState.Importing -> CenterStatus(
                    "Adding ${s.done + 1} of ${s.total}…",
                    spinner = true,
                )
                is ImportVCardState.Done -> CenterStatus(
                    if (s.skipped > 0) {
                        "${s.imported} added · ${s.skipped} skipped"
                    } else if (s.imported == 1) {
                        "1 contact added"
                    } else {
                        "${s.imported} contacts added"
                    },
                    spinner = false,
                )
                is ImportVCardState.Preview -> PreviewList(
                    cards = s.cards,
                    onImport = startImport,
                )
            }
        }
    }
}

@Composable
private fun PreviewList(cards: List<ParsedVCard>, onImport: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = if (cards.size == 1) {
                "1 contact in this file"
            } else {
                "${cards.size} contacts in this file"
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(cards) { card ->
                ContactPreviewCard(card)
            }
        }
        Button(
            onClick = onImport,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(if (cards.size == 1) "Add contact" else "Add ${cards.size} contacts")
        }
    }
}

@Composable
private fun ContactPreviewCard(card: ParsedVCard) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = card.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            card.organization?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            card.phones.forEach { phone ->
                Spacer(Modifier.height(4.dp))
                IconLine(icon = Icons.Filled.Phone, text = phone)
            }
            card.emails.forEach { email ->
                Spacer(Modifier.height(4.dp))
                IconLine(icon = Icons.Filled.Email, text = email)
            }
        }
    }
}

@Composable
private fun IconLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.width(20.dp).height(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CenterStatus(message: String, spinner: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (spinner) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
