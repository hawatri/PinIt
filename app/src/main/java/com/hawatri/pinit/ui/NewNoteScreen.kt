package com.hawatri.pinit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel // New Import
import com.hawatri.pinit.data.Note // New Import
import com.hawatri.pinit.viewmodel.PinItViewModel // New Import

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewNoteScreen(
    onNavigateBack: () -> Unit,
    viewModel: PinItViewModel = viewModel() // Injecting the ViewModel
) {
    var title by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf(TextFieldValue("")) }
    var isBodyFocused by remember { mutableStateOf(false) }

    var formatRanges by remember { mutableStateOf(listOf<FormatRange>()) }
    var activeFormats by remember { mutableStateOf(setOf<FormatType>()) }

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
                    IconButton(onClick = { }) { Icon(Icons.Filled.PushPin, contentDescription = "Pin", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { }) { Icon(Icons.Filled.Notifications, contentDescription = "Reminder", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { }) { Icon(Icons.Filled.Label, contentDescription = "Label", tint = MaterialTheme.colorScheme.onSurfaceVariant) }

                    // SAVE BUTTON UPDATED
                    IconButton(
                        onClick = {
                            // Only save if there is actually some text or a title
                            if (title.isNotBlank() || noteText.text.isNotBlank()) {
                                val newNote = Note(
                                    title = title,
                                    text = noteText.text,
                                    formatRanges = formatRanges
                                )
                                viewModel.addNote(newNote)
                            }
                            onNavigateBack()
                        }
                    ) {
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
            }
        }
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