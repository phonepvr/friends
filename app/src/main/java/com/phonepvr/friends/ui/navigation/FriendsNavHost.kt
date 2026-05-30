package com.phonepvr.friends.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.phonepvr.friends.ui.about.AboutScreen
import com.phonepvr.friends.ui.backup.BackupScreen
import com.phonepvr.friends.ui.contacts.ContactDetailScreen
import com.phonepvr.friends.ui.contacts.ContactEditScreen
import com.phonepvr.friends.ui.contacts.ContactsBrowserScreen
import com.phonepvr.friends.ui.contacts.ImportContactsScreen
import com.phonepvr.friends.ui.dialer.CallHistoryScreen
import com.phonepvr.friends.ui.dialer.DialerScreen
import com.phonepvr.friends.ui.dialer.DialpadScreen
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
    const val CONTACTS_BROWSER = "contacts/browse"
    const val CONTACT_DETAIL = "contacts/detail/{contactId}"
    const val NEW_CONTACT = "contacts/new"
    const val CONTACT_EDIT = "contacts/edit/{contactId}"
    const val DIALER = "dialer"
    const val DIALPAD = "dialpad?number={number}"
    const val DIALPAD_PREFILL_ARG = "number"
    const val CALL_HISTORY = "dialer/history?number={number}"
    const val BACKUP = "backup"
    const val SETTINGS = "settings"
    const val YEAR_IN_REVIEW = "year-in-review"
    const val MY_QUOTES = "quotes/my"
    const val ONBOARDING = "onboarding"
    const val ABOUT = "about"
    const val PERSON_ID_ARG = "personId"
    const val ENTRY_ID_ARG = "entryId"
    const val CONTACT_ID_ARG = "contactId"

    fun editPerson(personId: Long): String = "person/edit/$personId"
    fun personDetail(personId: Long): String = "person/detail/$personId"
    fun logInteraction(personId: Long): String = "interaction/log/$personId"
    fun editInteraction(entryId: Long): String = "interaction/edit/$entryId"
    fun contactDetail(contactId: Long): String = "contacts/detail/$contactId"
    fun contactEdit(contactId: Long): String = "contacts/edit/$contactId"
    fun dialpad(prefill: String? = null): String =
        if (prefill.isNullOrBlank()) {
            "dialpad"
        } else {
            "dialpad?number=${android.net.Uri.encode(prefill)}"
        }

    fun callHistory(number: String): String =
        "dialer/history?number=${android.net.Uri.encode(number)}"
}

@Composable
fun FriendsNavHost(
    navController: NavHostController = rememberNavController(),
    initialDeepLink: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    LaunchedEffect(initialDeepLink) {
        if (initialDeepLink != null) {
            navController.navigate(initialDeepLink)
            // Clear the one-shot link so tapping the same widget row a
            // second time re-fires navigation (StateFlow would dedupe
            // equal values otherwise).
            onDeepLinkConsumed()
        }
    }
    val onSelectTab: (TopLevelTab) -> Unit = { tab ->
        navController.navigate(tab.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    // Smoother nav-graph transitions than the Compose Nav defaults: a small
    // horizontal slide for forward / pop pairs with a quick crossfade, so
    // screens feel connected rather than slammed in.
    val slidePushIn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition =
        {
            slideInHorizontally(animationSpec = tween(220)) { it / 10 } +
                fadeIn(animationSpec = tween(220))
        }
    val slidePushOut: AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition =
        {
            slideOutHorizontally(animationSpec = tween(180)) { -it / 24 } +
                fadeOut(animationSpec = tween(180))
        }
    val slidePopIn: AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition =
        {
            slideInHorizontally(animationSpec = tween(220)) { -it / 24 } +
                fadeIn(animationSpec = tween(220))
        }
    val slidePopOut: AnimatedContentTransitionScope<NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition =
        {
            slideOutHorizontally(animationSpec = tween(180)) { it / 10 } +
                fadeOut(animationSpec = tween(180))
        }
    NavHost(
        navController = navController,
        startDestination = Routes.PEOPLE_LIST,
        enterTransition = slidePushIn,
        exitTransition = slidePushOut,
        popEnterTransition = slidePopIn,
        popExitTransition = slidePopOut,
    ) {
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
        composable(Routes.CONTACTS_BROWSER) {
            ContactsBrowserScreen(
                onOpenContact = { contactId, _ ->
                    navController.navigate(Routes.contactDetail(contactId))
                },
                onCreateContact = { navController.navigate(Routes.NEW_CONTACT) },
                bottomBar = { FriendsBottomBar(TopLevelTab.CONTACTS, onSelectTab) },
            )
        }
        composable(
            route = Routes.CONTACT_DETAIL,
            arguments = listOf(navArgument(Routes.CONTACT_ID_ARG) { type = NavType.LongType }),
        ) {
            ContactDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenPerson = { personId ->
                    navController.navigate(Routes.personDetail(personId))
                },
                onEdit = { contactId ->
                    navController.navigate(Routes.contactEdit(contactId))
                },
            )
        }
        composable(Routes.NEW_CONTACT) {
            ContactEditScreen(onDone = { navController.popBackStack() })
        }
        composable(
            route = Routes.CONTACT_EDIT,
            arguments = listOf(navArgument(Routes.CONTACT_ID_ARG) { type = NavType.LongType }),
        ) {
            ContactEditScreen(onDone = { navController.popBackStack() })
        }
        composable(
            route = Routes.DIALER,
            arguments = emptyList(),
        ) {
            DialerScreen(
                onOpenContact = { contactId ->
                    navController.navigate(Routes.contactDetail(contactId))
                },
                onOpenDialpad = { navController.navigate(Routes.dialpad()) },
                onOpenHistory = { number ->
                    navController.navigate(Routes.callHistory(number))
                },
                bottomBar = { FriendsBottomBar(TopLevelTab.PHONE, onSelectTab) },
            )
        }
        composable(
            route = Routes.DIALPAD,
            arguments = listOf(
                navArgument(Routes.DIALPAD_PREFILL_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            DialpadScreen(
                onClose = { navController.popBackStack() },
                onOpenContact = { contactId ->
                    navController.navigate(Routes.contactDetail(contactId))
                },
            )
        }
        composable(
            route = Routes.CALL_HISTORY,
            arguments = listOf(
                navArgument(Routes.DIALPAD_PREFILL_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            CallHistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenContact = { contactId ->
                    navController.navigate(Routes.contactDetail(contactId))
                },
                onOpenPerson = { personId ->
                    navController.navigate(Routes.personDetail(personId))
                },
            )
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
