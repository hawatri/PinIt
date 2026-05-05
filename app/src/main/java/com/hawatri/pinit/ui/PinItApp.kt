package com.hawatri.pinit.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hawatri.pinit.data.NoteDatabase
import com.hawatri.pinit.viewmodel.PinItViewModel
import com.hawatri.pinit.viewmodel.PinItViewModelFactory

@Composable
fun PinItApp() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val database = NoteDatabase.getDatabase(context)
    val dao = database.noteDao()

    val sharedViewModel: PinItViewModel = viewModel(
        factory = PinItViewModelFactory(dao)
    )

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                // Pass the Note ID when clicking an existing note
                onNoteClick = { noteId -> navController.navigate("new_note?noteId=$noteId") },
                onNavigateToNewNote = { navController.navigate("new_note") },
                onNavigateToNewList = { navController.navigate("new_list") },
                onNavigateToNewLocation = { navController.navigate("new_location") },
                onNavigateToNewQR = { navController.navigate("new_qr") },
                onNavigateToNewAppList = { navController.navigate("new_app_list") },
                onNavigateToNewLink = { navController.navigate("new_link") },
                onNavigateToNewContact = { navController.navigate("new_contact") },
                onNavigateToNewImage = { navController.navigate("new_image") },
                onNavigateToArchive = { navController.navigate("archive") },
                viewModel = sharedViewModel
            )
        }
        // Updated Route: Accepts optional noteId
        composable(
            route = "new_note?noteId={noteId}",
            arguments = listOf(navArgument("noteId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId")
            NewNoteScreen(
                noteId = noteId,
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
        composable("archive") {
            ArchiveScreen(
                onNavigateBack = { navController.popBackStack() },
                onNoteClick = { noteId -> navController.navigate("new_note?noteId=$noteId") },
                viewModel = sharedViewModel
            )
        }
    }
}