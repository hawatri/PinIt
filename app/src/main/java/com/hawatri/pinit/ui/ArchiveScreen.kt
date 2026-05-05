package com.hawatri.pinit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hawatri.pinit.viewmodel.PinItViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    onNavigateBack: () -> Unit,
    onNoteClick: (String) -> Unit,
    onListClick: (String) -> Unit, // <-- NEW PARAMETER
    viewModel: PinItViewModel
) {
    val allNotes by viewModel.notes.collectAsState()
    val archivedNotes = allNotes.filter { it.isArchived }

    var selectedNoteIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedNoteIds.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSelectionMode) selectedNoteIds.size.toString() else "Archive") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) selectedNoteIds = emptySet() else onNavigateBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        // Unarchive Button
                        IconButton(onClick = {
                            selectedNoteIds.forEach { id ->
                                allNotes.find { it.id == id }?.let { note -> viewModel.toggleArchive(note) }
                            }
                            selectedNoteIds = emptySet()
                        }) { Icon(Icons.Filled.Unarchive, "Unarchive") }

                        // Delete Button
                        IconButton(onClick = {
                            selectedNoteIds.forEach { id -> viewModel.deleteNote(id) }
                            selectedNoteIds = emptySet()
                        }) { Icon(Icons.Filled.Delete, "Delete") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)) {
            if (archivedNotes.isNotEmpty()) {
                // Reusing the grid from your HomeScreen!
                NotesGrid(
                    notes = archivedNotes,
                    selectedNoteIds = selectedNoteIds,
                    isSelectionMode = isSelectionMode,
                    onNoteClick = { id ->
                        if (isSelectionMode) {
                            selectedNoteIds = if (selectedNoteIds.contains(id)) selectedNoteIds - id else selectedNoteIds + id
                        } else {
                            val clickedNote = archivedNotes.find { it.id == id }
                            if (clickedNote?.isList == true) {
                                onListClick(id)
                            } else {
                                onNoteClick(id)
                            }
                        }
                    },
                    onNoteLongClick = { id -> selectedNoteIds = selectedNoteIds + id },
                    onPinClick = { note -> viewModel.togglePin(note) } 
                )
            } else {
                EmptyStateView(icon = Icons.Filled.Archive, message = "Archive is empty")
            }
        }
    }
}
