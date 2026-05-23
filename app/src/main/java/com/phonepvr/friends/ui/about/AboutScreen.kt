package com.phonepvr.friends.ui.about

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val packageInfo = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()
    val versionName = packageInfo?.versionName.orEmpty()
    @Suppress("DEPRECATION")
    val versionCode = packageInfo?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else it.versionCode.toLong()
    } ?: 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Bondwidth") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { WhatsBondwidthSection() }
            item { HowItWorksSection() }
            item { PrivacySection() }
            item { AppInfoSection(versionName = versionName, versionCode = versionCode) }
        }
    }
}

// ─── Section 1 ──────────────────────────────────────────────────────────────

@Composable
private fun WhatsBondwidthSection() {
    SectionCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        SectionHeading("What's Bondwidth?")
        Paragraph(
            "We all have that friend we keep meaning to call. The cousin whose " +
                "birthday we remember three days too late. The one person who'd " +
                "genuinely light up if we just said hi.",
        )
        Paragraph("Bondwidth is a quiet little nudge against all of that.")
        Paragraph(
            "It keeps track of when you last spoke to the people who matter, " +
                "remembers their birthdays and anniversaries so you're never " +
                "caught out, and lets you jot down what you talked about — so the " +
                "next conversation picks up right where the last one left off.",
        )
        Paragraph(
            "Think of it as bandwidth for your bonds: a small, private space that " +
                "makes sure the friendships and family ties you care about " +
                "actually get the attention they deserve.",
        )
        Paragraph("No feeds. No likes. No noise. Just you and your people.")
    }
}

// ─── Section 2 ──────────────────────────────────────────────────────────────

private data class HowItem(
    val icon: ImageVector,
    val heading: String,
    val body: String,
)

private val HOW_ITEMS = listOf(
    HowItem(
        icon = Icons.Filled.Person,
        heading = "Add your people.",
        body = "Pull friends and family straight in from your contacts, or add " +
            "them by hand. Set how often you'd like to stay in touch, and " +
            "Bondwidth keeps an eye on the gaps.",
    ),
    HowItem(
        icon = Icons.Filled.Edit,
        heading = "Log your conversations.",
        body = "Open someone's profile to scan your call history with them, or " +
            "log a call, a coffee or a message yourself — with a note on what " +
            "you talked about. Future-you will be grateful.",
    ),
    HowItem(
        icon = Icons.Filled.DateRange,
        heading = "Never miss the big days.",
        body = "Add birthdays and anniversaries — Bondwidth gently flags anyone " +
            "who's missing one — and get a heads-up before the date so you're " +
            "never the friend who forgot.",
    ),
    HowItem(
        icon = Icons.AutoMirrored.Filled.List,
        heading = "Follow the timeline.",
        body = "Every interaction lands on a timeline you can scroll back " +
            "through — one friendship at a time, or everyone at once.",
    ),
    HowItem(
        icon = Icons.Filled.Home,
        heading = "Keep it on your home screen.",
        body = "The widget shows your quote of the day up top and everyone's " +
            "upcoming birthdays and anniversaries below, with a quiet colour " +
            "cue when you're overdue for a catch-up.",
    ),
    HowItem(
        icon = Icons.Filled.Call,
        heading = "Reach out in one tap.",
        body = "Call, text, WhatsApp or Signal anyone right from their profile. " +
            "The hardest part of staying in touch is starting — this makes " +
            "starting a single tap.",
    ),
    HowItem(
        icon = Icons.Filled.Star,
        heading = "Look back once a year.",
        body = "Year in Review sums up your year of connecting — who you saw " +
            "most, who slipped through the cracks, and a gentle nudge for the " +
            "year ahead.",
    ),
    HowItem(
        icon = Icons.Filled.Share,
        heading = "Carry it with you.",
        body = "Changing phones? Export everything to one file and restore it on " +
            "the new one. Your history travels with you.",
    ),
)

@Composable
private fun HowItWorksSection() {
    SectionCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        SectionHeading("How it works")
        HOW_ITEMS.forEach { item ->
            Spacer(Modifier.height(4.dp))
            HowTile(item)
        }
    }
}

@Composable
private fun HowTile(item: HowItem) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(28.dp)
                    .padding(top = 2.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = item.heading,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Section 3 ──────────────────────────────────────────────────────────────

private data class PrivacyPoint(val lead: String, val body: String)

private val PRIVACY_POINTS = listOf(
    PrivacyPoint(
        lead = "Zero internet.",
        body = "Bondwidth has no internet access at all. Not \"we promise not to\" " +
            "— it genuinely cannot go online, which means it cannot send your " +
            "data anywhere, to anyone, ever.",
    ),
    PrivacyPoint(
        lead = "No ads.",
        body = "Nothing to sell, and no one to sell it to.",
    ),
    PrivacyPoint(
        lead = "No account.",
        body = "No sign-up, no login, no email, no password. Open it and go.",
    ),
    PrivacyPoint(
        lead = "No data gathering.",
        body = "Bondwidth collects nothing about you or your contacts.",
    ),
    PrivacyPoint(
        lead = "No telemetry or tracking.",
        body = "No analytics, no \"usage insights,\" no invisible third parties " +
            "looking over your shoulder.",
    ),
)

@Composable
private fun PrivacySection() {
    // The visual centrepiece — primaryContainer tint pulls the eye, the check
    // icons read the message before any text is parsed.
    SectionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            text = "Your privacy, plainly",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Bondwidth is built on one simple idea: your relationships are " +
                "nobody's business but yours. So here's the deal — no legalese, " +
                "no fine print:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(12.dp))
        PRIVACY_POINTS.forEach { point ->
            PrivacyRow(point)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Everything you put into Bondwidth lives in exactly one place: " +
                "this device. The only way your data ever leaves your phone is " +
                "if you choose to export a backup yourself.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "A couple of permissions make life easier — but both are " +
                "entirely optional:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            icon = Icons.Filled.Person,
            label = "Contacts",
            body = "lets you import friends and family instead of typing everyone " +
                "in. Decline it, and you can add people manually. Nothing breaks.",
        )
        Spacer(Modifier.height(8.dp))
        PermissionRow(
            icon = Icons.Filled.Call,
            label = "Call log",
            body = "lets Bondwidth show your call history on a person's profile " +
                "so you can log calls in a tap. Decline it, and you can log " +
                "every conversation by hand instead.",
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Say no to either, or both, and Bondwidth still works " +
                "completely. Your call — always.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun PrivacyRow(point: PrivacyPoint) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(22.dp)
                .padding(top = 2.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = point.lead,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = point.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, label: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .size(22.dp)
                .padding(top = 2.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

// ─── Section 4 ──────────────────────────────────────────────────────────────

@Composable
private fun AppInfoSection(versionName: String, versionCode: Long) {
    SectionCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text = "Bondwidth",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        if (versionName.isNotBlank()) {
            Text(
                text = "Version $versionName (build $versionCode)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Made with love by #PankLak ♥",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "For the people worth keeping in touch with — which, let's be " +
                "honest, is all of them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Shared helpers ─────────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    containerColor: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit,
) {
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
}
