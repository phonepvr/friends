package com.phonepvr.friends.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    PEOPLE(Routes.PEOPLE_LIST, "People", Icons.Filled.Person),
    YEAR_IN_REVIEW(Routes.YEAR_IN_REVIEW, "Year", Icons.Filled.BarChart),
}

@Composable
fun FriendsBottomBar(current: TopLevelTab, onSelect: (TopLevelTab) -> Unit) {
    NavigationBar {
        TopLevelTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}
