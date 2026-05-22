package com.phonepvr.friends.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.phonepvr.friends.ui.calls.ConfirmationQueueScreen
import com.phonepvr.friends.ui.contacts.ImportContactsScreen
import com.phonepvr.friends.ui.people.AddEditPersonScreen
import com.phonepvr.friends.ui.people.PeopleListScreen
import com.phonepvr.friends.ui.person.PersonDetailScreen
import com.phonepvr.friends.ui.timeline.LogInteractionScreen
import com.phonepvr.friends.ui.timeline.TimelineScreen

object Routes {
    const val PEOPLE_LIST = "people"
    const val TIMELINE = "timeline"
    const val CALLS = "calls"
    const val ADD_PERSON = "person/add"
    const val EDIT_PERSON = "person/edit/{personId}"
    const val PERSON_DETAIL = "person/detail/{personId}"
    const val LOG_INTERACTION = "interaction/log/{personId}"
    const val IMPORT_CONTACTS = "contacts/import"
    const val PERSON_ID_ARG = "personId"

    fun editPerson(personId: Long): String = "person/edit/$personId"
    fun personDetail(personId: Long): String = "person/detail/$personId"
    fun logInteraction(personId: Long): String = "interaction/log/$personId"
}

@Composable
fun FriendsNavHost(navController: NavHostController = rememberNavController()) {
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
                bottomBar = { FriendsBottomBar(TopLevelTab.PEOPLE, onSelectTab) },
            )
        }
        composable(Routes.TIMELINE) {
            TimelineScreen(
                onOpenPerson = { personId ->
                    navController.navigate(Routes.personDetail(personId))
                },
                bottomBar = { FriendsBottomBar(TopLevelTab.TIMELINE, onSelectTab) },
            )
        }
        composable(Routes.CALLS) {
            ConfirmationQueueScreen(
                bottomBar = { FriendsBottomBar(TopLevelTab.CALLS, onSelectTab) },
            )
        }
        composable(Routes.ADD_PERSON) {
            AddEditPersonScreen(onDone = { navController.popBackStack() })
        }
        composable(
            route = Routes.EDIT_PERSON,
            arguments = listOf(navArgument(Routes.PERSON_ID_ARG) { type = NavType.LongType }),
        ) {
            AddEditPersonScreen(onDone = { navController.popBackStack() })
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
            )
        }
        composable(
            route = Routes.LOG_INTERACTION,
            arguments = listOf(navArgument(Routes.PERSON_ID_ARG) { type = NavType.LongType }),
        ) {
            LogInteractionScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.IMPORT_CONTACTS) {
            ImportContactsScreen(onDone = { navController.popBackStack() })
        }
    }
}
