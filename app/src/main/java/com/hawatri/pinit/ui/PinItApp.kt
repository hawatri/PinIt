package com.hawatri.pinit.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hawatri.pinit.viewmodel.PinItViewModel

@Composable
fun PinItApp() {
    val navController = rememberNavController()

    // Create ONE shared ViewModel that lives as long as the app is running
    val sharedViewModel: PinItViewModel = viewModel()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToNewNote = { navController.navigate("new_note") },
                onNavigateToNewList = { navController.navigate("new_list") },
                onNavigateToNewLocation = { navController.navigate("new_location") },
                onNavigateToNewQR = { navController.navigate("new_qr") },
                onNavigateToNewAppList = { navController.navigate("new_app_list") },
                onNavigateToNewLink = { navController.navigate("new_link") },
                onNavigateToNewContact = { navController.navigate("new_contact") },
                onNavigateToNewImage = { navController.navigate("new_image") },
                viewModel = sharedViewModel // Pass the shared brain here
            )
        }
        composable("new_note") {
            NewNoteScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = sharedViewModel // Pass the exact same shared brain here
            )
        }
        composable("new_list") { NewListScreen(onNavigateBack = { navController.popBackStack() }) }
        composable("new_location") { NewLocationScreen(onNavigateBack = { navController.popBackStack() }) }
        composable("new_qr") { NewQRScreen(onNavigateBack = { navController.popBackStack() }) }
        composable("new_app_list") { NewAppListScreen(onNavigateBack = { navController.popBackStack() }) }
        composable("new_link") { NewLinkScreen(onNavigateBack = { navController.popBackStack() }) }
        composable("new_contact") { NewContactScreen(onNavigateBack = { navController.popBackStack() }) }
        composable("new_image") { NewImageScreen(onNavigateBack = { navController.popBackStack() }) }
    }
}