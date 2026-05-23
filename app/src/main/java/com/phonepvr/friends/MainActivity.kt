package com.phonepvr.friends

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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
import com.phonepvr.friends.ui.navigation.Routes
import com.phonepvr.friends.ui.onboarding.OnboardingScreen
import com.phonepvr.friends.ui.theme.FriendsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.setDeepLink(deepLinkFor(intent))
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val authenticated by viewModel.authenticated.collectAsStateWithLifecycle()
            val deepLink by viewModel.deepLink.collectAsStateWithLifecycle()
            val current = settings
            // Toggle FLAG_SECURE in lockstep with the setting so screenshots,
            // screen recordings, casts and the recents preview are blocked
            // whenever the user wants them blocked.
            LaunchedEffect(current?.hideFromScreenshots) {
                if (current?.hideFromScreenshots == true) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
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
                        FriendsNavHost(
                            initialDeepLink = deepLink,
                            onDeepLinkConsumed = { viewModel.setDeepLink(null) },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTop launchMode reuses this activity instance for new
        // launches (e.g. tapping a widget row while the app is alive).
        // Update the stored intent and push the new deep link through so
        // the NavHost reacts.
        setIntent(intent)
        viewModel.setDeepLink(deepLinkFor(intent))
    }

    override fun onStop() {
        super.onStop()
        // Re-lock when the app genuinely leaves the foreground, but not for a
        // configuration change such as rotation.
        if (!isChangingConfigurations) {
            viewModel.onMovedToBackground()
        }
    }

    private fun deepLinkFor(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.getBooleanExtra(EXTRA_OPEN_BACKUP, false)) return Routes.BACKUP
        val personId = intent.getLongExtra(EXTRA_OPEN_PERSON_ID, -1L)
        if (personId > 0L) return Routes.personDetail(personId)
        return null
    }

    companion object {
        /** True on the launch intent when the user tapped a backup-nudge notification. */
        const val EXTRA_OPEN_BACKUP = "com.phonepvr.friends.OPEN_BACKUP"

        /** Person id (Long) carried by widget-row deep links. */
        const val EXTRA_OPEN_PERSON_ID = "com.phonepvr.friends.OPEN_PERSON_ID"
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
