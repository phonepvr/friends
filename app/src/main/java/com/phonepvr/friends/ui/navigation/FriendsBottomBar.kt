package com.phonepvr.friends.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.phonepvr.friends.feature.Features

enum class TopLevelTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    /** False means the tab is hidden when [Features.ALL_IN_ONE] is off. */
    val gatedByAllInOne: Boolean = false,
) {
    PEOPLE(Routes.PEOPLE_LIST, "People", Icons.Filled.Person),
    PHONE(
        route = Routes.DIALER,
        label = "Phone",
        icon = Icons.Filled.Phone,
        gatedByAllInOne = true,
    ),
    CONTACTS(
        route = Routes.CONTACTS_BROWSER,
        label = "Contacts",
        icon = Icons.Filled.Contacts,
        gatedByAllInOne = true,
    ),
    YEAR_IN_REVIEW(Routes.YEAR_IN_REVIEW, "Year", Icons.Filled.DateRange),
}

@Composable
fun FriendsBottomBar(current: TopLevelTab, onSelect: (TopLevelTab) -> Unit) {
    NavigationBar {
        TopLevelTab.entries.forEach { tab ->
            if (tab.gatedByAllInOne && !Features.ALL_IN_ONE) return@forEach
            NavigationBarItem(
                selected = tab == current,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}
