package com.phonepvr.friends.ui.dialer

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.DrawableRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextOverflow
import com.phonepvr.friends.R
import com.phonepvr.friends.data.db.entity.FavouriteContactEntity
import com.phonepvr.friends.data.dialer.CallPlacer
import com.phonepvr.friends.domain.model.CallType
import com.phonepvr.friends.ui.components.CallTypeBadge
import com.phonepvr.friends.ui.components.Haptic
import com.phonepvr.friends.ui.components.PersonAvatar
import com.phonepvr.friends.ui.components.rememberHaptics
import com.phonepvr.friends.ui.permissions.PermissionRationaleSheet
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    onOpenContact: (Long) -> Unit,
    onOpenDialpad: () -> Unit,
    onOpenHistory: (number: String) -> Unit,
    onSaveNumber: (number: String) -> Unit,
    bottomBar: @Composable () -> Unit,
    viewModel: DialerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    var hasCallLog by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
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

    // Shows the SIM chooser first on a multi-SIM device with no default;
    // single-SIM users dial straight through.
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
        hasCallLog = grants[Manifest.permission.READ_CALL_LOG] == true || hasCallLog
        hasContacts = grants[Manifest.permission.READ_CONTACTS] == true || hasContacts
        hasCallPhone = grants[Manifest.permission.CALL_PHONE] == true || hasCallPhone
        viewModel.onPermissionState(hasCallLog, hasContacts)
        pendingCallNumber?.let { number ->
            pendingCallNumber = null
            if (hasCallPhone) simLauncher.launch(number)
        }
    }

    LaunchedEffect(hasCallLog, hasContacts) {
        viewModel.onPermissionState(hasCallLog, hasContacts)
    }

    if (showRationale) {
        PermissionRationaleSheet(
            title = "A few phone permissions",
            body = "Bondwidth needs: read access to your call log (to show " +
                "recents), read access to your contacts (to label the " +
                "numbers), and permission to place calls when you tap one. " +
                "All on-device.",
            manualFallback = "If you decline, this tab still opens but " +
                "stays empty until you grant access in system settings.",
            grantLabel = "Grant access",
            manualLabel = "Not now",
            onGrant = {
                showRationale = false
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.CALL_PHONE,
                    ),
                )
            },
            onManualFallback = { showRationale = false },
            onDismiss = { showRationale = false },
        )
    }

    var lastDialedNumber by remember { mutableStateOf<String?>(null) }
    val placeCall: (String) -> Unit = { number ->
        lastDialedNumber = number
        if (hasCallPhone) {
            simLauncher.launch(number)
        } else {
            pendingCallNumber = number
            showRationale = true
        }
    }

    var sheetEntry by remember { mutableStateOf<RecentEntry?>(null) }
    var numberPicker by remember { mutableStateOf<CallTarget.Pick?>(null) }
    var confirmDeleteEntry by remember { mutableStateOf<RecentEntry?>(null) }
    var deleteAfterPermission by remember { mutableStateOf<Long?>(null) }

    // Deleting a call writes to the log; request WRITE_CALL_LOG, then delete
    // the confirmed call once it's granted.
    val writeCallLogPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val id = deleteAfterPermission
        deleteAfterPermission = null
        if (granted && id != null) viewModel.deleteCall(id)
    }

    numberPicker?.let { pick ->
        NumberPickerSheet(
            pick = pick,
            onPick = { number ->
                numberPicker = null
                placeCall(number)
            },
            onDismiss = { numberPicker = null },
        )
    }

    sheetEntry?.let { entry ->
        val canBlock = remember { viewModel.canBlock() }
        // Loads asynchronously the first time the sheet opens for this
        // number. Null hides the row; true / false picks Unblock vs Block.
        var blockedStatus by remember(entry.number) { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(entry.number) {
            blockedStatus = if (canBlock && entry.number.isNotBlank()) {
                viewModel.isBlocked(entry.number)
            } else {
                null
            }
        }
        // App-install checks are cheap PackageManager lookups; memoize per
        // sheet open so we don't re-query on every recomposition. The
        // <queries> block in AndroidManifest declares both packages so
        // these resolve correctly on Android 11+.
        val hasWhatsApp = remember(context) { isAppInstalled(context, PKG_WHATSAPP) }
        val hasSignal = remember(context) { isAppInstalled(context, PKG_SIGNAL) }
        RecentActionsSheet(
            entry = entry,
            blockedStatus = blockedStatus,
            hasWhatsApp = hasWhatsApp,
            hasSignal = hasSignal,
            onDismiss = { sheetEntry = null },
            // Only a real call-log entry (non-zero id) can be deleted; the
            // sheet is also reused for search-result entries (id 0).
            onDelete = if (entry.id != 0L) {
                { sheetEntry = null; confirmDeleteEntry = entry }
            } else {
                null
            },
            onCall = {
                sheetEntry = null
                placeCall(entry.number)
            },
            onMessage = {
                sheetEntry = null
                openSms(context, entry.number)
            },
            onWhatsApp = {
                sheetEntry = null
                openWhatsApp(context, entry.number)
            },
            onSignal = {
                sheetEntry = null
                openSignal(context, entry.number)
            },
            onCopy = {
                sheetEntry = null
                copyToClipboard(context, entry.number)
                scope.launch { snackbarState.showSnackbar("Number copied") }
            },
            onOpenContact = entry.contactId?.let { id ->
                { sheetEntry = null; onOpenContact(id) }
            },
            onAddContact = if (entry.contactId == null) {
                { sheetEntry = null; onSaveNumber(entry.number) }
            } else {
                null
            },
            onToggleBlock = {
                val number = entry.number
                val nextBlocked = !(blockedStatus ?: false)
                if (nextBlocked) haptics.perform(Haptic.Reject)
                sheetEntry = null
                scope.launch {
                    val ok = viewModel.setBlocked(number, nextBlocked)
                    val msg = when {
                        !ok -> "Couldn't update the block list"
                        nextBlocked -> "$number blocked"
                        else -> "$number unblocked"
                    }
                    snackbarState.showSnackbar(msg)
                }
            },
        )
    }

    confirmDeleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmDeleteEntry = null },
            title = { Text("Delete this call?") },
            text = {
                Text(
                    "Remove this call with ${entry.displayName ?: entry.number} from " +
                        "your call log. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = entry.id
                    confirmDeleteEntry = null
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_CALL_LOG,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel.deleteCall(id)
                    } else {
                        deleteAfterPermission = id
                        writeCallLogPermissionLauncher.launch(Manifest.permission.WRITE_CALL_LOG)
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteEntry = null }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Calls") }) },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarState) { Snackbar(it) } },
        floatingActionButton = {
            // Scale-in animation on mount makes the FAB feel less abrupt.
            AnimatedVisibility(
                visible = true,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                FloatingActionButton(onClick = onOpenDialpad) {
                    Icon(Icons.Filled.Dialpad, contentDescription = "Open dialpad")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // A failed call attempt surfaces a persistent, dismissible banner.
            // (The old behaviour flashed a snackbar that auto-dismissed before
            // it could be read.) Retry re-dials the last attempted number.
            state.placeError?.let { msg ->
                DialerErrorBanner(
                    message = msg,
                    onRetry = lastDialedNumber?.let { number -> { placeCall(number) } },
                    onDismiss = { viewModel.dismissPlaceError() },
                )
            }
            // In-tab contact search: visible whenever contacts are readable,
            // so users can call by name without opening the dialpad. While the
            // query is non-empty, search results replace the recents list.
            if (hasContacts) {
                DialerSearchBar(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                )
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Crossfade between the permission gate and the recents list —
            // smoother than a hard swap after the user grants access.
            Crossfade(
                targetState = hasCallLog,
                label = "calls-permission",
            ) { granted ->
                if (!granted) {
                    EmptyArea(
                        message = "Bondwidth needs the call-log permission to show your recents.",
                        actionLabel = "Grant access",
                        onAction = { showRationale = true },
                    )
                } else if (state.query.isNotBlank()) {
                    ContactSearchResults(
                        results = state.contactResults,
                        onCallResult = { result ->
                            // Route through the same resolver recents use: it
                            // loads the contact's real numbers by id and either
                            // dials directly or surfaces the picker. Reuses the
                            // synthesized RecentEntry so there's one code path.
                            scope.launch {
                                val target = viewModel.resolveCallTarget(
                                    result.toRecentEntry(),
                                )
                                when (target) {
                                    is CallTarget.Direct -> placeCall(target.number)
                                    is CallTarget.Pick -> numberPicker = target
                                }
                            }
                        },
                        onOpenContact = onOpenContact,
                        onLongPress = { result ->
                            // The existing sheet (Call / Message / WhatsApp /
                            // Signal / Copy / View / Block) works as-is.
                            haptics.perform(Haptic.LongPress)
                            sheetEntry = result.toRecentEntry()
                        },
                    )
                } else {
                    RecentsContent(
                        state = state,
                        onCallEntry = { entry ->
                            // Resolve which number to dial; multi-number
                            // contacts with no default tagged surface a picker.
                            scope.launch {
                                val target = viewModel.resolveCallTarget(entry)
                                when (target) {
                                    is CallTarget.Direct -> placeCall(target.number)
                                    is CallTarget.Pick -> numberPicker = target
                                }
                            }
                        },
                        onOpenHistory = onOpenHistory,
                        onOpenContact = onOpenContact,
                        onAddToContacts = onSaveNumber,
                        onCallFavourite = { fav -> placeCall(fav.primaryNumber) },
                        onLongPress = { entry ->
                            haptics.perform(Haptic.LongPress)
                            sheetEntry = entry
                        },
                    )
                }
            }
            }
        }
    }
}

/**
 * A search result reuses the recents row's machinery (long-press sheet +
 * call resolver) by presenting itself as a synthetic RecentEntry. Only the
 * identity fields matter here; the call-type/timestamp/duration are unused
 * for these paths.
 */
private fun ContactSearchResult.toRecentEntry(): RecentEntry = RecentEntry(
    number = primaryNumber.orEmpty(),
    displayName = displayName,
    contactId = contactId,
    isTracked = isTracked,
    photoRelativePath = photoRelativePath,
    photoUri = photoUri,
    type = CallType.OUTGOING,
    timestampMillis = 0L,
    durationSeconds = 0L,
)

private fun openSms(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_SENDTO, "smsto:$number".toUri())
    runCatching { context.startActivity(intent) }
}

