package com.phonepvr.friends.ui.dialer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.data.dialer.CallPlacer
import com.phonepvr.friends.ui.components.PersonAvatar
import com.phonepvr.friends.ui.permissions.PermissionRationaleSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialpadScreen(
    onClose: () -> Unit,
    onOpenContact: (Long) -> Unit,
    onSaveNumber: (String) -> Unit,
    viewModel: DialpadViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var hasContacts by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasCallPhone by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var showRationale by remember { mutableStateOf(false) }
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }

    // Routes the actual placement, showing the SIM chooser first on a
    // multi-SIM device with no default. Single-SIM users go straight through.
    val simLauncher = rememberSimCallLauncher(
        accounts = viewModel::callCapableAccounts,
        needsChoice = viewModel::needsSimChoice,
        call = { number, account ->
            val result = viewModel.place(number, account)
            if (result == CallPlacer.PlaceResult.NO_PERMISSION) {
                scope.launch { snackbarState.showSnackbar("Grant Call permission to dial.") }
            }
        },
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        hasContacts = grants[Manifest.permission.READ_CONTACTS] == true || hasContacts
        hasCallPhone = grants[Manifest.permission.CALL_PHONE] == true || hasCallPhone
        viewModel.onContactsPermissionState(hasContacts)
        pendingCallNumber?.let { number ->
            pendingCallNumber = null
            if (hasCallPhone) simLauncher.launch(number)
        }
    }

    LaunchedEffect(hasContacts) {
        viewModel.onContactsPermissionState(hasContacts)
    }

    LaunchedEffect(state.placeError) {
        state.placeError?.let { msg ->
            snackbarState.showSnackbar(msg)
            viewModel.dismissPlaceError()
        }
    }

    if (showRationale) {
        PermissionRationaleSheet(
            title = "Permissions to dial",
            body = "Bondwidth needs: read access to your contacts (so " +
                "typing digits surfaces who's behind the number) and " +
                "permission to place calls. Both on-device.",
            manualFallback = "Skip and the dialpad still works — you just " +
                "won't see contact matches, and dialing won't go through " +
                "without Call permission.",
            grantLabel = "Grant access",
            manualLabel = "Not now",
            onGrant = {
                showRationale = false
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.CALL_PHONE,
                    ),
                )
            },
            onManualFallback = { showRationale = false },
            onDismiss = { showRationale = false },
        )
    }

    val placeCall: (String) -> Unit = { number ->
        if (hasCallPhone) {
            simLauncher.launch(number)
        } else {
            pendingCallNumber = number
            showRationale = true
        }
    }

    val speedDial by viewModel.speedDial.collectAsStateWithLifecycle()
    // Which key we're picking a contact for, so the picker callback knows
    // where to store the chosen number.
    var pendingAssignKey by remember { mutableStateOf<Int?>(null) }
    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val key = pendingAssignKey
        pendingAssignKey = null
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && key != null && uri != null) {
            val number = readPickedPhoneNumber(context, uri)
            if (number != null) {
                viewModel.setSpeedDial(key, number)
                scope.launch { snackbarState.showSnackbar("Speed dial $key set") }
            } else {
                scope.launch { snackbarState.showSnackbar("Couldn't read that number") }
            }
        }
    }

    // Long-press a digit: dial its speed-dial number if assigned, otherwise
    // open the contact picker to assign one. (0 keeps its '+' long-press.)
    val onLongPressKey: (Char) -> Unit = { digit ->
        val key = digit.digitToIntOrNull()
        if (key != null && key in 1..9) {
            val assigned = speedDial[key]
            if (assigned != null) {
                placeCall(assigned)
            } else {
                pendingAssignKey = key
                val pick = Intent(Intent.ACTION_PICK)
                    .setData(ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                runCatching { contactPickerLauncher.launch(pick) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dialpad") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close dialpad")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) { Snackbar(it) } },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            InputBar(input = state.input, onBackspace = viewModel::onBackspace)
            Box(modifier = Modifier.weight(1f)) {
                if (state.matches.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = state.matches,
                            key = { it.contactId },
                            contentType = { "match" },
                        ) { match ->
                            MatchRow(
                                match = match,
                                onCall = {
                                    placeCall(
                                        match.matchedNumber.ifBlank { state.input },
                                    )
                                },
                                onOpen = { onOpenContact(match.contactId) },
                            )
                        }
                    }
                } else if (state.input.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "No matching contacts. Hit Call to dial directly.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        // Only worth offering "Save as new contact" when the
                        // typed number is plausibly real (at least 3 digits)
                        // — same threshold the matcher uses to avoid noise.
                        if (state.input.count(Char::isDigit) >= 3) {
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = { onSaveNumber(state.input) },
                            ) {
                                Icon(
                                    Icons.Filled.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Save '${state.input}' as new contact")
                            }
                        }
                    }
                }
            }
            DialpadGrid(
                onDigit = viewModel::onDigit,
                onLongPressKey = onLongPressKey,
                speedDialKeys = speedDial.keys,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                FloatingActionButton(
                    onClick = {
                        val target = state.matches.firstOrNull()?.matchedNumber
                            ?.takeIf { it.isNotBlank() }
                            ?: state.input
                        placeCall(target)
                    },
                ) {
                    Icon(Icons.Filled.Call, contentDescription = "Call")
                }
            }
        }
    }
}

