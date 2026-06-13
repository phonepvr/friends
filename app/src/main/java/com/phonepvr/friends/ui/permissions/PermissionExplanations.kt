package com.phonepvr.friends.ui.permissions

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One user-facing permission group shown during onboarding. Grouped by purpose
 * (not by raw Android permission name) so the explanation reads in plain terms.
 *
 * NOTE: [PermissionRationaleSheet] still carries its own copy of this content
 * for the in-context prompts; consolidate the two when convenient.
 */
data class PermissionInfo(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

/** What Friends asks for, and why — in the order shown during onboarding. */
val permissionInfos: List<PermissionInfo> = listOf(
    PermissionInfo(
        icon = Icons.Filled.Contacts,
        title = "Contacts",
        description = "Read and update your contacts so Bondwidth can show names and " +
            "photos and keep your circle in sync.",
    ),
    PermissionInfo(
        icon = Icons.Filled.Call,
        title = "Call history",
        description = "Read your recent calls to show who you've spoken to and keep " +
            "each person's timeline up to date.",
    ),
    PermissionInfo(
        icon = Icons.Filled.Phone,
        title = "Phone",
        description = "Place and answer calls inside Bondwidth and show you who's " +
            "calling.",
    ),
    PermissionInfo(
        icon = Icons.Filled.Notifications,
        title = "Notifications",
        description = "Alert you to incoming calls and stay-in-touch reminders.",
    ),
)

/**
 * Renders [permissionInfos] as an explanation list. Used inline on the
 * onboarding permissions step so every request is explained up front.
 */
@Composable
fun PermissionInfoList(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        permissionInfos.forEach { info ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    // Read as one node: "Contacts. Read and update your contacts…"
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = info.icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = info.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The dangerous runtime permissions Friends asks for up front during
 * onboarding. POST_NOTIFICATIONS is only a runtime permission on API 33+, so
 * it's added conditionally. (CALL_PHONE / READ_CALL_LOG etc. are also granted
 * implicitly once Friends is the default phone app — requesting them here is
 * harmless and covers the "not default" case.)
 */
fun onboardingRuntimePermissions(): List<String> = buildList {
    add(Manifest.permission.READ_CONTACTS)
    add(Manifest.permission.WRITE_CONTACTS)
    add(Manifest.permission.READ_CALL_LOG)
    add(Manifest.permission.CALL_PHONE)
    add(Manifest.permission.READ_PHONE_STATE)
    add(Manifest.permission.READ_PHONE_NUMBERS)
    add(Manifest.permission.ANSWER_PHONE_CALLS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}