private const val PKG_WHATSAPP = "com.whatsapp"
private const val PKG_SIGNAL = "org.thoughtcrime.securesms"

private fun isAppInstalled(context: Context, pkg: String): Boolean = runCatching {
    context.packageManager.getPackageInfo(pkg, 0)
    true
}.getOrDefault(false)

private fun openWhatsApp(context: Context, number: String) {
    // wa.me wants digits only with no leading + (per WhatsApp's docs); we
    // pin setPackage so it goes to WhatsApp directly instead of the chooser.
    val digits = number.filter(Char::isDigit)
    val intent = Intent(Intent.ACTION_VIEW, "https://wa.me/$digits".toUri())
        .setPackage(PKG_WHATSAPP)
    runCatching { context.startActivity(intent) }
}

private fun openSignal(context: Context, number: String) {
    // Signal expects E.164 (leading + and digits). Preserve a leading + if
    // typed; otherwise prepend it so signal.me routes correctly.
    val digits = number.filter(Char::isDigit)
    val e164 = if (digits.isEmpty()) return else "+$digits"
    val intent = Intent(Intent.ACTION_VIEW, "https://signal.me/#p/$e164".toUri())
        .setPackage(PKG_SIGNAL)
    runCatching { context.startActivity(intent) }
}

private fun copyToClipboard(context: Context, number: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Phone number", number))
}


