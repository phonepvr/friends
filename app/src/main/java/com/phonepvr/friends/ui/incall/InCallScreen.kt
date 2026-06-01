package com.phonepvr.friends.ui.incall

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AddIcCall
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.phonepvr.friends.data.incall.CallAudioRoute
import com.phonepvr.friends.data.incall.CallDirection
import com.phonepvr.friends.data.incall.CallSimpleState
import com.phonepvr.friends.data.incall.CallSnapshot
import com.phonepvr.friends.domain.cadence.CadenceState
import com.phonepvr.friends.domain.cadence.CadenceStatus
import com.phonepvr.friends.ui.components.PersonAvatar
import com.phonepvr.friends.ui.theme.LocalCallColors
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun InCallScreen(
    state: InCallUiState,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onRejectWith: (String) -> Unit,
    onBlockReject: () -> Unit,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onSetAudioRoute: (CallAudioRoute) -> Unit,
    onDtmf: (Char) -> Unit,
    onToggleHold: () -> Unit,
    onSwap: () -> Unit,
    onMerge: () -> Unit,
    onAddCall: () -> Unit,
) {
    val snapshot = state.snapshot
    var showKeypad by remember { mutableStateOf(false) }
    var dtmfDigits by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header(
                snapshot = snapshot,
                bondedPerson = state.bondedPerson,
                callerName = state.callerName,
                callerPhotoUri = state.callerPhotoUri,
                callEnded = state.callEnded,
                dtmfDigits = dtmfDigits,
            )
            Spacer(Modifier.weight(1f))
            when {
                state.callEnded -> EndingFooter()
                snapshot == null -> { /* waiting for first snapshot */ }
                snapshot.state == CallSimpleState.RINGING &&
                    snapshot.direction == CallDirection.INCOMING ->
                    IncomingControls(
                        onAccept = onAccept,
                        onReject = onReject,
                        onRejectWith = onRejectWith,
                        canBlock = state.canBlockCaller,
                        onBlockReject = onBlockReject,
                    )
                showKeypad -> InCallDialpad(
                    onDigit = { c ->
                        dtmfDigits += c
                        onDtmf(c)
                    },
                    onHide = { showKeypad = false },
                )
                else -> OngoingControls(
                    isMuted = state.audio.isMuted,
                    route = state.audio.route,
                    availableRoutes = state.audio.availableRoutes,
                    canHold = snapshot.canHold,
                    isHeld = snapshot.state == CallSimpleState.HOLDING,
                    canMerge = snapshot.canMerge,
                    heldSnapshot = state.heldSnapshot,
                    heldName = state.heldName,
                    onEnd = onEnd,
                    onToggleMute = onToggleMute,
                    onShowKeypad = { showKeypad = true },
                    onSetAudioRoute = onSetAudioRoute,
                    onToggleHold = onToggleHold,
                    onSwap = onSwap,
                    onMerge = onMerge,
                    onAddCall = onAddCall,
                )
            }
        }
    }
}

