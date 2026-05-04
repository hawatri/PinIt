package com.hawatri.pinit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.viewmodel.PinItViewModel

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
    onNavigateToNewImage: () -> Unit,
    viewModel: PinItViewModel = viewModel() // Inject ViewModel
) {
    var showFabMenu by remember { mutableStateOf(false) }
    var selectedBottomTab by remember { mutableIntStateOf(0) }

    // Read the master list of notes from the ViewModel
    val allNotes by viewModel.notes.collectAsState()

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
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Add", modifier = Modifier.size(36.dp))
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
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                TopSearchBar()
                Spacer(modifier = Modifier.height(16.dp))

                // Pass the filtered data into the views
                when (selectedBottomTab) {
                    0 -> HomeView(notes = allNotes)
                    1 -> PinnedView(notes = allNotes.filter { it.isPinned })
                    2 -> LabelsView(hasItems = false) // Labels logic comes later
                }
            }

            // FAB Menu Overlay
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

// --- Content Views ---

@Composable
fun HomeView(notes: List<Note>) {
    if (notes.isNotEmpty()) {
        NotesGrid(notes = notes)
    } else {
        EmptyStateView(icon = Icons.Filled.Article, message = "No items")
    }
}

@Composable
fun PinnedView(notes: List<Note>) {
    if (notes.isNotEmpty()) {
        NotesGrid(notes = notes)
    } else {
        EmptyStateView(icon = Icons.Filled.PushPin, message = "No pinned items")
    }
}

@Composable
fun LabelsView(hasItems: Boolean) {
    if (hasItems) {
        // Labels UI
    } else {
        EmptyStateView(icon = Icons.Outlined.Label, message = "No labels")
    }
}

// --- The Staggered Grid ---

@Composable
fun NotesGrid(notes: List<Note>) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp
    ) {
        items(notes, key = { it.id }) { note ->
            NoteCard(note = note)
        }
    }
}

@Composable
fun NoteCard(note: Note) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: Open Note for Editing */ },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (note.title.isNotBlank()) {
                Text(
                    text = note.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (note.text.isNotBlank()) {
                Text(
                    // Format the text using our helper function!
                    text = buildFormattedString(note.text, note.formatRanges),
                    fontSize = 14.sp,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --- Reusable Components (Unchanged) ---

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