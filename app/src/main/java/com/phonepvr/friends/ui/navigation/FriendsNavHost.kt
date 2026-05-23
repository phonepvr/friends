package com.phonepvr.friends.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.phonepvr.friends.ui.about.AboutScreen
import com.phonepvr.friends.ui.backup.BackupScreen
import com.phonepvr.friends.ui.contacts.ImportContactsScreen
import com.phonepvr.friends.ui.onboarding.OnboardingScreen
import com.phonepvr.friends.ui.people.AddEditPersonScreen
import com.phonepvr.friends.ui.people.PeopleListScreen
import com.phonepvr.friends.ui.person.PersonDetailScreen
import com.phonepvr.friends.ui.quotes.MyQuotesScreen
import com.phonepvr.friends.ui.review.YearInReviewScreen
import com.phonepvr.friends.ui.settings.SettingsScreen
import com.phonepvr.friends.ui.timeline.LogInteractionScreen

object Routes {
    const val PEOPLE_LIST = "people"
    const val ADD_PERSON = "person/add"
    const val EDIT_PERSON = "person/edit/{personId}"
    const val PERSON_DETAIL = "person/detail/{personId}"
    const val LOG_INTERACTION = "interaction/log/{personId}"
    const val EDIT_INTERACTION = "interaction/edit/{entryId}"
    const val IMPORT_CONTACTS = "contacts/import"
    const val BACKUP = "backup"
    const val SETTINGS = "settings"
    const val YEAR_IN_REVIEW = "year-in-review"
    const val MY_QUOTES = "quotes/my"
    const val ONBOARDING = "onboarding"
    const val ABOUT = "about"
    const val PERSON_ID_ARG = "personId"
    const val ENTRY_ID_ARG = "entryId"

    fun editPerson(personId: Long): String = "person/edit/$personId"
    fun personDetail(personId: Long): String = "person/detail/$personId"
    fun logInteraction(personId: Long): String = "interaction/log/$personId"
    fun editInteraction(entryId: Long): String = "interaction/edit/$entryId"
}

@Composable
fun FriendsNavHost(
    navController: NavHostController = rememberNavController(),
    initialDeepLink: String? = null,
) {
    LaunchedEffect(initialDeepLink) {
        if (initialDeepLink != null) {
            navController.navigate(initialDeepLink)
        }
    }
    val onSelectTab: (TopLevelTab) -> Unit = { tab ->
        navController.navigate(tab.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    NavHost(navController = navController, startDestination = Routes.PEOPLE_LIST) {
        composable(Routes.PEOPLE_LIST) {
            PeopleListScreen(
                onAddPerson = { navController.navigate(Routes.ADD_PERSON) },
                onOpenPerson = { personId ->
                    navController.navigate(Routes.personDetail(personId))
                },
                onImportContacts = { navController.navigate(Routes.IMPORT_CONTACTS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenBackup = { navController.navigate(Routes.BACKUP) },
                bottomBar = { FriendsBottomBar(TopLevelTab.PEOPLE, onSelectTab) },
            )
        }
        composable(Routes.ADD_PERSON) {
            AddEditPersonScreen(onDone = { navController.popBackStack() })
        }
        composable(
            route = Routes.EDIT_PERSON,
            arguments = listOf(navArgument(Routes.PERSON_ID_ARG) { type = NavType.LongType }),
        ) {
            AddEditPersonScreen(
                onDone = { navController.popBackStack() },
                onDeleted = {
                    // Skip past the now-stale PersonDetail screen so its
                    // observePersonWithDetails flow doesn't surface "Loading…"
                    // for a row that just got deleted.
                    navController.popBackStack(Routes.PEOPLE_LIST, inclusive = false)
                },
            )
        }
        composable(
            route = Routes.PERSON_DETAIL,
            arguments = listOf(navArgument(Routes.PERSON_ID_ARG) { type = NavType.LongType }),
        ) {
            PersonDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { personId -> navController.navigate(Routes.editPerson(personId)) },
                onLogInteraction = { personId ->
                    navController.navigate(Routes.logInteraction(personId))
                },
                onEditInteraction = { entryId ->
                    navController.navigate(Routes.editInteraction(entryId))
                },
            )
        }
        composable(
            route = Routes.LOG_INTERACTION,
            arguments = listOf(navArgument(Routes.PERSON_ID_ARG) { type = NavType.LongType }),
        ) {
            LogInteractionScreen(onDone = { navController.popBackStack() })
        }
        composable(
            route = Routes.EDIT_INTERACTION,
            arguments = listOf(navArgument(Routes.ENTRY_ID_ARG) { type = NavType.LongType }),
        ) {
            LogInteractionScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.IMPORT_CONTACTS) {
            ImportContactsScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenBackup = { navController.navigate(Routes.BACKUP) },
                onOpenMyQuotes = { navController.navigate(Routes.MY_QUOTES) },
                onReplayOnboarding = { navController.navigate(Routes.ONBOARDING) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.BACKUP) {
            BackupScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.YEAR_IN_REVIEW) {
            YearInReviewScreen(
                onBack = { navController.popBackStack() },
                bottomBar = { FriendsBottomBar(TopLevelTab.YEAR_IN_REVIEW, onSelectTab) },
            )
        }
        composable(Routes.MY_QUOTES) {
            MyQuotesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ONBOARDING) {
            // Replay path — just pop back when the user finishes / skips. The
            // hasSeenOnboarding flag stays as-is.
            OnboardingScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
