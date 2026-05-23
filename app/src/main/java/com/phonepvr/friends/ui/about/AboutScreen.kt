package com.phonepvr.friends.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & about") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 16.dp, vertical = 16.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ElevatedCard {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Nothing on this screen leaves your phone.",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "Friends is completely offline. No accounts. No analytics. " +
                            "No tracking. Nothing about you is sent to a server, " +
                            "because there is no server.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "The app doesn't hold the internet permission at all — the " +
                            "manifest explicitly removes it, so we couldn't phone " +
                            "home if we wanted to.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Your people, dates, timeline and notes live in the app's " +
                            "private database on this device. They only ever leave " +
                            "via the backup file YOU choose to export, when YOU " +
                            "choose to export it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Permissions are opt-in. Contacts and the call log are " +
                            "asked for in plain English, and you can decline either " +
                            "without losing access to the app — you just add and " +
                            "log everything by hand instead.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (versionName.isNotBlank()) {
                Text(
                    text = "Version $versionName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
