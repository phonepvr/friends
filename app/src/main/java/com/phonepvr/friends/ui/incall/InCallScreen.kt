package com.phonepvr.friends.ui.incall

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phonepvr.friends.data.incall.CallAudioRoute
import com.phonepvr.friends.data.incall.CallDirection
import com.phonepvr.friends.data.incall.CallSimpleState
import com.phonepvr.friends.data.incall.CallSnapshot
import com.phonepvr.friends.ui.components.PersonAvatar
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun InCallScreen(
    state: InCallUiState,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
) {
    val snapshot = state.snapshot
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
                callEnded = state.callEnded,
            )
            Spacer(Modifier.weight(1f))
            when {
                state.callEnded -> EndingFooter()
                snapshot == null -> { /* waiting for first snapshot */ }
                snapshot.state == CallSimpleState.RINGING &&
                    snapshot.direction == CallDirection.INCOMING ->
                    IncomingControls(onAccept = onAccept, onReject = onReject)
                else -> OngoingControls(
                    isMuted = state.audio.isMuted,
                    isSpeaker = state.audio.route == CallAudioRoute.SPEAKER,
                    onEnd = onEnd,
                    onToggleMute = onToggleMute,
                    onToggleSpeaker = onToggleSpeaker,
                )
            }
        }
    }
}

@Composable
private fun Header(
    snapshot: CallSnapshot?,
    bondedPerson: MatchedBondedPerson?,
    callEnded: Boolean,
) {
    // Bonded match takes priority — that's how the user knows this contact.
    val displayName = bondedPerson?.displayName
        ?: snapshot?.callerDisplayName
        ?: snapshot?.number
        ?: ""
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
        // Use the bonded person's saved photo when we have one; otherwise
        // fall back to a tinted initial bubble.
        if (bondedPerson?.photoRelativePath != null) {
            PersonAvatar(
                photoRelativePath = bondedPerson.photoRelativePath,
                displayName = displayName,
                diameter = 120.dp,
            )
        } else {
            Box(
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
        if (bondedPerson != null) {
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
        }
        if (snapshot != null && snapshot.number.isNotBlank() && displayName != snapshot.number) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = snapshot.number,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun IncomingControls(onAccept: () -> Unit, onReject: () -> Unit) {
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
        )
        ActionButton(
            icon = Icons.Filled.Call,
            label = "Accept",
            containerColor = AcceptGreen,
            contentColor = Color.White,
            onClick = onAccept,
        )
    }
}

@Composable
private fun OngoingControls(
    isMuted: Boolean,
    isSpeaker: Boolean,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ToggleButton(
                icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                label = if (isMuted) "Unmute" else "Mute",
                active = isMuted,
                onClick = onToggleMute,
            )
            ToggleButton(
                icon = Icons.Filled.VolumeUp,
                label = if (isSpeaker) "Speaker on" else "Speaker",
                active = isSpeaker,
                onClick = onToggleSpeaker,
            )
        }
        ActionButton(
            icon = Icons.Filled.CallEnd,
            label = "End call",
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            onClick = onEnd,
        )
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape),
            color = containerColor,
            onClick = onClick,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
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

private val AcceptGreen = Color(0xFF1B873A)
