package com.hawatri.pinit.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.data.NoteType
import com.hawatri.pinit.util.NotificationHelper
import com.hawatri.pinit.viewmodel.PinItViewModel
import java.util.UUID

@SuppressLint("Range")
fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            }
        } finally { cursor?.close() }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) result = result?.substring(cut + 1)
    }
    return result ?: "Image"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewImageScreen(
    noteId: String? = null,
    prefillImageUri: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: PinItViewModel
) {
    val context = LocalContext.current
    val notificationHelper = remember(context) { NotificationHelper(context) }

    var currentStep by remember { mutableIntStateOf(if (prefillImageUri != null) 2 else 0) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(prefillImageUri?.let { Uri.parse(it) }) }
    var imageTitle by remember { mutableStateOf("") }
    var isPinned by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var colorHex by remember { mutableStateOf<String?>(null) }
    var labels by remember { mutableStateOf(listOf<String>()) }
    var showLabelsSheet by remember { mutableStateOf(false) }
    var currentNoteId by remember(noteId) { mutableStateOf(noteId ?: UUID.randomUUID().toString()) }

    val notesList by viewModel.notes.collectAsState()
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(notesList, noteId) {
        if (noteId != null && !isInitialized && notesList.isNotEmpty()) {
            val existing = notesList.find { it.id == noteId }
            if (existing != null) {
                imageTitle = existing.title
                selectedImageUri = existing.text.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                isPinned = existing.isPinned
                isLocked = existing.isLocked
                colorHex = existing.colorHex
                labels = existing.labels
                currentStep = 2
                isInitialized = true
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) currentStep = 1
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            // Take persistent permission so the URI stays valid after app restart
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { /* Some URIs don't support this, ignore */ }
            selectedImageUri = uri
            if (imageTitle.isBlank()) imageTitle = getFileName(context, uri).substringBeforeLast(".")
            currentStep = 2
        }
    }

    fun save(pinOverride: Boolean = isPinned, archiveOverride: Boolean? = null): String {
        val existing = notesList.find { it.id == currentNoteId }
        val note = Note(
            id = currentNoteId,
            title = imageTitle.ifBlank { "Image" },
            text = selectedImageUri?.toString() ?: "",
            formatRanges = emptyList(),
            isPinned = pinOverride,
            isArchived = archiveOverride ?: existing?.isArchived ?: false,
            isList = false,
            noteType = NoteType.IMAGE,
            colorHex = colorHex,
            isLocked = isLocked,
            labels = labels
        )
        if (existing != null) viewModel.updateNote(note) else viewModel.addNote(note)
        return currentNoteId
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                },
                actions = {
                    if (selectedImageUri != null) {
                        // Share image
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/*"
                                putExtra(Intent.EXTRA_STREAM, selectedImageUri)
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            context.startActivity(Intent.createChooser(intent, "Share image"))
                        }) { Icon(Icons.Filled.Share, "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant) }

                        // Archive
                        IconButton(onClick = {
                            if (isPinned) notificationHelper.unpinNoteFromNotification(currentNoteId)
                            save(pinOverride = false, archiveOverride = true)
                            onNavigateBack()
                        }) { Icon(Icons.Filled.Archive, "Archive", tint = MaterialTheme.colorScheme.onSurfaceVariant) }

                        IconButton(onClick = {
                            isPinned = !isPinned
                            val savedId = save(isPinned)
                            if (isPinned) notificationHelper.pinNoteToNotification(savedId, imageTitle.ifBlank { "Image" }, selectedImageUri?.toString() ?: "", isList = false, noteType = NoteType.IMAGE)
                            else notificationHelper.unpinNoteFromNotification(savedId)
                        }) {
                            Icon(if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, "Pin",
                                tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { showLabelsSheet = true }) {
                            Icon(Icons.Filled.Label, "Label",
                                tint = if (labels.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { isLocked = !isLocked; save() }) {
                            Icon(
                                if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                if (isLocked) "Locked" else "Unlocked",
                                tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ColorPickerMenuButton(
                            selectedColor = colorHex,
                            onColorSelected = { colorHex = it.ifBlank { null }; save() }
                        )
                        IconButton(onClick = { save(); onNavigateBack() }) {
                            Icon(Icons.Filled.Check, "Save", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).imePadding(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
            ) {
                when (currentStep) {
                    0 -> Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "To save an image, select or pick one from your device.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                Text("Pick Image")
                            }
                        }
                    }
                    1 -> Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Select an image", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                .clickable { imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Image, "Pick Image", tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(24.dp)) }
                    }
                    2 -> Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = selectedImageUri, contentDescription = "Selected Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("*Mandatory field", color = Color(0xFFD32F2F), fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp))
                            TextField(
                                value = imageTitle, onValueChange = { imageTitle = it },
                                placeholder = { Text("Title*", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                .clickable { imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Image, "Repick", tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(24.dp)) }
                    }
                }
            }
        }
    }

    if (showLabelsSheet) {
        val allLabels = remember(notesList) { notesList.flatMap { it.labels }.distinct() }
        LabelsEditorSheet(
            currentLabels = labels,
            allExistingLabels = allLabels,
            onLabelsChange = { labels = it; if (selectedImageUri != null) save() },
            onDismiss = { showLabelsSheet = false }
        )
    }
}
