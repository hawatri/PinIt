package com.hawatri.pinit.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hawatri.pinit.data.NoteDatabase
import com.hawatri.pinit.viewmodel.PinItViewModel
import com.hawatri.pinit.viewmodel.PinItViewModelFactory

@Composable
fun PinItApp() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // 1. Initialize the Room Database and grab the Dao
    val database = NoteDatabase.getDatabase(context)
    val dao = database.noteDao()

    // 2. Create the shared ViewModel using our new Factory
    val sharedViewModel: PinItViewModel = viewModel(
        factory = PinItViewModelFactory(dao)
    )

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
                viewModel = sharedViewModel
            )
        }
        composable("new_note") {
            NewNoteScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = sharedViewModel
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