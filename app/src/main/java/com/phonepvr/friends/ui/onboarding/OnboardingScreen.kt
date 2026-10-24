package com.phonepvr.friends.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.domain.quotes.Quote
import com.phonepvr.friends.ui.permissions.PermissionInfoList
import com.phonepvr.friends.ui.permissions.RestrictedDialerSettingsDialog
import com.phonepvr.friends.ui.permissions.missingRuntimePermissions
import com.phonepvr.friends.ui.permissions.rememberPhoneAppRoleRequest
import kotlinx.coroutines.launch

// Welcome, cadence, events, privacy+permissions intro, then the permissions
// action page. Kept tight so users reach the app fast.
private const val SLIDE_COUNT = 5

/** The last slide gathers permissions + the default-phone-app role. */
private const val PERMISSIONS_PAGE = SLIDE_COUNT - 1

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val quote by viewModel.quote.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { SLIDE_COUNT })
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                // Skip is always available — onboarding never blocks the app.
                TextButton(onClick = onDone) { Text("Skip") }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                if (page == PERMISSIONS_PAGE) {
                    PermissionsSlide(viewModel = viewModel)
                } else {
                    OnboardingSlide(page = page, quote = quote)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PageDots(
                    pageCount = SLIDE_COUNT,
                    currentPage = pagerState.currentPage,
                )
                val isLast = pagerState.currentPage == SLIDE_COUNT - 1
                Button(
                    onClick = {
                        if (isLast) {
                            onDone()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                ) {
                    Text(if (isLast) "Get started" else "Next")
                }
            }
        }
    }
}

