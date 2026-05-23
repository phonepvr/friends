package com.phonepvr.friends.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reusable explainer shown the first time the app would ask for a runtime
 * permission. The wording leads with what Friends would do with the
 * permission, then with the manual fallback if the user declines — both
 * paths are first-class, so the user never feels cornered.
 *
 * The sheet itself is dumb. The caller marks the rationale as shown after
 * dismissal (any of the three callbacks below), so a second tap on the
 * same action goes straight to the OS dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRationaleSheet(
    title: String,
    body: String,
    manualFallback: String,
    onGrant: () -> Unit,
    onManualFallback: () -> Unit,
    onDismiss: () -> Unit,
    grantLabel: String = "Continue",
    manualLabel: String = "I'll do it manually",
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = manualFallback,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onManualFallback,
                    modifier = Modifier.weight(1f),
                ) { Text(manualLabel) }
                Button(
                    onClick = onGrant,
                    modifier = Modifier.weight(1f),
                ) { Text(grantLabel) }
            }
        }
    }
}