@Composable
private fun RecentsContent(
    state: DialerUiState,
    onCallEntry: (RecentEntry) -> Unit,
    onOpenHistory: (String) -> Unit,
    onOpenContact: (Long) -> Unit,
    onAddToContacts: (String) -> Unit,
    onCallFavourite: (FavouriteContactEntity) -> Unit,
    onLongPress: (RecentEntry) -> Unit,
) {
    if (!state.recentsLoaded && state.favourites.isEmpty()) {
        EmptyArea(message = "Loading…")
        return
    }
    if (state.recents.isEmpty() && state.favourites.isEmpty()) {
        EmptyArea(message = "No calls in the last 30 days.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (state.favourites.isNotEmpty()) {
            item(key = "__favourites_strip", contentType = "favourites") {
                FavouritesStrip(
                    favourites = state.favourites,
                    photoByLookupKey = state.favouritePhotoByLookupKey,
                    onCallFavourite = onCallFavourite,
                )
            }
        }
        items(
            items = state.recents,
            key = { "${it.timestampMillis}-${it.number}" },
            contentType = { "recent" },
        ) { entry ->
            RecentRow(
                entry = entry,
                onCall = { onCallEntry(entry) },
                onOpenHistory = { onOpenHistory(entry.number) },
                onOpenContact = entry.contactId
                    ?.let { id -> { onOpenContact(id) } }
                    ?: { onOpenHistory(entry.number) },
                onAddToContacts = { onAddToContacts(entry.number) },
                onLongPress = { onLongPress(entry) },
            )
        }
    }
}

