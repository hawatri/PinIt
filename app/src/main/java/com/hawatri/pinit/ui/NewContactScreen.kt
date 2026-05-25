package com.hawatri.pinit.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.data.NoteType
import com.hawatri.pinit.util.NotificationHelper
import com.hawatri.pinit.viewmodel.PinItViewModel
import java.util.UUID

data class ContactNoteData(val name: String, val phone: String)

@SuppressLint("Range")
fun getContactDetails(context: Context, uri: Uri): Pair<String, String> {
    var name = ""
    var phone = ""
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val id = it.getString(it.getColumnIndex(ContactsContract.Contacts._ID))
            name = it.getString(it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)) ?: ""
            val hasPhoneNumber = it.getString(it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)).toInt()
            if (hasPhoneNumber > 0) {
                val phoneCursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", arrayOf(id), null
                )
                phoneCursor?.use { pc ->
                    if (pc.moveToFirst()) phone = pc.getString(pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
                }
            }
        }
    }
    return Pair(name, phone)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewContactScreen(
    noteId: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: PinItViewModel
) {
    val context = LocalContext.current
    val notificationHelper = remember(context) { NotificationHelper(context) }
    val gson = remember { Gson() }

    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
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
                try {
                    val data = gson.fromJson(existing.text, ContactNoteData::class.java)
                    name = data.name
                    phoneNumber = data.phone
                } catch (e: Exception) { name = existing.title }
                isPinned = existing.isPinned
                isLocked = existing.isLocked
                colorHex = existing.colorHex
                labels = existing.labels
                isInitialized = true
            }
        }
    }

    fun save(pinOverride: Boolean = isPinned, archiveOverride: Boolean? = null): String {
        val data = ContactNoteData(name, phoneNumber)
        val existing = notesList.find { it.id == currentNoteId }
        val note = Note(
            id = currentNoteId,
            title = name.ifBlank { "Contact" },
            text = gson.toJson(data),
            formatRanges = emptyList(),
            isPinned = pinOverride,
            isArchived = archiveOverride ?: existing?.isArchived ?: false,
            isList = false,
            noteType = NoteType.CONTACT,
            colorHex = colorHex,
            isLocked = isLocked,
            labels = labels
        )
        if (existing != null) viewModel.updateNote(note) else viewModel.addNote(note)
        return currentNoteId
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            val (fetchedName, fetchedPhone) = getContactDetails(context, uri)
            name = fetchedName
            phoneNumber = fetchedPhone
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) contactPickerLauncher.launch(null)
    }

    fun onPickContactClicked() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            contactPickerLauncher.launch(null)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    // Share
                    IconButton(onClick = {
                        if (name.isNotBlank() || phoneNumber.isNotBlank()) {
                            val shareText = buildString {
                                if (name.isNotBlank()) { append(name); append("\n") }
                                if (phoneNumber.isNotBlank()) append(phoneNumber)
                            }
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share contact"))
                        }
                    }) { Icon(Icons.Filled.Share, "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant) }

                    // Archive
                    IconButton(onClick = {
                        if (name.isNotBlank() || phoneNumber.isNotBlank()) {
                            if (isPinned) notificationHelper.unpinNoteFromNotification(currentNoteId)
                            save(pinOverride = false, archiveOverride = true)
                            onNavigateBack()
                        }
                    }) { Icon(Icons.Filled.Archive, "Archive", tint = MaterialTheme.colorScheme.onSurfaceVariant) }

                    IconButton(onClick = {
                        isPinned = !isPinned
                        val savedId = save(isPinned)
                        if (isPinned) {
                            val text = gson.toJson(ContactNoteData(name, phoneNumber))
                            notificationHelper.pinNoteToNotification(savedId, name.ifBlank { "Contact" }, text, isList = false, noteType = NoteType.CONTACT)
                        } else notificationHelper.unpinNoteFromNotification(savedId)
                    }) {
                        Icon(if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, "Pin",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showLabelsSheet = true }) {
                        Icon(Icons.Filled.Label, "Label",
                            tint = if (labels.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { if (name.isNotBlank() || phoneNumber.isNotBlank()) { isLocked = !isLocked; save() } }) {
                        Icon(
                            if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            if (isLocked) "Locked" else "Unlocked",
                            tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ColorPickerMenuButton(
                        selectedColor = colorHex,
                        onColorSelected = { colorHex = it.ifBlank { null }; if (name.isNotBlank() || phoneNumber.isNotBlank()) save() }
                    )
                    IconButton(onClick = { if (name.isNotBlank() || phoneNumber.isNotBlank()) { save(); onNavigateBack() } }) {
                        Icon(Icons.Filled.Check, "Save", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (name.isEmpty() && phoneNumber.isEmpty()) {
                            Text("*Mandatory field", color = Color(0xFFD32F2F), fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
                        }
                        TextField(
                            value = name, onValueChange = { name = it },
                            placeholder = { Text("Name*", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                            textStyle = TextStyle(fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface),
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        TextField(
                            value = phoneNumber, onValueChange = { phoneNumber = it },
                            placeholder = { Text("Phone Number*", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                            textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                            .clickable { onPickContactClicked() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.ImportContacts, "Pick Contact", tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(24.dp))
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
            onLabelsChange = { labels = it; if (name.isNotBlank() || phoneNumber.isNotBlank()) save() },
            onDismiss = { showLabelsSheet = false }
        )
    }
}
