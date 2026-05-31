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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.domain.quotes.Quote
import com.phonepvr.friends.ui.permissions.PermissionInfoList
import com.phonepvr.friends.ui.permissions.onboardingRuntimePermissions
import kotlinx.coroutines.launch

private const val SLIDE_COUNT = 7

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
                title = "Your timeline. Just yours.",
                body = "Every call, message and meet-up gets logged on this device. " +
                    "No accounts, no analytics, no servers. Friends doesn't even ask " +
                    "for an internet permission — we couldn't phone home if we wanted to.",
            )
            4 -> TitleBodySlide(
                title = "A couple of permissions make this sing.",
                body = "Bondwidth works best as your phone and contacts app: it reads " +
                    "your contacts so you can add the people you already know, and your " +
                    "call log so check-ins are counted automatically — no manual logging. " +
                    "Granting these on the next screen gives you the smoothest experience. " +
                    "It all stays on this device.",
            )
            5 -> TitleBodySlide(
                title = "There's a widget, too.",
                body = "Long-press your home screen and drop a Bondwidth widget there. " +
                    "Upcoming birthdays, anniversaries, who's overdue for a check-in " +
                    "— at a glance.",
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

    var requested by rememberSaveable { mutableStateOf(false) }
    var allGranted by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        requested = true
        allGranted = result.values.all { it }
    }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refreshDefaultDialer() }

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
            text = "Friends is your phone and contacts app, so granting these " +
                "gives you the full, smooth experience — calls, contacts and " +
                "automatic check-ins all working from the start. Here's what, " +
                "and why. It all stays on this device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        PermissionInfoList()
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                permissionLauncher.launch(onboardingRuntimePermissions().toTypedArray())
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (allGranted) "Permissions granted" else "Grant permissions")
        }
        Spacer(Modifier.height(8.dp))
        if (isDefaultDialer) {
            Text(
                text = "Friends is your default phone app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        } else {
            OutlinedButton(
                onClick = { viewModel.makeDialerRoleIntent()?.let(roleLauncher::launch) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Set as default phone app")
            }
        }
        if (requested && !allGranted) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Some permissions weren't granted. Friends still works — " +
                    "you can enable them anytime.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = { openAppInfo(context) }) {
                Text("Open app settings")
            }
        }
    }
}

private fun openAppInfo(context: android.content.Context) {
    val intent = android.content.Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    )
        .setData(android.net.Uri.fromParts("package", context.packageName, null))
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