@Composable
private fun DialerSearchBar(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search contacts by name or number") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        } else {
            null
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactSearchResults(
    results: List<ContactSearchResult>,
    onCallResult: (ContactSearchResult) -> Unit,
    onOpenContact: (Long) -> Unit,
    onLongPress: (ContactSearchResult) -> Unit,
) {
    if (results.isEmpty()) {
        EmptyArea(message = "No matching contacts.")
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = results, key = { it.contactId }, contentType = { "search" }) { r ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpenContact(r.contactId) },
                        onLongClick = { onLongPress(r) },
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonAvatar(
                    photoRelativePath = r.photoRelativePath,
                    displayName = r.displayName,
                    photoUri = r.photoUri,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = r.displayName, style = MaterialTheme.typography.bodyLarge)
                    r.primaryNumber?.takeIf { it.isNotBlank() }?.let { number ->
                        Text(
                            text = number,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { onCallResult(r) }) {
                    Icon(Icons.Filled.Call, contentDescription = "Call ${r.displayName}")
                }
            }
        }
    }
}

@Composable
private fun FavouritesStrip(
    favourites: List<FavouriteContactEntity>,
    photoByLookupKey: Map<String, String>,
    onCallFavourite: (FavouriteContactEntity) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Favourites",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp),
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                items = favourites,
                key = { it.lookupKey },
                contentType = { "fav" },
            ) { fav ->
                FavouriteTile(
                    fav = fav,
                    photoUri = photoByLookupKey[fav.lookupKey],
                    onClick = { onCallFavourite(fav) },
                )
            }
        }
    }
}

