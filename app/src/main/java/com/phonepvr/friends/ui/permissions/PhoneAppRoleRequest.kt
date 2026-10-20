package com.phonepvr.friends.ui.permissions

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Remembers how to ask Android to make Bondwidth the phone app, the way Settings
 * and onboarding do it: the system request first. When that comes back without
 * the role on Android 13+ — which is how Android turns down an app installed
 * outside an app store until "Allow restricted settings" is on for it (a test
 * build is a separate app and needs it too) — [onRefused] runs, so the screen
 * can show [RestrictedDialerSettingsDialog] instead of leaving the user thinking
 * it worked.
 *
 * [isPhoneApp] re-reads the role once the request returns; [onResult] gets the
 * answer. Returns the function that starts the request.
 */
@Composable
fun rememberPhoneAppRoleRequest(
    makeRequestIntent: () -> Intent?,
    isPhoneApp: () -> Boolean,
    onRefused: () -> Unit,
    onResult: (isPhoneApp: Boolean) -> Unit = {},
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val granted = isPhoneApp()
        onResult(granted)
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) onRefused()
    }
    return {
        val intent = makeRequestIntent()
        if (intent == null || runCatching { launcher.launch(intent) }.isFailure) onRefused()
    }
}

/**
 * Shown when Block is tapped while Bondwidth isn't the phone app. Android lets
 * only the phone app (or the SMS app) add numbers to the system block list, so
 * the option is explained and offered rather than silently missing.
 */
@Composable
fun BlockNeedsPhoneAppDialog(
    onDismiss: () -> Unit,
    onMakePhoneApp: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Blocking needs Bondwidth as your phone app") },
        text = {
            Text(
                "Android only lets the phone app add numbers to the block list. " +
                    "Make Bondwidth your phone app, then block the number.",
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onMakePhoneApp()
            }) { Text("Make it the phone app") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}
