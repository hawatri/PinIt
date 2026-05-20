package com.hawatri.pinit.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.util.NotificationHelper
import com.hawatri.pinit.util.cancelAlarm
import com.hawatri.pinit.util.formatAlarmText
import com.hawatri.pinit.viewmodel.PinItViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewNoteScreen(
    noteId: String? = null,
    sharedText: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: PinItViewModel
) {
    val context = LocalContext.current
    val notificationHelper = remember(context) { NotificationHelper(context) }

    var showReminderMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }

    var title by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf(TextFieldValue(sharedText ?: "")) }
    var isBodyFocused by remember { mutableStateOf(false) }
    var currentNoteId by remember(noteId) { mutableStateOf(noteId) }
    var isPinned by remember { mutableStateOf(false) }

    var formatRanges by remember { mutableStateOf(listOf<FormatRange>()) }
    var activeFormats by remember { mutableStateOf(setOf<FormatType>()) }
    var colorHex by remember { mutableStateOf<String?>(null) }
    var isLocked by remember { mutableStateOf(false) }
    var labels by remember { mutableStateOf(listOf<String>()) }
    var showLabelsSheet by remember { mutableStateOf(false) }

    val undoHistory = remember { ArrayDeque<String>() }
    val redoHistory = remember { ArrayDeque<String>() }
    var lastTextForUndo by remember { mutableStateOf("") }

    val notesList by viewModel.notes.collectAsState()
    var isInitialized by remember { mutableStateOf(false) }
    var currentReminderText by remember { mutableStateOf<String?>(null) }

    fun saveOrUpdateNote(): String? {
        val idToUse = currentNoteId ?: noteId ?: java.util.UUID.randomUUID().toString()
        if (title.isBlank() && noteText.text.isBlank()) return null

        val existing = notesList.find { it.id == idToUse }
        val noteToPersist = com.hawatri.pinit.data.Note(
            id = idToUse,
            title = title,
            text = noteText.text,
            formatRanges = formatRanges,
            isPinned = isPinned,
            isArchived = existing?.isArchived ?: false,
            reminderText = currentReminderText,
            noteType = com.hawatri.pinit.data.NoteType.TEXT,
            colorHex = colorHex,
            isLocked = isLocked,
            labels = labels
        )

        if (existing != null) viewModel.updateNote(noteToPersist) else viewModel.addNote(noteToPersist)
        currentNoteId = idToUse
        return idToUse
    }

    fun togglePin() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasPermission) {
            android.widget.Toast.makeText(context, "Notification permission required to pin", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        isPinned = !isPinned
        val savedId = saveOrUpdateNote() ?: return
        if (isPinned) {
            notificationHelper.pinNoteToNotification(savedId, title, noteText.text)
        } else {
            notificationHelper.unpinNoteFromNotification(savedId)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) togglePin()
        else android.widget.Toast.makeText(context, "Notification permission denied", android.widget.Toast.LENGTH_SHORT).show()
    }

    val reminderPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) android.widget.Toast.makeText(context, "Reminders won't show without notification permission", android.widget.Toast.LENGTH_LONG).show()
    }

    fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            reminderPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Debounced undo history push — fires 400ms after text stops changing
    LaunchedEffect(noteText.text) {
        if (noteText.text != lastTextForUndo) {
            kotlinx.coroutines.delay(400)
            undoHistory.addLast(lastTextForUndo)
            if (undoHistory.size > 50) undoHistory.removeFirst()
            lastTextForUndo = noteText.text
        }
    }

    LaunchedEffect(notesList, noteId) {
        if (noteId != null && !isInitialized && notesList.isNotEmpty()) {
            val existingNote = notesList.find { it.id == noteId }
            if (existingNote != null) {
                title = existingNote.title
                noteText = TextFieldValue(existingNote.text, selection = TextRange(existingNote.text.length))
                formatRanges = existingNote.formatRanges
                currentReminderText = existingNote.reminderText
                isPinned = existingNote.isPinned
                colorHex = existingNote.colorHex
                isLocked = existingNote.isLocked
                labels = existingNote.labels
                isInitialized = true
            }
        }
    }

    fun toggleFormat(type: FormatType) {
        val selection = noteText.selection
        if (selection.start != selection.end) {
            val min = minOf(selection.start, selection.end)
            val max = maxOf(selection.start, selection.end)
            val existing = formatRanges.find { it.type == type && it.start == min && it.end == max }
            formatRanges = if (existing != null) formatRanges - existing else formatRanges + FormatRange(type, min, max)
        } else {
            activeFormats = if (activeFormats.contains(type)) activeFormats - type else activeFormats + type
        }
    }

    fun cycleChecklist() {
        val text = noteText.text
        val cursor = noteText.selection.min

        val lineStart = text.lastIndexOf('\n', cursor - 1).takeIf { it != -1 }?.plus(1) ?: 0
        val lineEnd = text.indexOf('\n', cursor).takeIf { it != -1 } ?: text.length
        val lineText = text.substring(lineStart, lineEnd)

        val newText: String
        val newCursor: Int

        if (lineText.startsWith("☐ ")) {
            newText = text.replaceRange(lineStart, lineStart + 2, "☑ ")
            newCursor = cursor
        } else if (lineText.startsWith("☑ ")) {
            newText = text.replaceRange(lineStart, lineStart + 2, "☐ ")
            newCursor = cursor
        } else {
            val prefix = if (cursor == lineStart && lineText.isEmpty()) "☐ " else "\n☐ "
            newText = text.replaceRange(cursor, cursor, prefix)
            newCursor = cursor + prefix.length

            formatRanges = formatRanges.mapNotNull { range ->
                if (range.start >= cursor) range.copy(start = range.start + prefix.length, end = range.end + prefix.length)
                else if (range.end > cursor) range.copy(end = range.end + prefix.length)
                else range
            }
        }
        noteText = TextFieldValue(newText, TextRange(newCursor))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    // Share button
                    IconButton(onClick = {
                        val shareText = buildString {
                            if (title.isNotBlank()) { append(title); append("\n\n") }
                            append(noteText.text)
                        }
                        if (shareText.isNotBlank()) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share note"))
                        }
                    }) {
                        Icon(Icons.Filled.Share, "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Archive button
                    IconButton(onClick = {
                        val idToUse = currentNoteId ?: noteId
                        if (idToUse != null) {
                            notesList.find { it.id == idToUse }?.let { note ->
                                if (note.isPinned) notificationHelper.unpinNoteFromNotification(note.id)
                                viewModel.toggleArchive(note)
                            }
                        } else {
                            // New unsaved note — save then archive
                            val savedId = saveOrUpdateNote()
                            if (savedId != null) {
                                notesList.find { it.id == savedId }?.let { viewModel.toggleArchive(it) }
                            }
                        }
                        onNavigateBack()
                    }) {
                        Icon(Icons.Filled.Archive, contentDescription = "Archive", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Pin toggle button
                    IconButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                togglePin()
                            }
                        }
                    ) {
                        Icon(
                            if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { 
                            checkNotificationPermission()
                            showReminderMenu = true 
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = "Set Reminder",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showReminderMenu,
                            onDismissRequest = { showReminderMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Tomorrow (8:00 AM)") },
                                onClick = {
                                    showReminderMenu = false
                                    currentReminderText = "Tomorrow, 8:00 AM"
                                    val noteToPinId = saveOrUpdateNote() ?: return@DropdownMenuItem
                                    val scheduled = com.hawatri.pinit.util.setTomorrowAlarm(
                                        context = context,
                                        noteId = noteToPinId,
                                        noteTitle = title
                                    )
                                    if (scheduled) {
                                        currentReminderText = formatAlarmText(
                                            java.util.Calendar.getInstance().apply {
                                                add(java.util.Calendar.DAY_OF_YEAR, 1)
                                                set(java.util.Calendar.HOUR_OF_DAY, 8)
                                                set(java.util.Calendar.MINUTE, 0)
                                                set(java.util.Calendar.SECOND, 0)
                                            }
                                        )
                                        saveOrUpdateNote()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Pick date and time") },
                                onClick = {
                                    showReminderMenu = false
                                    showDatePicker = true
                                }
                            )
                        }
                    }
                    IconButton(onClick = { showLabelsSheet = true }) {
                        Icon(Icons.Filled.Label, "Label",
                            tint = if (labels.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { isLocked = !isLocked; saveOrUpdateNote() }) {
                        Icon(
                            if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = if (isLocked) "Locked" else "Unlocked",
                            tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { saveOrUpdateNote(); onNavigateBack() }) {
                        Icon(Icons.Filled.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding(),
            verticalArrangement = Arrangement.Bottom
        ) {

            Spacer(modifier = Modifier.weight(1f))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "*Mandatory field",
                        color = Color(0xFFD32F2F),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                    )

                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Title*", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        textStyle = TextStyle(fontSize = 24.sp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState -> if (focusState.isFocused) isBodyFocused = false }
                    )

                    if (currentReminderText != null) {
                        androidx.compose.material3.AssistChip(
                            onClick = { showReminderMenu = true },
                            label = { Text(currentReminderText!!) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Notifications,
                                    contentDescription = "Alarm",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove Alarm",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            val idToCancel = currentNoteId ?: noteId
                                            if (idToCancel != null) {
                                                cancelAlarm(context, idToCancel)
                                            }
                                            currentReminderText = null
                                            saveOrUpdateNote()
                                        }
                                )
                            },
                            colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = null,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                        )
                    }

                    TextField(
                        value = noteText,
                        onValueChange = { newValue ->
                            val oldText = noteText.text
                            val newText = newValue.text
                            val lengthDiff = newText.length - oldText.length

                            if (lengthDiff != 0) {
                                val cursor = newValue.selection.min
                                val editPos = if (lengthDiff > 0) cursor - lengthDiff else cursor

                                var updatedRanges = formatRanges.mapNotNull { range ->
                                    var start = range.start
                                    var end = range.end

                                    if (lengthDiff > 0) {
                                        if (start >= editPos) start += lengthDiff
                                        if (end > editPos) end += lengthDiff
                                    } else {
                                        val delEnd = editPos - lengthDiff
                                        if (start >= delEnd) start += lengthDiff else if (start >= editPos) start = editPos
                                        if (end >= delEnd) end += lengthDiff else if (end >= editPos) end = editPos
                                    }
                                    if (start >= end) null else FormatRange(range.type, start, end)
                                }

                                if (lengthDiff > 0 && activeFormats.isNotEmpty()) {
                                    activeFormats.forEach { type ->
                                        updatedRanges = updatedRanges + FormatRange(type, editPos, editPos + lengthDiff)
                                    }
                                }

                                val mergedRanges = mutableListOf<FormatRange>()
                                FormatType.values().forEach { type ->
                                    val typeRanges = updatedRanges.filter { it.type == type }.sortedBy { it.start }
                                    if (typeRanges.isNotEmpty()) {
                                        var cStart = typeRanges[0].start
                                        var cEnd = typeRanges[0].end
                                        for (i in 1 until typeRanges.size) {
                                            val next = typeRanges[i]
                                            if (next.start <= cEnd) {
                                                cEnd = maxOf(cEnd, next.end)
                                            } else {
                                                mergedRanges.add(FormatRange(type, cStart, cEnd))
                                                cStart = next.start
                                                cEnd = next.end
                                            }
                                        }
                                        mergedRanges.add(FormatRange(type, cStart, cEnd))
                                    }
                                }

                                formatRanges = mergedRanges
                            }
                            if (newValue.text != noteText.text) redoHistory.clear()
                            noteText = newValue
                        },
                        placeholder = { Text("Text*", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        visualTransformation = RichTextVisualTransformation(formatRanges),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState -> if (focusState.isFocused) isBodyFocused = true }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FormatIcon(
                    icon = Icons.Filled.FormatBold,
                    isActive = activeFormats.contains(FormatType.BOLD),
                    isEnabled = isBodyFocused,
                    onClick = { toggleFormat(FormatType.BOLD) }
                )
                FormatIcon(
                    icon = Icons.Filled.FormatItalic,
                    isActive = activeFormats.contains(FormatType.ITALIC),
                    isEnabled = isBodyFocused,
                    onClick = { toggleFormat(FormatType.ITALIC) }
                )
                FormatIcon(
                    icon = Icons.Filled.StrikethroughS,
                    isActive = activeFormats.contains(FormatType.STRIKETHROUGH),
                    isEnabled = isBodyFocused,
                    onClick = { toggleFormat(FormatType.STRIKETHROUGH) }
                )
                FormatIcon(
                    icon = Icons.Filled.Title,
                    isActive = activeFormats.contains(FormatType.HEADING),
                    isEnabled = isBodyFocused,
                    onClick = { toggleFormat(FormatType.HEADING) }
                )
                FormatIcon(
                    icon = Icons.Filled.FormatListBulleted,
                    isActive = false,
                    isEnabled = isBodyFocused,
                    onClick = {
                        val insert = if (noteText.selection.min == 0) "• " else "\n• "
                        val newText = noteText.text.substring(0, noteText.selection.min) + insert + noteText.text.substring(noteText.selection.max)
                        noteText = TextFieldValue(newText, TextRange(noteText.selection.min + insert.length))
                    }
                )
                FormatIcon(
                    icon = Icons.Filled.Checklist,
                    isActive = false,
                    isEnabled = isBodyFocused,
                    onClick = { cycleChecklist() }
                )

                // Undo
                FormatIcon(
                    icon = Icons.Filled.Undo,
                    isActive = false,
                    isEnabled = undoHistory.isNotEmpty(),
                    onClick = {
                        if (undoHistory.isNotEmpty()) {
                            val prev = undoHistory.removeLast()
                            redoHistory.addLast(noteText.text)
                            noteText = androidx.compose.ui.text.input.TextFieldValue(prev, androidx.compose.ui.text.TextRange(prev.length))
                        }
                    }
                )
                // Redo
                FormatIcon(
                    icon = Icons.Filled.Redo,
                    isActive = false,
                    isEnabled = redoHistory.isNotEmpty(),
                    onClick = {
                        if (redoHistory.isNotEmpty()) {
                            val next = redoHistory.removeLast()
                            undoHistory.addLast(noteText.text)
                            noteText = androidx.compose.ui.text.input.TextFieldValue(next, androidx.compose.ui.text.TextRange(next.length))
                        }
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                NoteColorPicker(
                    selectedColor = colorHex,
                    onColorSelected = { colorHex = it.ifBlank { null }; saveOrUpdateNote() }
                )
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedDateMillis = datePickerState.selectedDateMillis
                        showDatePicker = false
                        showTimePicker = true // Open TimePicker immediately after DatePicker
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time Picker Dialog
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select time") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTimePicker = false
                        
                        val calendar = java.util.Calendar.getInstance()
                        calendar.timeInMillis = selectedDateMillis ?: System.currentTimeMillis()
                        calendar.set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                        calendar.set(java.util.Calendar.MINUTE, timePickerState.minute)
                        val sdf = java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.getDefault())
                        currentReminderText = sdf.format(calendar.time)
                        
                        val noteToPinId = saveOrUpdateNote() ?: return@TextButton
                        
                        val scheduled = com.hawatri.pinit.util.scheduleCustomAlarm(
                            context = context,
                            noteId = noteToPinId,
                            noteTitle = title,
                            dateMillis = selectedDateMillis,
                            hour = timePickerState.hour,
                            minute = timePickerState.minute
                        )
                        if (!scheduled) return@TextButton
                        currentReminderText = formatAlarmText(calendar)
                        saveOrUpdateNote()
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Labels editor sheet
    if (showLabelsSheet) {
        val allLabels = remember(notesList) { notesList.flatMap { it.labels }.distinct() }
        LabelsEditorSheet(
            currentLabels = labels,
            allExistingLabels = allLabels,
            onLabelsChange = { labels = it; saveOrUpdateNote() },
            onDismiss = { showLabelsSheet = false }
        )
    }
}

@Composable
fun FormatIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val backgroundAlpha = if (isActive) 0.8f else if (isEnabled) 0.3f else 0.1f
    val iconTintAlpha = if (isActive) 1.0f else if (isEnabled) 0.7f else 0.3f

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = backgroundAlpha))
            .clickable(enabled = isEnabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = iconTintAlpha),
            modifier = Modifier.size(20.dp)
        )
    }
}