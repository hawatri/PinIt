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
                // CHANGED: Use LazyListState instead of ScrollState
                val listState = rememberLazyListState() 
                
                LaunchedEffect(checklistItems.size) {
                    if (checklistItems.isNotEmpty()) {
                        listState.animateScrollToItem(checklistItems.size + 1)
                    }
                }

                // CHANGED: LazyColumn enables built-in smooth reordering animations
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxSize()
                        .animateContentSize() // <-- SMOOTH overall resizing when adding/removing
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
                        key = { checklistItems[it].id ?: UUID.randomUUID().toString() } // Tracks items for animation
                    ) { index ->
                        val item = checklistItems[index]
                        EditableChecklistItem(
                            item = item,
                            // Item translation is handled by drag offset; keep modifier plain for compatibility.
                            modifier = Modifier,
                            onTextChange = { newText -> checklistItems[index] = item.copy(text = newText) },
                            onCheckedChange = { checked -> checklistItems[index] = item.copy(isChecked = checked) },
                            onRemove = { checklistItems.removeAt(index) },
                            onMoveUp = {
                                if (index > 0) {
                                    val temp = checklistItems[index]
                                    checklistItems[index] = checklistItems[index - 1]
                                    checklistItems[index - 1] = temp
                                }
                            },
                            onMoveDown = {
                                if (index < checklistItems.size - 1) {
                                    val temp = checklistItems[index]
                                    checklistItems[index] = checklistItems[index + 1]
                                    checklistItems[index + 1] = temp
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditableChecklistItem(
    item: ChecklistItemData,
    modifier: Modifier = Modifier, // <-- NEW
    onTextChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var offsetY by remember { mutableFloatStateOf(0f) }
    val isDragging = offsetY != 0f
    val density = LocalDensity.current.density
    val swapThreshold = 40 * density 

    Row(
        modifier = modifier
            .zIndex(if (isDragging) 1f else 0f) // Brings the dragged item to the front
            .offset { IntOffset(0, offsetY.roundToInt()) } // Makes it follow your finger exactly
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
                        onDragEnd = { offsetY = 0f },
                        onDragCancel = { offsetY = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetY += dragAmount
                        
                        // Swap logic that smoothly shifts the offset so it doesn't snap back instantly
                        if (offsetY > swapThreshold) {
                            onMoveDown()
                            offsetY -= swapThreshold 
                        } else if (offsetY < -swapThreshold) {
                            onMoveUp()
                            offsetY += swapThreshold
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