@Composable
private fun PageDots(pageCount: Int, currentPage: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(pageCount) { i ->
            val color = if (i == currentPage) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun OnboardingSlide(page: Int, quote: Quote?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (page) {
            0 -> WelcomeSlide(quote)
            1 -> TitleBodySlide(
                title = "Friendship is small attentions, often.",
                body = "Set a check-in cadence — every 7, 14, 30 days, whatever fits. " +
                    "Bondwidth quietly flags who's getting too quiet, so you can reach out " +
                    "before silence becomes a habit.",
            )
            2 -> TitleBodySlide(
                title = "Birthdays. Anniversaries. The dates you don't want to miss.",
                body = "Add them once. We'll wave a friendly notification a few days " +
                    "ahead — and a \"Mark as wished\" button right on the notification " +
                    "for the day itself.",
            )
            3 -> TitleBodySlide(
                title = "Private by design — and better with a couple of permissions.",
                body = "Everything stays on this device: no accounts, no servers, not " +
                    "even an internet permission. As your phone and contacts app, " +
                    "Bondwidth reads your contacts (to add people you know) and your " +
                    "call log (so check-ins count automatically). Granting these on the " +
                    "next screen gives you the smoothest experience.",
            )
        }
    }
}

@Composable
private fun WelcomeSlide(quote: Quote?) {
    Text(
        text = "Hi. Glad you came.",
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = "Bondwidth is a tiny ledger of the people you care about.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    if (quote != null) {
        Spacer(Modifier.height(32.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = quote.text,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                quote.attribution?.let { attribution ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "— $attribution",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun TitleBodySlide(title: String, body: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * Final onboarding slide: explains every permission group up front and
 * requests them together, plus offers the default-phone-app role. Nothing
 * here blocks finishing — the bottom "Get started" button always works, and
 * anything declined falls back to the in-context prompts elsewhere.
 */
@Composable
private fun PermissionsSlide(viewModel: OnboardingViewModel) {
    val context = LocalContext.current
    val isDefaultDialer by viewModel.isDefaultDialer.collectAsStateWithLifecycle()

    // What's still not granted; re-read after every request and on resume (the
    // user may grant things in system Settings and come back).
    var missing by remember { mutableStateOf(missingRuntimePermissions(context)) }
    var declined by rememberSaveable { mutableStateOf(false) }
    var showRestrictedDialog by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshDefaultDialer()
        missing = missingRuntimePermissions(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        missing = missingRuntimePermissions(context)
        declined = missing.isNotEmpty()
    }
    // The one-step path, as in other phone apps: becoming the phone app makes
    // Android grant calls, call log, contacts and notifications itself.
    val requestPhoneApp = rememberPhoneAppRoleRequest(
        makeRequestIntent = viewModel::makeDialerRoleIntent,
        isPhoneApp = viewModel::refreshDefaultDialer,
        onRefused = { showRestrictedDialog = true },
        onResult = {
            missing = missingRuntimePermissions(context)
            declined = missing.isNotEmpty()
        },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Let's get you set up",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Make Bondwidth your phone app and Android sets up calls, " +
                "contacts and notifications in one step — no separate pop-ups. " +
                "Here's what that covers, and why. It all stays on this device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        PermissionInfoList()
        Spacer(Modifier.height(24.dp))
        if (isDefaultDialer) {
            Text(
                text = if (missing.isEmpty()) {
                    "Bondwidth is your phone app. You're all set."
                } else {
                    "Bondwidth is your phone app."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            if (missing.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { permissionLauncher.launch(missing.toTypedArray()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Allow the rest") }
            }
        } else {
            Button(
                onClick = requestPhoneApp,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Make Bondwidth your phone app") }
            Spacer(Modifier.height(4.dp))
            // Fallback for people who keep another phone app: the same permissions,
            // asked one by one by Android.
            if (missing.isNotEmpty()) {
                TextButton(
                    onClick = { permissionLauncher.launch(missing.toTypedArray()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Keep my phone app, just allow permissions") }
            }
            // Up-front explainer: the request above can fail on Android 13+ for
            // apps installed outside an app store because of "restricted
            // settings". Say what's coming so the user isn't stuck.
            Spacer(Modifier.height(12.dp))
            DefaultDialerHelp(
                onOpenDefaultApps = {
                    runCatching {
                        context.startActivity(viewModel.makeDefaultAppsSettingsIntent())
                    }
                },
                onLearnHow = { showRestrictedDialog = true },
            )
        }
        if (declined && missing.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Some permissions weren't granted. Bondwidth still works — " +
                    "you can enable them anytime.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = {
                runCatching { context.startActivity(viewModel.makeAppInfoIntent()) }
            }) {
                Text("Open app settings")
            }
        }
    }

    if (showRestrictedDialog) {
        RestrictedDialerSettingsDialog(
            onDismiss = { showRestrictedDialog = false },
            onOpenAppInfo = {
                runCatching { context.startActivity(viewModel.makeAppInfoIntent()) }
            },
            onOpenDefaultApps = {
                runCatching {
                    context.startActivity(viewModel.makeDefaultAppsSettingsIntent())
                }
            },
        )
    }
}

/**
 * Up-front explainer card shown beneath "Set as default phone app" during
 * onboarding. Tells the user WHY we want the role and HOW to recover if
 * Android blocks it — the question the user usually only asks after hitting
 * the "App was denied access" toast.
 */
@Composable
private fun DefaultDialerHelp(
    onOpenDefaultApps: () -> Unit,
    onLearnHow: () -> Unit,
) {
    androidx.compose.material3.ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Why and how",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Being the phone app lets Bondwidth show its in-call screen, " +
                    "log who called you so check-ins work, and block numbers in a " +
                    "tap. Calls still place without it — only the in-call UI falls " +
                    "back to your old dialer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Android 13+ may show “App was denied access to be the " +
                    "default Phone app” the first time you try, because Bondwidth " +
                    "isn't from the Play Store. One-time fix: allow restricted " +
                    "settings, then pick Bondwidth under Default apps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onLearnHow) {
                    Text("How to allow it")
                }
                TextButton(onClick = onOpenDefaultApps) {
                    Text("Open default apps")
                }
            }
        }
    }
}
