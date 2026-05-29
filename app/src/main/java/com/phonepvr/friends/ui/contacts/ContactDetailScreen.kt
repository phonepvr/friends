package com.phonepvr.friends.ui.contacts

import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.data.contacts.ContactDate
import com.phonepvr.friends.ui.components.PersonAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    onBack: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    viewModel: ContactDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.details?.displayName ?: "Contact") },
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
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.notFound -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Contact not found.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    val d = state.details ?: return@Box
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Header(
                            displayName = d.displayName,
                            photoRelativePath = state.photoRelativePath,
                            isTracked = state.isTracked,
                            mutating = state.mutating,
                            onToggle = viewModel::toggleTracked,
                            onOpenPerson = state.trackedPersonId
                                ?.takeIf { state.isTracked }
                                ?.let { id -> { onOpenPerson(id) } },
                        )
                        if (d.phoneNumbers.isNotEmpty()) {
                            SectionLabel("Phone")
                            d.phoneNumbers.forEach { number ->
                                PhoneRow(
                                    number = number,
                                    onCall = {
                                        val intent = Intent(
                                            Intent.ACTION_DIAL,
                                            "tel:$number".toUri(),
                                        )
                                        context.startActivity(intent)
                                    },
                                )
                            }
                        }
                        d.birthday?.let {
                            DateRow(
                                label = "Birthday",
                                date = it,
                                icon = Icons.Filled.Cake,
                            )
                        }
                        d.anniversary?.let {
                            DateRow(
                                label = "Anniversary",
                                date = it,
                                icon = Icons.Filled.CalendarMonth,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    displayName: String,
    photoRelativePath: String?,
    isTracked: Boolean,
    mutating: Boolean,
    onToggle: () -> Unit,
    onOpenPerson: (() -> Unit)?,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PersonAvatar(
            photoRelativePath = photoRelativePath,
            displayName = displayName,
            diameter = 96.dp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (isTracked) {
                Button(
                    onClick = onToggle,
                    enabled = !mutating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Tracked — tap to stop")
                }
            } else {
                Button(onClick = onToggle, enabled = !mutating) {
                    Icon(Icons.Filled.FavoriteBorder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Track in Bondwidth")
                }
            }
            if (onOpenPerson != null) {
                OutlinedButton(onClick = onOpenPerson) { Text("Open profile") }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PhoneRow(number: String, onCall: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCall)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Call, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Text(number, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DateRow(label: String, date: ContactDate, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(formatDate(date), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun formatDate(date: ContactDate): String {
    val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
    val monthName = months.getOrNull(date.month - 1) ?: date.month.toString()
    return if (date.year != null) "$monthName ${date.day}, ${date.year}" else "$monthName ${date.day}"
}
