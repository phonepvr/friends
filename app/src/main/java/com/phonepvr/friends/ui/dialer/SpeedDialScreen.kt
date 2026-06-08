package com.phonepvr.friends.ui.dialer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.ui.components.Haptic
import com.phonepvr.friends.ui.components.rememberHaptics
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedDialScreen(
    onBack: () -> Unit,
    viewModel: SpeedDialViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val assignments by viewModel.assignments.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val snackbarState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var pendingKey by remember { mutableStateOf<Int?>(null) }
    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val key = pendingKey
        pendingKey = null
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && key != null && uri != null) {
            val number = readPickedNumber(context, uri)
            if (number != null) {
                haptics.perform(Haptic.Confirm)
                viewModel.assign(key, number)
            } else {
                scope.launch { snackbarState.showSnackbar("Couldn't read that number") }
            }
        }
    }
    val pick: (Int) -> Unit = { key ->
        pendingKey = key
        val intent = Intent(Intent.ACTION_PICK)
            .setData(ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        runCatching { pickerLauncher.launch(intent) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Speed dial") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Text(
                text = "Long-press a number key on the dialpad to call its speed dial.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            // Keys 1–9. (0 is reserved for the '+' shortcut on the dialpad.)
            (1..9).forEach { key ->
                val number = assignments[key]
                ListItem(
                    headlineContent = { Text("Key $key") },
                    supportingContent = { Text(number ?: "Not set") },
                    trailingContent = if (number != null) {
                        {
                            IconButton(onClick = {
                                haptics.perform(Haptic.Tick)
                                viewModel.clear(key)
                            }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.clickable { pick(key) },
                )
                HorizontalDivider()
            }
        }
    }
}

private fun readPickedNumber(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)?.trim()?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    }.getOrNull()
