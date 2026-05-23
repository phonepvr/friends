package com.phonepvr.friends.ui.tooltips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A one-shot inline tooltip. Renders nothing once [dismissed] contains
 * [tipId]; otherwise shows a soft tertiary-tinted banner with a close
 * button. The host VM owns the dismissed set so the same tip never shows
 * twice across re-launches.
 *
 * Deliberately inline-flow rather than an overlay Popup — the tips are
 * meant to be quiet hints, not attention-grabbers.
 */
@Composable
fun CoachMarkBanner(
    tipId: String,
    text: String,
    dismissed: Set<String>,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tipId in dismissed) return
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
            )
            IconButton(onClick = { onDismiss(tipId) }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss tip",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

/** Stable ids for the five initial coach marks. */
object Tooltips {
    const val WIDGET = "tip_widget"
    const val SCAN_CALLS = "tip_scan_calls"
    const val REACH_OUT = "tip_reach_out"
    const val CADENCE_TAP = "tip_cadence_tap"
    const val TIMELINE_LOG = "tip_timeline_log"
}