@Composable
private fun Header(
    snapshot: CallSnapshot?,
    bondedPerson: MatchedBondedPerson?,
    callerName: String?,
    callerPhotoUri: String?,
    callEnded: Boolean,
    dtmfDigits: String,
) {
    val isConference = snapshot?.isConference == true
    // Bonded name wins, then the resolved address-book name, then whatever
    // Telecom gave us, then the raw number. A conference has no single
    // identity, so it overrides everything with a fixed label.
    val displayName = when {
        isConference -> "Conference call"
        else -> bondedPerson?.displayName
            ?: callerName
            ?: snapshot?.callerDisplayName
            ?: snapshot?.number
            ?: ""
    }
    val statusText = when {
        callEnded -> "Call ended"
        snapshot == null -> "Connecting…"
        else -> snapshot.state.label(snapshot.direction)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        // Photo priority: conference → group glyph; then bonded person's
        // local photo, then the system contact photo, else a tinted initial
        // bubble.
        val localPhoto = bondedPerson?.photoRelativePath
        when {
            isConference -> Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            localPhoto != null -> PersonAvatar(
                photoRelativePath = localPhoto,
                displayName = displayName,
                diameter = 120.dp,
            )
            callerPhotoUri != null -> AsyncImage(
                model = callerPhotoUri.toUri(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape),
            )
            else -> Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = displayName.trim().firstOrNull()?.uppercase() ?: "?",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = displayName.ifBlank { "Unknown" },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
        if (isConference && snapshot != null && snapshot.childCount > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${snapshot.childCount} people",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (bondedPerson != null && !isConference) {
            Spacer(Modifier.height(8.dp))
            BondedChip()
            bondedPerson.daysSinceLastContact?.let { days ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = lastSpokeLabel(days),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            cadenceSubtitle(bondedPerson.cadenceStatus)?.let { (text, tint) ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tint,
                )
            }
        }
        if (snapshot != null && snapshot.number.isNotBlank() && displayName != snapshot.number) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = snapshot.number,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // DTMF digits the user has tapped into the in-call keypad.
        if (dtmfDigits.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = dtmfDigits,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (snapshot?.state == CallSimpleState.ACTIVE && snapshot.connectTimeMillis != null) {
            Spacer(Modifier.height(8.dp))
            DurationTicker(connectTimeMillis = snapshot.connectTimeMillis)
        }
    }
}

@Composable
private fun BondedChip() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Bonded",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private fun lastSpokeLabel(daysAgo: Int): String = when (daysAgo) {
    0 -> "Spoke today"
    1 -> "Spoke yesterday"
    else -> "Last spoke $daysAgo days ago"
}

@Composable
private fun cadenceSubtitle(status: CadenceStatus): Pair<String, Color>? {
    val days = status.daysUntilDue ?: return null
    return when (status.state) {
        CadenceState.OVERDUE -> {
            val n = -days
            val text = if (n == 1L) "Overdue by 1 day" else "Overdue by $n days"
            text to MaterialTheme.colorScheme.error
        }
        CadenceState.DUE_SOON -> {
            val text = when (days) {
                0L -> "Due today"
                1L -> "Due tomorrow"
                else -> "Due in $days days"
            }
            text to MaterialTheme.colorScheme.tertiary
        }
        CadenceState.ON_TRACK -> {
            val text = if (days == 1L) "Next check-in in 1 day" else "Next check-in in $days days"
            text to MaterialTheme.colorScheme.onSurfaceVariant
        }
        CadenceState.NOT_TRACKED, CadenceState.NEVER_CONTACTED -> null
    }
}

@Composable
private fun DurationTicker(connectTimeMillis: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connectTimeMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsedSeconds = ((now - connectTimeMillis) / 1_000).coerceAtLeast(0)
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    Text(
        text = String.format(Locale.US, "%d:%02d", minutes, seconds),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncomingControls(
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onRejectWith: (String) -> Unit,
    canBlock: Boolean,
    onBlockReject: () -> Unit,
) {
    var showQuickReplies by remember { mutableStateOf(false) }

    if (showQuickReplies) {
        QuickReplySheet(
            onPick = { msg ->
                showQuickReplies = false
                onRejectWith(msg)
            },
            onDismiss = { showQuickReplies = false },
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Hold Reject to reply with a message",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionButton(
                icon = Icons.Filled.CallEnd,
                label = "Reject",
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                onClick = onReject,
                onLongClick = { showQuickReplies = true },
                haptic = ActionHaptic.Reject,
            )
            ActionButton(
                icon = Icons.Filled.Call,
                label = "Accept",
                containerColor = LocalCallColors.current.accept,
                contentColor = LocalCallColors.current.onAccept,
                onClick = onAccept,
                haptic = ActionHaptic.Confirm,
            )
        }
        // Tertiary action for nuisance callers: block the number and decline
        // in one tap. Hidden when we can't block (not default dialer, tablet,
        // or withheld number) so it never dead-ends.
        if (canBlock) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onBlockReject) {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Block & reject")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickReplySheet(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Reply and decline",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            QUICK_DECLINE_MESSAGES.forEach { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableRow { onPick(msg) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Message,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(text = msg, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)

@Composable
private fun OngoingControls(
    isMuted: Boolean,
    route: CallAudioRoute,
    availableRoutes: Set<CallAudioRoute>,
    canHold: Boolean,
    isHeld: Boolean,
    canMerge: Boolean,
    heldSnapshot: CallSnapshot?,
    heldName: String?,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onShowKeypad: () -> Unit,
    onSetAudioRoute: (CallAudioRoute) -> Unit,
    onToggleHold: () -> Unit,
    onSwap: () -> Unit,
    onMerge: () -> Unit,
    onAddCall: () -> Unit,
) {
    var showRoutePicker by remember { mutableStateOf(false) }
    // Only earpiece + speaker on this device → a simple toggle. Bluetooth or
    // wired headset present → open a picker so all routes are one tap away.
    val hasExtraRoutes = availableRoutes.any {
        it == CallAudioRoute.BLUETOOTH || it == CallAudioRoute.WIRED_HEADSET
    }

    if (showRoutePicker) {
        AudioRouteSheet(
            current = route,
            available = availableRoutes,
            onPick = {
                showRoutePicker = false
                onSetAudioRoute(it)
            },
            onDismiss = { showRoutePicker = false },
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (heldSnapshot != null) {
            HeldCallBanner(
                heldName = heldName ?: "On hold",
                canMerge = canMerge,
                onSwap = onSwap,
                onMerge = onMerge,
            )
            Spacer(Modifier.height(16.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ToggleButton(
                icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                label = if (isMuted) "Unmute" else "Mute",
                active = isMuted,
                onClick = onToggleMute,
            )
            if (canHold) {
                ToggleButton(
                    icon = if (isHeld) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    label = if (isHeld) "Resume" else "Hold",
                    active = isHeld,
                    onClick = onToggleHold,
                )
            }
            ToggleButton(
                icon = Icons.Filled.Dialpad,
                label = "Keypad",
                active = false,
                onClick = onShowKeypad,
            )
            ToggleButton(
                icon = route.icon(),
                label = route.label(),
                active = route != CallAudioRoute.EARPIECE,
                onClick = {
                    if (hasExtraRoutes) {
                        showRoutePicker = true
                    } else {
                        onSetAudioRoute(
                            if (route == CallAudioRoute.SPEAKER) {
                                CallAudioRoute.EARPIECE
                            } else {
                                CallAudioRoute.SPEAKER
                            },
                        )
                    }
                },
            )
        }
        // Only offer "Add call" while we're already 1-on-1; the platform
        // won't accept a third leg so hiding the entry point is honest.
        if (heldSnapshot == null) {
            TextButton(onClick = onAddCall, modifier = Modifier.padding(bottom = 16.dp)) {
                Icon(
                    imageVector = Icons.Filled.AddIcCall,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Add call")
            }
        } else {
            Spacer(Modifier.height(16.dp))
        }
        ActionButton(
            icon = Icons.Filled.CallEnd,
            label = "End call",
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            onClick = onEnd,
            haptic = ActionHaptic.Reject,
        )
    }
}

@Composable
private fun HeldCallBanner(
    heldName: String,
    canMerge: Boolean,
    onSwap: () -> Unit,
    onMerge: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "On hold",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = heldName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (canMerge) {
                TextButton(onClick = onMerge) {
                    Icon(
                        imageVector = Icons.Filled.MergeType,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Merge")
                }
            }
            TextButton(onClick = onSwap) {
                Icon(
                    imageVector = Icons.Filled.SwapCalls,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Swap")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioRouteSheet(
    current: CallAudioRoute,
    available: Set<CallAudioRoute>,
    onPick: (CallAudioRoute) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Audio output",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            // Stable display order; only show routes the platform reports.
            listOf(
                CallAudioRoute.EARPIECE,
                CallAudioRoute.SPEAKER,
                CallAudioRoute.BLUETOOTH,
                CallAudioRoute.WIRED_HEADSET,
            ).filter { it in available }.forEach { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableRow { onPick(r) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = r.icon(),
                        contentDescription = null,
                        tint = if (r == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = r.label(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (r == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun InCallDialpad(
    onDigit: (Char) -> Unit,
    onHide: () -> Unit,
) {
    val view = LocalView.current
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#'),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { digit ->
                    Surface(
                        modifier = Modifier.size(64.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onDigit(digit)
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = digit.toString(),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            onClick = onHide,
        ) {
            Text(
                text = "Hide keypad",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

/** Which tactile signal a call action should give. */
private enum class ActionHaptic { Confirm, Reject, Neutral }

/**
 * CONFIRM / REJECT (API 30+) make "accept" and "decline" feel distinct under
 * the thumb; older devices fall back to a plain key tap. Honours the system
 * haptic setting and needs no VIBRATE permission.
 */
private fun View.performActionHaptic(kind: ActionHaptic) {
    val constant = when (kind) {
        ActionHaptic.Confirm ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        ActionHaptic.Reject ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        ActionHaptic.Neutral -> HapticFeedbackConstants.VIRTUAL_KEY
    }
    performHapticFeedback(constant)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    haptic: ActionHaptic = ActionHaptic.Neutral,
) {
    val view = LocalView.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = {
                        view.performActionHaptic(haptic)
                        onClick()
                    },
                    onLongClick = onLongClick,
                ),
            color = containerColor,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ToggleButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            },
            modifier = Modifier.size(64.dp),
            colors = if (active) {
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EndingFooter() {
    Text(
        text = "This call has ended.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 24.dp),
    )
}

private fun CallAudioRoute.icon(): ImageVector = when (this) {
    CallAudioRoute.EARPIECE -> Icons.Filled.PhoneInTalk
    CallAudioRoute.SPEAKER -> Icons.Filled.VolumeUp
    CallAudioRoute.BLUETOOTH -> Icons.Filled.Bluetooth
    CallAudioRoute.WIRED_HEADSET -> Icons.Filled.Headset
}

private fun CallAudioRoute.label(): String = when (this) {
    CallAudioRoute.EARPIECE -> "Earpiece"
    CallAudioRoute.SPEAKER -> "Speaker"
    CallAudioRoute.BLUETOOTH -> "Bluetooth"
    CallAudioRoute.WIRED_HEADSET -> "Headset"
}

private fun CallSimpleState.label(direction: CallDirection): String = when (this) {
    CallSimpleState.NEW, CallSimpleState.CONNECTING -> "Connecting…"
    CallSimpleState.RINGING -> when (direction) {
        CallDirection.INCOMING -> "Incoming call"
        CallDirection.OUTGOING -> "Ringing…"
    }
    CallSimpleState.DIALING -> "Calling…"
    CallSimpleState.ACTIVE -> "In call"
    CallSimpleState.HOLDING -> "On hold"
    CallSimpleState.DISCONNECTED -> "Disconnected"
}
