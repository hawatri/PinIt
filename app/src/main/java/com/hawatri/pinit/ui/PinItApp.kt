package com.hawatri.pinit.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hawatri.pinit.data.NoteDatabase
import com.hawatri.pinit.data.NoteType
import com.hawatri.pinit.data.ThemeMode
import com.hawatri.pinit.viewmodel.PinItViewModel
import com.hawatri.pinit.viewmodel.PinItViewModelFactory

@Composable
fun PinItApp(
    sharedText: String? = null,
    sharedImageUri: String? = null,
    sharedIcsUri: String? = null,
    widgetAction: String? = null,
    widgetOpenNoteId: String? = null,
    currentTheme: ThemeMode = ThemeMode.SYSTEM,
    onThemeChange: (ThemeMode) -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val database = NoteDatabase.getDatabase(context)
    val dao = database.noteDao()

    val sharedViewModel: PinItViewModel = viewModel(factory = PinItViewModelFactory(dao))

    // Refresh widget whenever notes change (pin state may have changed)
    val notes by sharedViewModel.notes.collectAsState()
    LaunchedEffect(notes) {
        com.hawatri.pinit.widget.PinItWidget.requestUpdate(context)
        com.hawatri.pinit.widget.NoteWidget.requestUpdateAll(context)
    }

    // Handle widget quick-action intents
    LaunchedEffect(widgetAction) {
        when (widgetAction) {
            "new_note" -> navController.navigate("new_note")
            "new_list" -> navController.navigate("new_list")
        }
    }

    // Handle "open this note" intents fired by tapping a NoteWidget header
    LaunchedEffect(widgetOpenNoteId, notes) {
        if (widgetOpenNoteId != null) {
            val note = notes.find { it.id == widgetOpenNoteId } ?: return@LaunchedEffect
            val route = when {
                note.noteType == NoteType.LIST || note.isList -> "new_list?noteId=${note.id}"
                note.noteType == NoteType.QR -> "new_qr?noteId=${note.id}"
                note.noteType == NoteType.LINK -> "new_link?noteId=${note.id}"
                note.noteType == NoteType.CONTACT -> "new_contact?noteId=${note.id}"
                note.noteType == NoteType.LOCATION -> "new_location?noteId=${note.id}"
                note.noteType == NoteType.APPLIST -> "new_app_list?noteId=${note.id}"
                note.noteType == NoteType.IMAGE -> "new_image?noteId=${note.id}"
                note.noteType == NoteType.PDF -> "new_pdf?noteId=${note.id}"
                note.noteType == NoteType.AUDIO -> "new_audio?noteId=${note.id}"
                else -> "new_note?noteId=${note.id}"
            }
            navController.navigate(route)
        }
    }

    // Handle incoming share intents — navigate after the nav graph is ready
    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            if (sharedText.startsWith("http://") || sharedText.startsWith("https://")) {
                val encoded = Uri.encode(sharedText)
                navController.navigate("new_link?url=$encoded")
            } else {
                val encoded = Uri.encode(sharedText)
                navController.navigate("new_note?sharedText=$encoded")
            }
        }
    }

    LaunchedEffect(sharedImageUri) {
        if (sharedImageUri != null) {
            val encoded = Uri.encode(sharedImageUri)
            navController.navigate("new_image?imageUri=$encoded")
        }
    }

    // ICS share — navigate to home (the HomeScreen's import launcher handles it)
    val icsShareUri = remember(sharedIcsUri) { sharedIcsUri?.let { Uri.parse(it) } }

    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth / 4 },
                animationSpec = tween(280)
            ) + fadeIn(animationSpec = tween(220))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 6 },
                animationSpec = tween(220)
            ) + fadeOut(animationSpec = tween(180))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 6 },
                animationSpec = tween(260)
            ) + fadeIn(animationSpec = tween(220))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth / 4 },
                animationSpec = tween(240)
            ) + fadeOut(animationSpec = tween(180))
        }
    ) {
        composable("home") {
            HomeScreen(
                onNoteClick = { note ->
                    when {
                        note.noteType == NoteType.LIST || note.isList ->
                            navController.navigate("new_list?noteId=${note.id}")
                        note.noteType == NoteType.QR ->
                            navController.navigate("new_qr?noteId=${note.id}")
                        note.noteType == NoteType.LINK ->
                            navController.navigate("new_link?noteId=${note.id}")
                        note.noteType == NoteType.CONTACT ->
                            navController.navigate("new_contact?noteId=${note.id}")
                        note.noteType == NoteType.LOCATION ->
                            navController.navigate("new_location?noteId=${note.id}")
                        note.noteType == NoteType.APPLIST ->
                            navController.navigate("new_app_list?noteId=${note.id}")
                        note.noteType == NoteType.IMAGE ->
                            navController.navigate("new_image?noteId=${note.id}")
                        note.noteType == NoteType.PDF ->
                            navController.navigate("new_pdf?noteId=${note.id}")
                        note.noteType == NoteType.AUDIO ->
                            navController.navigate("new_audio?noteId=${note.id}")
                        else -> navController.navigate("new_note?noteId=${note.id}")
                    }
                },
                onNavigateToNewNote = { navController.navigate("new_note") },
                onNavigateToNewList = { navController.navigate("new_list") },
                onNavigateToNewLocation = { navController.navigate("new_location") },
                onNavigateToNewQR = { navController.navigate("new_qr") },
                onNavigateToNewAppList = { navController.navigate("new_app_list") },
                onNavigateToNewLink = { navController.navigate("new_link") },
                onNavigateToNewContact = { navController.navigate("new_contact") },
                onNavigateToNewImage = { navController.navigate("new_image") },
                onNavigateToNewPDF = { navController.navigate("new_pdf") },
                onNavigateToNewAudio = { navController.navigate("new_audio") },
                icsShareUri = icsShareUri,
                onNavigateToArchive = { navController.navigate("archive") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToSignIn = { navController.navigate("sign_in") },
                viewModel = sharedViewModel
            )
        }

        composable(
            route = "new_note?noteId={noteId}&sharedText={sharedText}",
            arguments = listOf(
                navArgument("noteId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("sharedText") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            NewNoteScreen(
                noteId = backStackEntry.arguments?.getString("noteId"),
                sharedText = backStackEntry.arguments?.getString("sharedText"),
                onNavigateBack = { navController.popBackStack() },
                viewModel = sharedViewModel
            )
        }

        composable(
            route = "new_list?noteId={noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            NewListScreen(
                noteId = backStackEntry.arguments?.getString("noteId"),
                onNavigateBack = { navController.popBackStack() },
                viewModel = sharedViewModel
            )
        }

        composable(
            route = "new_qr?noteId={noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            NewQRScreen(
                noteId = backStackEntry.arguments?.getString("noteId"),
                onNavigateBack = { navController.popBackStack() },
                viewModel = sharedViewModel
            )
        }

        composable(
            route = "new_link?noteId={noteId}&url={url}",
            arguments = listOf(
                navArgument("noteId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            NewLinkScreen(
                noteId = backStackEntry.arguments?.getString("noteId"),
                prefillUrl = backStackEntry.arguments?.getString("url"),
                onNavigateBack = { navController.popBackStack() },
                viewModel = sharedViewModel
            )
        }

        composable(
            route = "new_contact?noteId={noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            NewContactScreen(
                noteId = backStackEntry.arguments?.getString("noteId"),
                onNavigateBack = { navController.popBackStack() },
                viewModel = sharedViewModel
            )
        }

        composable(
            route = "new_location?noteId={noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            NewLocationScreen(
                noteId = backStackEntry.arguments?.getString("noteId"),
                onNavigateBack = { navController.popBackStack() },
                viewModel = sharedViewModel
            )
        }

        composable(
            route = "new_app_list?noteId={noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            NewAppListScreen(
                noteId = backStackEntry.arguments?.getString("noteId"),
                onNavigateBack = { navController.popBackStack() },
                viewModel = sharedViewModel
            )
        }

        composable(
            route = "new_image?noteId={noteId}&imageUri={imageUri}",
            arguments = listOf(
                navArgument("noteId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("imageUri") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            NewImageScreen(
                noteId = backStackEntry.arguments?.getString("noteId"),
                prefillImageUri = backStackEntry.arguments?.getString("imageUri"),
                onNavigateBack = { navController.popBackStack() },
                viewModel = sharedViewModel
            )
        }

        composable(
            route = "new_pdf?noteId={noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            NewPDFScreen(
                noteId = backStackEntry.arguments?.getString("noteId"),
                onNavigateBack = { navController.popBackStack() },
                viewModel = sharedViewModel
            )
        }

        composable(
            route = "new_audio?noteId={noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            NewAudioScreen(
                noteId = backStackEntry.arguments?.getString("noteId"),
                onNavigateBack = { navController.popBackStack() },
                viewModel = sharedViewModel
            )
        }

        composable("archive") {
            ArchiveScreen(
                onNavigateBack = { navController.popBackStack() },
                onNoteClick = { note ->
                    when {
                        note.noteType == NoteType.LIST || note.isList ->
                            navController.navigate("new_list?noteId=${note.id}")
                        note.noteType == NoteType.QR ->
                            navController.navigate("new_qr?noteId=${note.id}")
                        note.noteType == NoteType.LINK ->
                            navController.navigate("new_link?noteId=${note.id}")
                        note.noteType == NoteType.CONTACT ->
                            navController.navigate("new_contact?noteId=${note.id}")
                        note.noteType == NoteType.LOCATION ->
                            navController.navigate("new_location?noteId=${note.id}")
                        note.noteType == NoteType.APPLIST ->
                            navController.navigate("new_app_list?noteId=${note.id}")
                        note.noteType == NoteType.IMAGE ->
                            navController.navigate("new_image?noteId=${note.id}")
                        note.noteType == NoteType.PDF ->
                            navController.navigate("new_pdf?noteId=${note.id}")
                        else -> navController.navigate("new_note?noteId=${note.id}")
                    }
                },
                viewModel = sharedViewModel
            )
        }

        composable("settings") {
            SettingsScreen(
                currentTheme = currentTheme,
                onThemeChange = onThemeChange,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("sign_in") {
            SignInScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
