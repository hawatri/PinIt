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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.util.NotificationHelper
import com.hawatri.pinit.viewmodel.PinItViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    onNavigateBack: () -> Unit,
    onNoteClick: (Note) -> Unit,
    viewModel: PinItViewModel
) {
    val context = LocalContext.current
    val notificationHelper = remember(context) { NotificationHelper(context) }
    val allNotes by viewModel.notes.collectAsState()
    val archivedNotes = allNotes.filter { it.isArchived }

    var selectedNoteIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedNoteIds.isNotEmpty()

    val pendingPinAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    val notifPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingPinAction.value
        pendingPinAction.value = null
        if (granted) {
            action?.invoke()
        } else {
            android.widget.Toast.makeText(context, "Notification permission required to pin", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    fun runWithNotifPermission(action: () -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingPinAction.value = action
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSelectionMode) selectedNoteIds.size.toString() else "Archive") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) selectedNoteIds = emptySet() else onNavigateBack()
                    }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            selectedNoteIds.forEach { id ->
                                allNotes.find { it.id == id }?.let { note -> viewModel.toggleArchive(note) }
                            }
                            selectedNoteIds = emptySet()
                        }) { Icon(Icons.Filled.Unarchive, "Unarchive") }

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
                NotesGrid(
                    notes = archivedNotes,
                    selectedNoteIds = selectedNoteIds,
                    isSelectionMode = isSelectionMode,
                    onNoteClick = { id ->
                        if (isSelectionMode) {
                            selectedNoteIds = if (selectedNoteIds.contains(id)) selectedNoteIds - id else selectedNoteIds + id
                        } else {
                            archivedNotes.find { it.id == id }?.let { onNoteClick(it) }
                        }
                    },
                    onNoteLongClick = { id -> selectedNoteIds = selectedNoteIds + id },
                    onPinClick = { note ->
                        runWithNotifPermission {
                            val willBePinned = !note.isPinned
                            viewModel.togglePin(note)
                            if (willBePinned) {
                                notificationHelper.pinNoteToNotification(note.id, note.title, note.text, note.isList, note.noteType)
                            } else {
                                notificationHelper.unpinNoteFromNotification(note.id)
                            }
                        }
                    },
                    onCopyClick = { text ->
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Copied Note", text))
                        android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onToggleAllClick = { note ->
                        val items = try {
                            Gson().fromJson(note.text, Array<ChecklistItemData>::class.java).toList()
                        } catch (_: Exception) { emptyList() }
                        val allChecked = items.isNotEmpty() && items.all { it.isChecked }
                        val newItems = items.map { it.copy(isChecked = !allChecked) }
                        val updated = note.copy(text = Gson().toJson(newItems))
                        viewModel.updateNote(updated)
                        if (updated.isPinned) {
                            notificationHelper.pinNoteToNotification(updated.id, updated.title, updated.text, true)
                        }
                    }
                )
            } else {
                EmptyStateView(icon = Icons.Filled.Archive, message = "Archive is empty")
            }
        }
    }
}
