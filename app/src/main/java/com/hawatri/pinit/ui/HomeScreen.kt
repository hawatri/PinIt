package com.hawatri.pinit.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToNewNote: () -> Unit,
    onNavigateToNewList: () -> Unit,
    onNavigateToNewLocation: () -> Unit,
    onNavigateToNewQR: () -> Unit,
    onNavigateToNewAppList: () -> Unit,
    onNavigateToNewLink: () -> Unit,
    onNavigateToNewContact: () -> Unit,
    onNavigateToNewImage: () -> Unit
) {
    var showFabMenu by remember { mutableStateOf(false) }
    var selectedBottomTab by remember { mutableIntStateOf(0) }

    var hasNotes by remember { mutableStateOf(false) }
    var hasPinnedNotes by remember { mutableStateOf(false) }
    var hasLabels by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            PinItBottomNavigation(
                selectedItem = selectedBottomTab,
                onItemSelected = { selectedBottomTab = it }
            )
        },
        floatingActionButton = {
            if (selectedBottomTab == 0) {
                LargeFloatingActionButton(
                    onClick = { showFabMenu = !showFabMenu },
                    shape = RoundedCornerShape(24.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                TopSearchBar()
                Spacer(modifier = Modifier.height(24.dp))

                // Animated Tab Switching
                AnimatedContent(
                    targetState = selectedBottomTab,
                    transitionSpec = {
                        // Compare the target tab to the initial tab to determine slide direction
                        if (targetState > initialState) {
                            // Slide left
                            (slideInHorizontally(animationSpec = tween(300)) { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally(animationSpec = tween(300)) { width -> -width } + fadeOut()
                            )
                        } else {
                            // Slide right
                            (slideInHorizontally(animationSpec = tween(300)) { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally(animationSpec = tween(300)) { width -> width } + fadeOut()
                            )
                        }
                    },
                    label = "Tab Transition"
                ) { targetTab ->
                    when (targetTab) {
                        0 -> HomeView(hasItems = hasNotes)
                        1 -> PinnedView(hasItems = hasPinnedNotes)
                        2 -> LabelsView(hasItems = hasLabels)
                    }
                }
            }

            if (showFabMenu && selectedBottomTab == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { showFabMenu = false }
                )

                FabMenu(
                    onDismiss = { showFabMenu = false },
                    onNewNoteClick = onNavigateToNewNote,
                    onNewListClick = onNavigateToNewList,
                    onNewLocationClick = onNavigateToNewLocation,
                    onNewQRClick = onNavigateToNewQR,
                    onNewAppListClick = onNavigateToNewAppList,
                    onNewLinkClick = onNavigateToNewLink,
                    onNewContactClick = onNavigateToNewContact,
                    onNewImageClick = onNavigateToNewImage,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 88.dp)
                )
            }
        }
    }
}

// --- Isolated Content Views ---

@Composable
fun HomeView(hasItems: Boolean) {
    if (hasItems) {
        NoteCard()
    } else {
        EmptyStateView(icon = Icons.Filled.Article, message = "No items")
    }
}

@Composable
fun PinnedView(hasItems: Boolean) {
    if (hasItems) {
        NoteCard()
    } else {
        EmptyStateView(icon = Icons.Filled.PushPin, message = "No pinned items")
    }
}

@Composable
fun LabelsView(hasItems: Boolean) {
    if (hasItems) {
        // Build your Labels list UI here later
    } else {
        EmptyStateView(icon = Icons.Outlined.Label, message = "No labels")
    }
}

// --- Reusable Components ---

@Composable
fun EmptyStateView(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = message,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinItBottomNavigation(selectedItem: Int, onItemSelected: (Int) -> Unit) {
    val items = listOf("Home", "Pinned", "Labels")

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                icon = {
                    when (index) {
                        0 -> Icon(Icons.Filled.Home, contentDescription = item)
                        1 -> Icon(Icons.Filled.PushPin, contentDescription = item)
                        2 -> Icon(Icons.Filled.Label, contentDescription = item)
                    }
                },
                label = { Text(item) },
                selected = selectedItem == index,
                onClick = { onItemSelected(index) }
            )
        }
    }
}