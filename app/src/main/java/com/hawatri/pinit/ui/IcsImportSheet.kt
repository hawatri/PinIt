package com.hawatri.pinit.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.data.NoteType
import com.hawatri.pinit.util.EXTRA_NOTE_TITLE
import com.hawatri.pinit.util.formatAlarmText
import com.hawatri.pinit.viewmodel.PinItViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class IcsEvent(
    val summary: String,
    val description: String,
    val location: String,
    val startCalendar: Calendar?,
    val endCalendar: Calendar?,
    val startLabel: String
)

fun parseIcsContent(content: String): List<IcsEvent> {
    val events = mutableListOf<IcsEvent>()
    val lines = content
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .lines()
        .fold(mutableListOf<String>()) { acc, line ->
            // Unfold continuation lines (lines starting with whitespace)
            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (acc.isNotEmpty()) acc[acc.lastIndex] = acc.last() + line.trimStart()
                else acc.add(line.trimStart())
            } else { acc.add(line) }
            acc
        }

    var inEvent = false
    var summary = ""; var description = ""; var location = ""
    var dtStart = ""; var dtEnd = ""

    lines.forEach { line ->
        when {
            line == "BEGIN:VEVENT" -> {
                inEvent = true; summary = ""; description = ""; location = ""; dtStart = ""; dtEnd = ""
            }
            line == "END:VEVENT" -> {
                if (inEvent && summary.isNotBlank()) {
                    val start = parseDtValue(dtStart)
                    val end = parseDtValue(dtEnd)
                    events.add(IcsEvent(
                        summary = summary.replace("\\n", "\n").replace("\\,", ","),
                        description = description.replace("\\n", "\n").replace("\\,", ","),
                        location = location,
                        startCalendar = start,
                        endCalendar = end,
                        startLabel = if (start != null) formatAlarmText(start) else "No date"
                    ))
                }
                inEvent = false
            }
            inEvent -> {
                val key = line.substringBefore(':').substringBefore(';')
                val value = line.substringAfter(':')
                when (key) {
                    "SUMMARY" -> summary = value
                    "DESCRIPTION" -> description = value
                    "LOCATION" -> location = value
                    "DTSTART" -> dtStart = value
                    "DTEND" -> dtEnd = value
                }
            }
        }
    }
    return events
}

private fun parseDtValue(value: String): Calendar? {
    if (value.isBlank()) return null
    return try {
        val isUtc = value.endsWith("Z")
        val clean = value.trimEnd('Z')
        val cal = Calendar.getInstance()
        when {
            clean.length == 8 -> { // Date only: YYYYMMDD
                val sdf = SimpleDateFormat("yyyyMMdd", Locale.US)
                cal.time = sdf.parse(clean) ?: return null
                cal.set(Calendar.HOUR_OF_DAY, 9) // Default 9 AM for all-day events
                cal
            }
            clean.length >= 15 -> { // DateTime: YYYYMMDDTHHmmss
                val sdf = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
                if (isUtc) sdf.timeZone = TimeZone.getTimeZone("UTC")
                cal.time = sdf.parse(clean.take(15)) ?: return null
                cal
            }
            else -> null
        }
    } catch (e: Exception) { null }
}

suspend fun readIcsFromUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IcsImportSheet(
    uri: Uri,
    viewModel: PinItViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var events by remember { mutableStateOf<List<IcsEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val selectedIndices = remember { mutableStateListOf<Int>() }

    LaunchedEffect(uri) {
        isLoading = true
        try {
            val content = readIcsFromUri(context, uri)
            events = parseIcsContent(content)
            // Auto-select all by default
            selectedIndices.addAll(events.indices)
        } catch (e: Exception) {
            errorMsg = "Could not read calendar file: ${e.message}"
        }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Calendar Events", style = MaterialTheme.typography.titleMedium)
            }

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMsg != null -> {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                }
                events.isEmpty() -> {
                    Text("No events found in the calendar file.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    Text(
                        "${events.size} event${if (events.size > 1) "s" else ""} found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                        items(events.size) { index ->
                            val event = events[index]
                            val isSelected = index in selectedIndices
                            ListItem(
                                headlineContent = { Text(event.summary, fontWeight = FontWeight.Medium) },
                                supportingContent = {
                                    Column {
                                        Text(event.startLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        if (event.location.isNotBlank()) Text(event.location, fontSize = 12.sp)
                                    }
                                },
                                leadingContent = {
                                    IconButton(onClick = {
                                        if (isSelected) selectedIndices.remove(index) else selectedIndices.add(index)
                                    }) {
                                        Icon(
                                            if (isSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                            null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    selectedIndices.sorted().forEach { index ->
                                        val event = events[index]
                                        val noteText = buildString {
                                            if (event.description.isNotBlank()) { append(event.description); append("\n") }
                                            if (event.location.isNotBlank()) append("📍 ${event.location}")
                                        }.trim()
                                        val note = Note(
                                            title = event.summary,
                                            text = noteText,
                                            formatRanges = emptyList(),
                                            noteType = NoteType.TEXT,
                                            reminderText = event.startLabel.takeIf { it != "No date" }
                                        )
                                        viewModel.addNote(note)
                                        // Schedule alarm if the event is in the future
                                        event.startCalendar?.let { cal ->
                                            if (cal.timeInMillis > System.currentTimeMillis()) {
                                                com.hawatri.pinit.util.scheduleCustomAlarm(
                                                    context = context,
                                                    noteId = note.id,
                                                    noteTitle = note.title,
                                                    dateMillis = cal.timeInMillis,
                                                    hour = cal.get(Calendar.HOUR_OF_DAY),
                                                    minute = cal.get(Calendar.MINUTE)
                                                )
                                            }
                                        }
                                    }
                                    onDismiss()
                                    android.widget.Toast.makeText(
                                        context,
                                        "Imported ${selectedIndices.size} event${if (selectedIndices.size > 1) "s" else ""}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = selectedIndices.isNotEmpty()
                        ) {
                            Icon(Icons.Filled.EventAvailable, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import ${selectedIndices.size}")
                        }
                    }
                }
            }
        }
    }
}