@Composable
private fun FavouriteTile(
    fav: FavouriteContactEntity,
    photoUri: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PersonAvatar(
            photoRelativePath = fav.photoRelativePath,
            displayName = fav.displayName,
            diameter = 56.dp,
            photoUri = photoUri,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = fav.displayName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(
    entry: RecentEntry,
    onCall: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenContact: () -> Unit,
    onAddToContacts: () -> Unit,
    onLongPress: () -> Unit,
) {
    // Four touch zones — avatar opens the contact card, the middle column
    // opens the call history (long-press = action sheet), the "+" saves an
    // unknown number, and the call button dials with multi-number picker
    // when the contact has more than one and no default is tagged.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.clickable(onClick = onOpenContact),
        ) {
            PersonAvatar(
                photoRelativePath = entry.photoRelativePath,
                displayName = entry.displayName ?: entry.number,
                photoUri = entry.photoUri,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(onClick = onOpenHistory, onLongClick = onLongPress)
                .padding(vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CallTypeBadge(type = entry.type, size = 22.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.displayName ?: entry.number,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (entry.type == CallType.MISSED) FontWeight.SemiBold
                    else FontWeight.Normal,
                )
                if (entry.isTracked) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Text(
                text = secondaryLine(entry),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Unknown numbers get a quick "+" → system's create-new / save-to-existing
        // chooser; known numbers don't need it.
        if (entry.contactId == null) {
            IconButton(onClick = onAddToContacts) {
                Icon(
                    Icons.Filled.PersonAdd,
                    contentDescription = "Add to contacts",
                )
            }
        }
        IconButton(onClick = onCall) {
            Icon(Icons.Filled.Call, contentDescription = "Call back")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberPickerSheet(
    pick: CallTarget.Pick,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Call ${pick.displayName}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Text(
                text = "Pick a number — tag one as default on the contact " +
                    "to skip this step next time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            pick.phones.forEach { phone ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(phone.number) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null)
                    Spacer(Modifier.width(20.dp))
                    Text(
                        text = phone.number,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentActionsSheet(
    entry: RecentEntry,
    blockedStatus: Boolean?,
    hasWhatsApp: Boolean,
    hasSignal: Boolean,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onWhatsApp: () -> Unit,
    onSignal: () -> Unit,
    onCopy: () -> Unit,
    onOpenContact: (() -> Unit)?,
    onAddContact: (() -> Unit)?,
    onToggleBlock: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PersonAvatar(
                    photoRelativePath = entry.photoRelativePath,
                    displayName = entry.displayName ?: entry.number,
                    photoUri = entry.photoUri,
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = entry.displayName ?: entry.number,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (entry.displayName != null) {
                        Text(
                            text = entry.number,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider()
            SheetAction(Icons.Filled.Call, "Call", onCall)
            SheetAction(Icons.AutoMirrored.Filled.Message, "Message", onMessage)
            // WhatsApp / Signal only show when the package is actually
            // installed — tapping otherwise would just snackbar a failure.
            if (hasWhatsApp) {
                SheetActionPainter(R.drawable.ic_brand_whatsapp, "WhatsApp", onWhatsApp)
            }
            if (hasSignal) {
                SheetActionPainter(R.drawable.ic_brand_signal, "Signal", onSignal)
            }
            SheetAction(Icons.Filled.ContentCopy, "Copy number", onCopy)
            onOpenContact?.let { SheetAction(Icons.Filled.Person, "View contact", it) }
            onAddContact?.let { SheetAction(Icons.Filled.PersonAdd, "Add to contacts", it) }
            // Block / Unblock — hidden when the device can't block (tablet,
            // not default dialer) or while the current state is still loading.
            blockedStatus?.let { blocked ->
                SheetAction(
                    icon = Icons.Filled.Block,
                    label = if (blocked) "Unblock this number" else "Block this number",
                    onClick = onToggleBlock,
                )
            }
            onDelete?.let {
                SheetAction(Icons.Filled.Delete, "Delete from call log", it)
            }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Same as [SheetAction] but for vector drawables (brand icons). */
@Composable
private fun SheetActionPainter(@DrawableRes iconRes: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

// Cached so we don't allocate a DateFormat per Recents row per recomposition.
// DateFormat instances aren't thread-safe, but Compose recomposition runs on
// the main thread so a single shared instance is fine here.
private val timestampFormat: DateFormat by lazy {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
}

private fun formatTimestamp(millis: Long): String = timestampFormat.format(Date(millis))

/**
 * Second line of a recents row: the dialled number plus the time when we
 * resolved a contact name (so the number is always visible), or just the
 * time for an unknown number that's already shown as the title.
 */
private fun secondaryLine(entry: RecentEntry): String {
    val time = formatTimestamp(entry.timestampMillis)
    return if (entry.displayName != null && entry.number.isNotBlank()) {
        "${entry.number} · $time"
    } else {
        time
    }
}

/**
 * Persistent, dismissible error banner for a failed call attempt. Replaces a
 * self-dismissing snackbar so a placement failure stays visible until the user
 * acts. [onRetry] is null when there's no number to re-dial (no Retry shown).
 */
@Composable
private fun DialerErrorBanner(
    message: String,
    onRetry: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text("Retry")
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss error",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyArea(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
