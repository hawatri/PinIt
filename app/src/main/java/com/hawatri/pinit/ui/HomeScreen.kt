package com.hawatri.pinit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    onNavigateToNewAppList: () -> Unit
) {
    var showFabMenu by remember { mutableStateOf(false) }
    var selectedBottomTab by remember { mutableIntStateOf(0) }

    // Toggle this to true later when implementing list logic
    var hasItems by remember { mutableStateOf(false) }

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

                if (hasItems) {
                    Spacer(modifier = Modifier.height(24.dp))
                    if (selectedBottomTab == 0 || selectedBottomTab == 1) {
                        NoteCard()
                    }
                } else {
                    // Empty State
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Article,
                                contentDescription = "No items",
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No items",
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 88.dp)
                )
            }
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