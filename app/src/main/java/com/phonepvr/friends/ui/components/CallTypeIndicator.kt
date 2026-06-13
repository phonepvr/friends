package com.phonepvr.friends.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.phonepvr.friends.domain.model.CallType
import com.phonepvr.friends.ui.theme.callColor

/**
 * Up-and-right for outgoing, down-and-left for incoming, a broken arrow for a
 * missed or rejected call. Shared so the dialer and call history can't drift
 * apart on which arrow means what.
 */
fun CallType.callIcon(): ImageVector = when (this) {
    CallType.INCOMING -> Icons.Filled.CallReceived
    CallType.OUTGOING -> Icons.Filled.CallMade
    CallType.MISSED, CallType.REJECTED -> Icons.Filled.CallMissed
}

/** Plain-language direction, used for the badge's screen-reader description. */
fun CallType.directionLabel(): String = when (this) {
    CallType.INCOMING -> "Incoming call"
    CallType.OUTGOING -> "Outgoing call"
    CallType.MISSED -> "Missed call"
    CallType.REJECTED -> "Rejected call"
}

/**
 * A call's direction as its arrow icon inside a soft tinted disc, in the
 * call-log colour: blue outgoing, green incoming, red missed/rejected. The disc
 * makes the direction scannable down a list at a glance; the distinct arrow
 * shapes keep it readable without relying on colour alone (colour-blind safe).
 *
 * Pass [contentDescription] = null where the row already shows the direction as
 * text (e.g. call history), so a screen reader doesn't announce it twice.
 */
@Composable
fun CallTypeBadge(
    type: CallType,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    contentDescription: String? = type.directionLabel(),
) {
    val color = callColor(type)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = type.callIcon(),
            contentDescription = contentDescription,
            tint = color,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}
