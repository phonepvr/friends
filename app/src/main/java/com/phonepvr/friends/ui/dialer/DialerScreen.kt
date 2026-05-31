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
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextOverflow
import com.phonepvr.friends.data.db.entity.FavouriteContactEntity
import com.phonepvr.friends.data.dialer.CallPlacer
import com.phonepvr.friends.domain.model.CallType
import com.phonepvr.friends.ui.components.PersonAvatar
import com.phonepvr.friends.ui.permissions.PermissionRationaleSheet
import kotlinx.coroutines.CoroutineScope
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        hasCallLog = grants[Manifest.permission.READ_CALL_LOG] == true || hasCallLog
        hasContacts = grants[Manifest.permission.READ_CONTACTS] == true || hasContacts
        hasCallPhone = grants[Manifest.permission.CALL_PHONE] == true || hasCallPhone
        viewModel.onPermissionState(hasCallLog, hasContacts)
        pendingCallNumber?.let { number ->
            pendingCallNumber = null
            if (hasCallPhone) tryPlace(viewModel, number, snackbarState, scope)
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
            tryPlace(viewModel, number, snackbarState, scope)
        } else {
            pendingCallNumber = number
            showRationale = true
        }
    }

    var sheetEntry by remember { mutableStateOf<RecentEntry?>(null) }
    var numberPicker by remember { mutableStateOf<CallTarget.Pick?>(null) }

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
        RecentActionsSheet(
            entry = entry,
            blockedStatus = blockedStatus,
            onDismiss = { sheetEntry = null },
            onCall = {
                sheetEntry = null
                placeCall(entry.number)
            },
            onMessage = {
                sheetEntry = null
                openSms(context, entry.number)
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
                        onLongPress = { entry -> sheetEntry = entry },
                    )
                }
            }
            }
        }
    }
}

private fun openSms(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_SENDTO, "smsto:$number".toUri())
    runCatching { context.startActivity(intent) }
}

private fun copyToClipboard(context: Context, number: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Phone number", number))
}

private fun tryPlace(
    viewModel: DialerViewModel,
    number: String,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
) {
    val result = viewModel.place(number)
    if (result == CallPlacer.PlaceResult.NO_PERMISSION) {
        scope.launch { snackbarHostState.showSnackbar("Grant Call permission to dial.") }
    }
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
                Icon(
                    imageVector = entry.type.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = entry.type.tint(),
                )
                Spacer(Modifier.width(6.dp))
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
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onCopy: () -> Unit,
    onOpenContact: (() -> Unit)?,
    onAddContact: (() -> Unit)?,
    onToggleBlock: () -> Unit,
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

private fun CallType.icon(): ImageVector = when (this) {
    CallType.INCOMING -> Icons.Filled.CallReceived
    CallType.OUTGOING -> Icons.Filled.CallMade
    CallType.MISSED, CallType.REJECTED -> Icons.Filled.CallMissed
}

@Composable
private fun CallType.tint(): Color = when (this) {
    CallType.INCOMING, CallType.OUTGOING ->
        MaterialTheme.colorScheme.onSurfaceVariant
    CallType.MISSED, CallType.REJECTED ->
        MaterialTheme.colorScheme.error
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
