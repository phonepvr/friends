package com.phonepvr.friends.ui.permissions

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Explainer + action buttons for the "Android blocked setting Bondwidth as
 * the default phone app" wall users hit on Android 13+ when they sideload.
 * Used from both the Settings screen and the onboarding permissions slide so
 * the wording stays in sync; reaching this page from the intro is the whole
 * point of explaining it up front.
 *
 * - [onOpenAppInfo]   — fires Settings.ACTION_APPLICATION_DETAILS_SETTINGS
 *                       so the user can flip "Allow restricted settings"
 *                       behind the ⋮ menu.
 * - [onOpenDefaultApps] — direct link to Settings → Apps → Default apps,
 *                       where Phone app can be reassigned. Mostly useful
 *                       on the second visit, after restricted settings is
 *                       already allowed.
 */
@Composable
fun RestrictedDialerSettingsDialog(
    onDismiss: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenDefaultApps: () -> Unit,
) {
    // The restricted-settings gate only exists on Android 13+; lower versions
    // hit a different (older) wall, so we adapt the body slightly. The same
    // dialog still helps either way because the "Open default apps" link
    // works everywhere.
    val tiramisuPlus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allow Bondwidth to be your phone app") },
        text = {
            Column {
                if (tiramisuPlus) {
                    Text(
                        "Android 13 and newer block apps installed outside " +
                            "the Play Store from becoming the default phone " +
                            "app. The dialog you see — “App was denied " +
                            "access to be the default Phone app” — is " +
                            "Android's standard refusal, not a Bondwidth " +
                            "limit. Same wording on Pixel, Samsung, Xiaomi.",
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Why we ask for it:")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Being the phone app is what lets Bondwidth show its " +
                            "in-call screen, log who called you so cadence works, " +
                            "and let you block numbers in one tap. Without it, " +
                            "calls still place — they just open in your old dialer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("To allow it, just once:")
                    Spacer(Modifier.height(6.dp))
                    Text("1.  Tap “Open app info” below.")
                    Text("2.  Tap the ⋮ menu in the top-right corner of that screen.")
                    Text("3.  Tap “Allow restricted settings” and confirm.")
                    Text(
                        "4.  Come back, then tap “Open default apps” here and " +
                            "pick Bondwidth under “Phone app”.",
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "If you don't see the ⋮ menu, the manufacturer has " +
                            "hidden it. On some Xiaomi / HyperOS builds you " +
                            "also have to turn off Developer options → MIUI " +
                            "optimization, then reboot. Calls still place " +
                            "fine without the role — only the in-call screen " +
                            "falls back to your existing one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Android's “Default apps” screen is where the phone " +
                            "app is chosen. Tap “Open default apps” below and " +
                            "pick Bondwidth under “Phone app”. If you don't " +
                            "see it listed, open Bondwidth's app info, then " +
                            "come back here.",
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onOpenDefaultApps()
                },
            ) { Text("Open default apps") }
        },
        dismissButton = {
            Column {
                TextButton(
                    onClick = {
                        onDismiss()
                        onOpenAppInfo()
                    },
                ) { Text("Open app info") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}
