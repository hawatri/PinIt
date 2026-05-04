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
                onNavigateToNewNote = {
                    navController.navigate("new_note")
                },
                onNavigateToNewList = {
                    navController.navigate("new_list")
                }
            )
        }
        composable("new_note") {
            NewNoteScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable("new_list") {
            NewListScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}