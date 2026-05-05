package com.hawatri.pinit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import java.util.UUID
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.graphicsLayer

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.hawatri.pinit.util.NotificationHelper
import com.google.gson.Gson
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.viewmodel.PinItViewModel

data class ChecklistItemData(
    val id: String? = UUID.randomUUID().toString(), // NEW: Helps Compose track animations
    val text: String = "", 
    val isChecked: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class) // <-- Updated OptIn
@Composable
fun NewListScreen(
    noteId: String? = null, // <-- NEW PARAMETER
    onNavigateBack: () -> Unit,
    viewModel: PinItViewModel
) {
    val context = LocalContext.current
    val notificationHelper = remember(context) { NotificationHelper(context) }

    // State for dialogs and menus
    var showReminderMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // States for pickers
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }

    var title by remember { mutableStateOf("") }
    val checklistItems = remember { mutableStateListOf<ChecklistItemData>() }
    val gson = remember { Gson() }
    
    // --- NEW: Load Existing List Logic ---
    val notesList by viewModel.notes.collectAsState()
    var isInitialized by remember { mutableStateOf(false) }
    var currentNoteId by remember(noteId) { mutableStateOf(noteId) }

    LaunchedEffect(notesList, noteId) {
        if (noteId != null && !isInitialized && notesList.isNotEmpty()) {
            val existingNote = notesList.find { it.id == noteId }
            if (existingNote != null) {
                title = existingNote.title
                val items = try {
                    gson.fromJson(existingNote.text, Array<ChecklistItemData>::class.java).toList()
                } catch (e: Exception) { emptyList() }
                
                checklistItems.clear()
                checklistItems.addAll(items)
                isInitialized = true
            }
        }
    }
    // -------------------------------------

    fun saveList(pinOverride: Boolean? = null, archiveOverride: Boolean? = null): String? {
        val validItems = checklistItems.filter { it.text.isNotBlank() }
        if (title.isBlank() && validItems.isEmpty()) return null

        // --- NEW: Update instead of add ---
        val idToUse = currentNoteId ?: noteId ?: java.util.UUID.randomUUID().toString()
        val existing = notesList.find { it.id == idToUse }
        
        val jsonText = gson.toJson(validItems)
        
        val isPinned = pinOverride ?: existing?.isPinned ?: false
        val isArchived = archiveOverride ?: existing?.isArchived ?: false

        val note = Note(
            id = idToUse,
            title = title,
            text = jsonText,
            formatRanges = emptyList(),
            isPinned = isPinned,
            isArchived = isArchived,
            isList = true
        )

        if (existing != null) {
            viewModel.updateNote(note)
        } else {
            viewModel.addNote(note)
        }
        currentNoteId = idToUse
        return idToUse
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) return@rememberLauncherForActivityResult
        val validItems = checklistItems.filter { it.text.isNotBlank() }
        val jsonText = gson.toJson(validItems)
        val savedNoteId = saveList(pinOverride = true) ?: return@rememberLauncherForActivityResult
        notificationHelper.pinNoteToNotification(savedNoteId, title, jsonText, isList = true)
    }

    val reminderPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            android.widget.Toast.makeText(context, "Reminders won't show without notification permission", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            reminderPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
                    // PIN BUTTON
                    IconButton(onClick = { 
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val hasNotificationPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                            if (hasNotificationPermission) {
                                val savedNoteId = saveList(pinOverride = true) ?: return@IconButton
                                val validItems = checklistItems.filter { it.text.isNotBlank() }
                                val jsonText = gson.toJson(validItems)
                                notificationHelper.pinNoteToNotification(savedNoteId, title, jsonText, isList = true)
                                onNavigateBack()
                            } else {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            val savedNoteId = saveList(pinOverride = true) ?: return@IconButton
                            val validItems = checklistItems.filter { it.text.isNotBlank() }
                            val jsonText = gson.toJson(validItems)
                            notificationHelper.pinNoteToNotification(savedNoteId, title, jsonText, isList = true)
                            onNavigateBack()
                        }
                    }) { Icon(Icons.Filled.PushPin, contentDescription = "Pin", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    
                    // ARCHIVE BUTTON (Replaced Reminder with Archive for consistency)
                    IconButton(onClick = { 
                        saveList(archiveOverride = true)
                        onNavigateBack()
                    }) { Icon(Icons.Filled.Archive, contentDescription = "Archive", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    
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
                                    val noteToPinId = saveList() ?: return@DropdownMenuItem
                                    com.hawatri.pinit.util.setTomorrowAlarm(
                                        context = context,
                                        noteId = noteToPinId,
                                        noteTitle = title
                                    )
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
                    IconButton(onClick = { }) { Icon(Icons.Filled.Label, contentDescription = "Label", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    
                    // SAVE BUTTON
                    IconButton(onClick = { 
                        saveList()
                        onNavigateBack()
                    }) { Icon(Icons.Filled.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                .imePadding()
        ) {
            // Input Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .weight(1f), 
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
            ) {
                val listState = rememberLazyListState() 
                // NEW: Track exactly which item is being held
                var draggedItemId by remember { mutableStateOf<String?>(null) } 
                
                LaunchedEffect(checklistItems.size) {
                    if (checklistItems.isNotEmpty()) {
                        listState.animateScrollToItem(checklistItems.size + 1)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxSize()
                        .animateContentSize() 
                ) {
                    // Header items (Mandatory text & Title)
                    item {
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
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            textStyle = TextStyle(fontSize = 24.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Checklist Items loop
                    items(
                        count = checklistItems.size, 
                        key = { checklistItems[it].id ?: UUID.randomUUID().toString() } 
                    ) { index ->
                        val item = checklistItems[index]
                        val isDragging = draggedItemId == item.id // Check if this is the active item
                        
                        EditableChecklistItem(
                            item = item,
                            isDragging = isDragging, // Pass this down
                            // FIX: Only animate items you AREN'T holding so they slide out of the way smoothly
                            modifier = if (isDragging) Modifier else Modifier.animateItem(), 
                            onDragStart = { draggedItemId = item.id },
                            onDragEnd = { draggedItemId = null },
                            onTextChange = { newText -> 
                                val safeIndex = checklistItems.indexOf(item)
                                if (safeIndex != -1) checklistItems[safeIndex] = item.copy(text = newText) 
                            },
                            onCheckedChange = { checked -> 
                                val safeIndex = checklistItems.indexOf(item)
                                if (safeIndex != -1) checklistItems[safeIndex] = item.copy(isChecked = checked) 
                            },
                            onRemove = { checklistItems.remove(item) },
                            // FIX: Dynamically check the index to prevent rapid-drag crashes
                            onMoveUp = {
                                val currentIndex = checklistItems.indexOf(item)
                                if (currentIndex > 0) {
                                    val temp = checklistItems[currentIndex]
                                    checklistItems[currentIndex] = checklistItems[currentIndex - 1]
                                    checklistItems[currentIndex - 1] = temp
                                }
                            },
                            onMoveDown = {
                                val currentIndex = checklistItems.indexOf(item)
                                if (currentIndex >= 0 && currentIndex < checklistItems.size - 1) {
                                    val temp = checklistItems[currentIndex]
                                    checklistItems[currentIndex] = checklistItems[currentIndex + 1]
                                    checklistItems[currentIndex + 1] = temp
                                }
                            }
                        )
                    }

                    // Bottom "Add Item" button
                    item {
                        Row(
                            modifier = Modifier
                                .padding(start = 48.dp, top = 8.dp, bottom = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { checklistItems.add(ChecklistItemData()) }
                                .padding(vertical = 8.dp, horizontal = 4.dp), 
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add item", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                    }
                }
            }

            // Bottom formatting bar (Reusing your UI pattern)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FormatIcon(
                    icon = Icons.Filled.GridView,
                    isActive = false, // Changed from alpha to isActive
                    isEnabled = true,
                    onClick = { /* Handle grid layout toggle later */ }
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
                        val noteToPinId = saveList() ?: return@TextButton
                        
                        com.hawatri.pinit.util.scheduleCustomAlarm(
                            context = context,
                            noteId = noteToPinId,
                            noteTitle = title,
                            dateMillis = selectedDateMillis,
                            hour = timePickerState.hour,
                            minute = timePickerState.minute
                        )
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditableChecklistItem(
    item: ChecklistItemData,
    modifier: Modifier = Modifier,
    isDragging: Boolean, // NEW
    onDragStart: () -> Unit, // NEW
    onDragEnd: () -> Unit, // NEW
    onTextChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var offsetY by remember { mutableFloatStateOf(0f) }
    var itemHeight by remember { mutableIntStateOf(0) } // NEW: Tracks exact row height

    Row(
        modifier = modifier
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = offsetY
                // Visual polish: Pops the item up slightly when held
                scaleX = if (isDragging) 1.02f else 1f 
                scaleY = if (isDragging) 1.02f else 1f
                shadowElevation = if (isDragging) 8f else 0f
            }
            .onGloballyPositioned { itemHeight = it.size.height } // Captures exact pixel height
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.DragIndicator,
            contentDescription = "Drag",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(24.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = { 
                            offsetY = 0f
                            onDragEnd() 
                        },
                        onDragCancel = { 
                            offsetY = 0f
                            onDragEnd() 
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetY += dragAmount
                        
                        // Safety check to ensure height is calculated
                        if (itemHeight > 0) {
                            // Swap when you drag past 50% of the item's actual height
                            val swapThreshold = itemHeight * 0.5f 
                            
                            if (offsetY > swapThreshold) {
                                onMoveDown()
                                offsetY -= itemHeight // Instantly correct offset so it stays under finger
                            } else if (offsetY < -swapThreshold) {
                                onMoveUp()
                                offsetY += itemHeight
                            }
                        }
                    }
                }
        )
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        TextField(
            value = item.text,
            onValueChange = onTextChange,
            placeholder = { Text("Item", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}