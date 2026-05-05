package com.hawatri.pinit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.viewmodel.PinItViewModel
import androidx.compose.ui.platform.LocalContext
import com.hawatri.pinit.util.NotificationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNoteClick: (String) -> Unit, // New Parameter
    onNavigateToNewNote: () -> Unit,
    onNavigateToNewList: () -> Unit,
    onNavigateToNewLocation: () -> Unit,
    onNavigateToNewQR: () -> Unit,
    onNavigateToNewAppList: () -> Unit,
    onNavigateToNewLink: () -> Unit,
    onNavigateToNewContact: () -> Unit,
    onNavigateToNewImage: () -> Unit,
    viewModel: PinItViewModel
) {
    var showFabMenu by remember { mutableStateOf(false) }
    var selectedBottomTab by remember { mutableIntStateOf(0) }
    val allNotes by viewModel.notes.collectAsState()

    // 1. Selection State
    var selectedNoteIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedNoteIds.isNotEmpty()

    // NEW: Initialize Notification Helper
    val context = LocalContext.current
    val notificationHelper = remember(context) { NotificationHelper(context) }

    Scaffold(
        bottomBar = {
            PinItBottomNavigation(
                selectedItem = selectedBottomTab,
                onItemSelected = { selectedBottomTab = it }
            )
        },
        floatingActionButton = {
            if (selectedBottomTab == 0 && !isSelectionMode) { // Hide FAB during selection
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
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(16.dp))

                // 2. Contextual Top Bar logic
                if (isSelectionMode) {
                    TopAppBar(
                        title = { Text(selectedNoteIds.size.toString()) },
                        navigationIcon = {
                            IconButton(onClick = { selectedNoteIds = emptySet() }) { Icon(Icons.Filled.Close, "Clear") }
                        },
                        actions = {
                            // CHANGED: Pin action replaced with Archive action
                            IconButton(onClick = {
                                selectedNoteIds.forEach { id ->
                                    allNotes.find { it.id == id }?.let { note -> viewModel.toggleArchive(note) }
                                }
                                selectedNoteIds = emptySet()
                            }) { Icon(Icons.Filled.Archive, "Archive") }

                            IconButton(onClick = {
                                selectedNoteIds.forEach { id -> viewModel.deleteNote(id) }
                                selectedNoteIds = emptySet()
                            }) { Icon(Icons.Filled.Delete, "Delete") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                } else {
                    TopSearchBar()
                }

                Spacer(modifier = Modifier.height(16.dp))

                // CHANGED: Filter out archived notes from Home and Pinned tabs
                val displayNotes = when (selectedBottomTab) {
                    1 -> allNotes.filter { it.isPinned && !it.isArchived }
                    else -> allNotes.filter { !it.isArchived } 
                }

                if (displayNotes.isNotEmpty()) {
                    NotesGrid(
                        notes = displayNotes,
                        selectedNoteIds = selectedNoteIds,
                        isSelectionMode = isSelectionMode,
                        onNoteClick = { id ->
                            if (isSelectionMode) {
                                selectedNoteIds = if (selectedNoteIds.contains(id)) selectedNoteIds - id else selectedNoteIds + id
                            } else {
                                onNoteClick(id)
                            }
                        },
                        onNoteLongClick = { id -> selectedNoteIds = selectedNoteIds + id },
                        onPinClick = { note -> 
                            // NEW: Update DB and Notification
                            val willBePinned = !note.isPinned
                            viewModel.togglePin(note)
                            
                            if (willBePinned) {
                                notificationHelper.pinNoteToNotification(note.id, note.title, note.text)
                            } else {
                                notificationHelper.unpinNoteFromNotification(note.id)
                            }
                        }
                    )
                } else {
                    val icon = if (selectedBottomTab == 1) Icons.Filled.PushPin else Icons.Filled.Article
                    val msg = if (selectedBottomTab == 1) "No pinned items" else "No items"
                    EmptyStateView(icon = icon, message = msg)
                }
            }

            if (showFabMenu && selectedBottomTab == 0 && !isSelectionMode) {
                Box(modifier = Modifier.fillMaxSize().clickable { showFabMenu = false })
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
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 88.dp)
                )
            }
        }
    }
}

@Composable
fun NotesGrid(
    notes: List<Note>,
    selectedNoteIds: Set<String>,
    isSelectionMode: Boolean,
    onNoteClick: (String) -> Unit,
    onNoteLongClick: (String) -> Unit,
    onPinClick: (Note) -> Unit
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp
    ) {
        items(notes, key = { it.id }) { note ->
            NoteCard(
                note = note,
                isSelected = selectedNoteIds.contains(note.id),
                onClick = { onNoteClick(note.id) },
                onLongClick = { onNoteLongClick(note.id) },
                onPinClick = { onPinClick(note) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPinClick: () -> Unit
) {
    // 3. Highlight the border if selected
    val borderStroke = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
    else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = borderStroke
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                if (note.title.isNotBlank()) {
                    Text(
                        text = note.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                IconButton(
                    onClick = onPinClick,
                    modifier = Modifier.size(28.dp) // slightly larger for better touch target
                ) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "Toggle Pin",
                        tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (note.text.isNotBlank()) {
                Text(
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

// ... the rest (EmptyStateView & BottomNavigation) remain identical ...

@Composable
fun EmptyStateView(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = message, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = message, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PinItBottomNavigation(selectedItem: Int, onItemSelected: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        listOf("Home", "Pinned", "Labels").forEachIndexed { index, item ->
            NavigationBarItem(
                icon = { Icon(when(index){ 0->Icons.Filled.Home; 1->Icons.Filled.PushPin; else->Icons.Filled.Label }, item) },
                label = { Text(item) }, selected = selectedItem == index, onClick = { onItemSelected(index) }
            )
        }
    }
}