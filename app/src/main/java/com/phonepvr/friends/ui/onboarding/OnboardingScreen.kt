package com.phonepvr.friends.ui.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phonepvr.friends.domain.quotes.Quote
import kotlinx.coroutines.launch

private const val SLIDE_COUNT = 6

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
                OnboardingSlide(page = page, quote = quote)
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
                title = "Two doors you can leave shut.",
                body = "Bondwidth can ask for contacts (to import the people you already " +
                    "know) and the call log (to count calls automatically). Both are " +
                    "optional. Say no and the app still works — add anyone by hand, " +
                    "log every conversation yourself.",
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
