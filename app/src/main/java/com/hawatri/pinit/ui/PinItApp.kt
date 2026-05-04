package com.hawatri.pinit.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun PinItApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToNewNote = { navController.navigate("new_note") },
                onNavigateToNewList = { navController.navigate("new_list") },
                onNavigateToNewLocation = { navController.navigate("new_location") },
                onNavigateToNewQR = { navController.navigate("new_qr") },
                onNavigateToNewAppList = { navController.navigate("new_app_list") },
                onNavigateToNewLink = { navController.navigate("new_link") } // Added navigation action
            )
        }
        composable("new_note") { NewNoteScreen(onNavigateBack = { navController.popBackStack() }) }
        composable("new_list") { NewListScreen(onNavigateBack = { navController.popBackStack() }) }
        composable("new_location") { NewLocationScreen(onNavigateBack = { navController.popBackStack() }) }
        composable("new_qr") { NewQRScreen(onNavigateBack = { navController.popBackStack() }) }
        composable("new_app_list") { NewAppListScreen(onNavigateBack = { navController.popBackStack() }) }
        composable("new_link") { NewLinkScreen(onNavigateBack = { navController.popBackStack() }) } // Added route
    }
}