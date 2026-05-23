package com.phonepvr.friends

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.domain.model.ThemeMode
import com.phonepvr.friends.ui.lock.LockScreen
import com.phonepvr.friends.ui.navigation.FriendsNavHost
import com.phonepvr.friends.ui.onboarding.OnboardingScreen
import com.phonepvr.friends.ui.theme.FriendsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialDeepLink = if (intent.getBooleanExtra(EXTRA_OPEN_BACKUP, false)) {
            com.phonepvr.friends.ui.navigation.Routes.BACKUP
        } else {
            null
        }
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val authenticated by viewModel.authenticated.collectAsStateWithLifecycle()
            val current = settings
            FriendsTheme(
                themeMode = current?.themeMode ?: ThemeMode.SYSTEM,
                dynamicColor = current?.dynamicColorEnabled ?: false,
            ) {
                when {
                    // Wait for the first settings read so the lock and theme
                    // are correct before anything is drawn.
                    current == null -> Surface(modifier = Modifier.fillMaxSize()) {}

                    !current.hasSeenOnboarding ->
                        OnboardingScreen(onDone = viewModel::markOnboardingSeen)

                    current.appLockEnabled && !authenticated ->
                        LockScreen(onUnlocked = viewModel::onAuthenticated)

                    else -> {
                        NotificationPermissionEffect()
                        FriendsNavHost(initialDeepLink = initialDeepLink)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Re-lock when the app genuinely leaves the foreground, but not for a
        // configuration change such as rotation.
        if (!isChangingConfigurations) {
            viewModel.onMovedToBackground()
        }
    }

    companion object {
        /** True on the launch intent when the user tapped a backup-nudge notification. */
        const val EXTRA_OPEN_BACKUP = "com.phonepvr.friends.OPEN_BACKUP"
    }
}

/** Requests POST_NOTIFICATIONS once per launch on Android 13+. */
@Composable
private fun NotificationPermissionEffect() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* If denied, notifications simply stay off. */ }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
