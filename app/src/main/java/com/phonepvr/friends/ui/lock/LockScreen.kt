package com.phonepvr.friends.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private val APP_LOCK_AUTHENTICATORS =
    Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL

/** True when the device can satisfy a biometric or device-credential prompt. */
fun isAppLockAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(APP_LOCK_AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS

/**
 * Full-screen gate shown when app lock is on and the session is not yet
 * authenticated. Prompts for biometrics or the device credential on appear,
 * with a manual retry button.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val activity = LocalContext.current as? FragmentActivity

    LaunchedEffect(Unit) {
        if (activity != null) promptAppLock(activity, onUnlocked) else onUnlocked()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Friends is locked",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Confirm it's you to see your people and reminders.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { activity?.let { promptAppLock(it, onUnlocked) } }) {
                Text("Unlock")
            }
        }
    }
}

private fun promptAppLock(activity: FragmentActivity, onUnlocked: () -> Unit) {
    if (!isAppLockAvailable(activity)) {
        // The device cannot authenticate; never lock the user out of their data.
        onUnlocked()
        return
    }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult,
            ) {
                onUnlocked()
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Friends")
        .setSubtitle("Confirm it's you to open the app")
        .setAllowedAuthenticators(APP_LOCK_AUTHENTICATORS)
        .build()
    runCatching { prompt.authenticate(info) }
}
