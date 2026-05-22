package com.phonepvr.friends.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.phonepvr.friends.ui.contacts.ImportContactsScreen
import com.phonepvr.friends.ui.people.AddEditPersonScreen
import com.phonepvr.friends.ui.people.PeopleListScreen

object Routes {
    const val PEOPLE_LIST = "people"
    const val ADD_PERSON = "person/add"
    const val EDIT_PERSON = "person/edit/{personId}"
    const val IMPORT_CONTACTS = "contacts/import"
    const val PERSON_ID_ARG = "personId"

    fun editPerson(personId: Long): String = "person/edit/$personId"
}

@Composable
fun FriendsNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.PEOPLE_LIST) {
        composable(Routes.PEOPLE_LIST) {
            PeopleListScreen(
                onAddPerson = { navController.navigate(Routes.ADD_PERSON) },
                onEditPerson = { personId -> navController.navigate(Routes.editPerson(personId)) },
                onImportContacts = { navController.navigate(Routes.IMPORT_CONTACTS) },
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
        composable(Routes.IMPORT_CONTACTS) {
            ImportContactsScreen(onDone = { navController.popBackStack() })
        }
    }
}