@Composable
private fun InputBar(input: String, onBackspace: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = input.ifEmpty { " " },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.End,
        )
        if (input.isNotEmpty()) {
            IconButton(onClick = onBackspace) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                )
            }
        }
    }
}

@Composable
private fun MatchRow(
    match: DialpadMatch,
    onCall: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(
            photoRelativePath = match.photoRelativePath,
            displayName = match.displayName,
            photoUri = match.photoUri,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(match.displayName, style = MaterialTheme.typography.bodyLarge)
            if (match.matchedNumber.isNotBlank()) {
                Text(
                    text = match.matchedNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onCall) {
            Icon(Icons.Filled.Call, contentDescription = "Call")
        }
    }
}

@Composable
private fun DialpadGrid(
    onDigit: (Char) -> Unit,
    onLongPressKey: (Char) -> Unit,
    speedDialKeys: Set<Int>,
) {
    // Long-press '0' enters '+' (international prefix), matching every other
    // dialer in the world. Long-press 1–9 triggers speed dial.
    val rows = listOf(
        listOf(KeyDef('1', ""), KeyDef('2', "ABC"), KeyDef('3', "DEF")),
        listOf(KeyDef('4', "GHI"), KeyDef('5', "JKL"), KeyDef('6', "MNO")),
        listOf(KeyDef('7', "PQRS"), KeyDef('8', "TUV"), KeyDef('9', "WXYZ")),
        listOf(KeyDef('*', ""), KeyDef('0', "+", longPressDigit = '+'), KeyDef('#', "")),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (key in row) {
                    val digit = key.digit
                    val longPressDigit = key.longPressDigit
                    val digitValue = digit.digitToIntOrNull()
                    val hasSpeedDial = digitValue != null && digitValue in speedDialKeys
                    // '0' keeps its '+' shortcut; 1–9 trigger speed dial.
                    val longClick: (() -> Unit)? = when {
                        longPressDigit != null -> ({ onDigit(longPressDigit) })
                        digit in '1'..'9' -> ({ onLongPressKey(digit) })
                        else -> null
                    }
                    DialpadKey(
                        key = key,
                        modifier = Modifier.weight(1f),
                        hasSpeedDial = hasSpeedDial,
                        onClick = { onDigit(digit) },
                        onLongClick = longClick,
                    )
                }
            }
        }
    }
}

private data class KeyDef(
    val digit: Char,
    val letters: String,
    /** When set, long-pressing the key enters this character instead. */
    val longPressDigit: Char? = null,
)

/**
 * Reads the phone NUMBER from the URI returned by the system contact picker
 * (ACTION_PICK on Phone.CONTENT_URI points straight at a phone-data row). The
 * picker grants temporary read on that URI, so no contacts permission of our
 * own is required. Returns null on any provider error.
 */
private fun readPickedPhoneNumber(context: Context, uri: android.net.Uri): String? =
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialpadKey(
    key: KeyDef,
    modifier: Modifier = Modifier,
    hasSpeedDial: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val view = LocalView.current
    Surface(
        modifier = modifier
            .height(64.dp)
            .combinedClickable(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                },
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = key.digit.toString(),
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
            )
            if (key.letters.isNotEmpty()) {
                Text(
                    text = key.letters,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (hasSpeedDial) {
                // A small dot hints "this key has a speed-dial assignment"
                // when there are no letters to show (the '1' key).
                Text(
                    text = "•",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
