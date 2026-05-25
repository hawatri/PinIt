package com.hawatri.pinit.ui

import android.content.Intent
import android.net.Uri
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.data.NoteType
import com.hawatri.pinit.util.NotificationHelper
import com.hawatri.pinit.viewmodel.PinItViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNoteClick: (Note) -> Unit,
    onNavigateToNewNote: () -> Unit,
    onNavigateToNewList: () -> Unit,
    onNavigateToNewLocation: () -> Unit,
    onNavigateToNewQR: () -> Unit,
    onNavigateToNewAppList: () -> Unit,
    onNavigateToNewLink: () -> Unit,
    onNavigateToNewContact: () -> Unit,
    onNavigateToNewImage: () -> Unit,
    onNavigateToNewPDF: () -> Unit = {},
    onNavigateToNewAudio: () -> Unit = {},
    icsShareUri: android.net.Uri? = null,
    onNavigateToArchive: () -> Unit,
    viewModel: PinItViewModel
) {
    var showFabMenu by remember { mutableStateOf(false) }
    var selectedBottomTab by remember { mutableIntStateOf(0) }
    val allNotes by viewModel.notes.collectAsState()
    var selectedNoteIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedNoteIds.isNotEmpty()
    val context = LocalContext.current
    val notificationHelper = remember(context) { NotificationHelper(context) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLabel by remember { mutableStateOf<String?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(SortOrder.NEWEST_FIRST) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var lastDeletedNote by remember { mutableStateOf<Note?>(null) }
    var icsImportUri by remember { mutableStateOf<android.net.Uri?>(icsShareUri) }
    LaunchedEffect(icsShareUri) { if (icsShareUri != null) icsImportUri = icsShareUri }
    val icsPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) icsImportUri = uri
    }

    // Biometric auth for locked notes
    val pendingLockedNote = remember { mutableStateOf<Note?>(null) }
    val biometricPrompt = remember(context) {
        BiometricPrompt(
            context as FragmentActivity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    pendingLockedNote.value?.let { onNoteClick(it) }
                    pendingLockedNote.value = null
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    pendingLockedNote.value = null
                }
            }
        )
    }
    val biometricPromptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Note")
            .setSubtitle("Authenticate to open this note")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
    }

    fun handleNoteClick(note: Note) {
        if (note.isLocked) {
            val canAuth = BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                pendingLockedNote.value = note
                biometricPrompt.authenticate(biometricPromptInfo)
            } else {
                // No biometric enrolled — open directly (degrade gracefully)
                onNoteClick(note)
            }
        } else {
            onNoteClick(note)
        }
    }

    fun archiveWithUndo(note: Note) {
        if (note.isPinned) notificationHelper.unpinNoteFromNotification(note.id)
        viewModel.toggleArchive(note)
        scope.launch {
            val result = snackbarHostState.showSnackbar("Archived", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.notes.value.find { it.id == note.id }?.let { viewModel.toggleArchive(it) }
            }
        }
    }

    fun deleteWithUndo(note: Note) {
        lastDeletedNote = note
        if (note.isPinned) notificationHelper.unpinNoteFromNotification(note.id)
        viewModel.deleteNote(note.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar("Deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) {
                lastDeletedNote?.let { viewModel.addNote(it) }
            }
            lastDeletedNote = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            PinItBottomNavigation(
                selectedItem = selectedBottomTab,
                onItemSelected = { selectedBottomTab = it },
                pinnedCount = allNotes.count { it.isPinned && !it.isArchived }
            )
        },
        floatingActionButton = {
            if (selectedBottomTab == 0 && !isSelectionMode) {
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

                if (isSelectionMode) {
                    TopAppBar(
                        title = { Text(selectedNoteIds.size.toString()) },
                        navigationIcon = {
                            IconButton(onClick = { selectedNoteIds = emptySet() }) { Icon(Icons.Filled.Close, "Clear") }
                        },
                        actions = {
                            // Duplicate selected notes
                            if (selectedNoteIds.size == 1) {
                                IconButton(onClick = {
                                    selectedNoteIds.firstOrNull()?.let { id ->
                                        allNotes.find { it.id == id }?.let { note ->
                                            viewModel.addNote(note.copy(
                                                id = java.util.UUID.randomUUID().toString(),
                                                title = if (note.title.isBlank()) "" else "${note.title} (copy)",
                                                isPinned = false,
                                                timestamp = System.currentTimeMillis()
                                            ))
                                        }
                                    }
                                    selectedNoteIds = emptySet()
                                }) { Icon(Icons.Filled.ContentCopy, "Duplicate") }
                            }

                            IconButton(onClick = {
                                selectedNoteIds.forEach { id -> allNotes.find { it.id == id }?.let { archiveWithUndo(it) } }
                                selectedNoteIds = emptySet()
                            }) { Icon(Icons.Filled.Archive, "Archive") }

                            IconButton(onClick = {
                                selectedNoteIds.forEach { id -> allNotes.find { it.id == id }?.let { deleteWithUndo(it) } }
                                selectedNoteIds = emptySet()
                            }) { Icon(Icons.Filled.Delete, "Delete") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                } else {
                    Box {
                        TopSearchBar(
                            onArchiveClick = onNavigateToArchive,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onSortClick = { showSortMenu = true }
                        )
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            SortOrder.entries.forEach { order ->
                                val isSelected = sortOrder == order
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    onClick = { sortOrder = order; showSortMenu = false },
                                    trailingIcon = if (isSelected) ({ Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }) else null
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val displayNotes = when (selectedBottomTab) {
                    1 -> allNotes.filter { it.isPinned && !it.isArchived }
                    2 -> if (selectedLabel != null)
                            allNotes.filter { !it.isArchived && selectedLabel in it.labels }
                         else emptyList()
                    else -> allNotes.filter { !it.isArchived }
                }.filter { note ->
                    if (searchQuery.isBlank()) true
                    else note.title.contains(searchQuery, ignoreCase = true) ||
                         note.text.contains(searchQuery, ignoreCase = true)
                }.let { list ->
                    when (sortOrder) {
                        SortOrder.NEWEST_FIRST -> list.sortedByDescending { it.timestamp }
                        SortOrder.OLDEST_FIRST -> list.sortedBy { it.timestamp }
                        SortOrder.TITLE_AZ -> list.sortedBy { it.title.lowercase() }
                        SortOrder.TITLE_ZA -> list.sortedByDescending { it.title.lowercase() }
                    }
                }

                when {
                    // Labels tab — no label selected: show label browser
                    selectedBottomTab == 2 && selectedLabel == null -> {
                        val allLabels = remember(allNotes) {
                            allNotes.filter { !it.isArchived }
                                .flatMap { it.labels }
                                .groupingBy { it }
                                .eachCount()
                                .entries
                                .sortedByDescending { it.value }
                        }
                        LabelBrowser(
                            labelCounts = allLabels,
                            onLabelClick = { selectedLabel = it },
                            onRename = { old, nu -> viewModel.renameLabel(old, nu) },
                            onDelete = { name -> viewModel.deleteLabel(name) }
                        )
                    }
                    // Labels tab — label selected: show filtered notes
                    selectedBottomTab == 2 && selectedLabel != null -> {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedLabel = null }) { Icon(Icons.Filled.ArrowBack, "Back") }
                            Text(selectedLabel!!, style = MaterialTheme.typography.titleMedium)
                        }
                        if (displayNotes.isNotEmpty()) {
                            NotesGrid(notes = displayNotes, selectedNoteIds = selectedNoteIds, isSelectionMode = isSelectionMode,
                                onNoteClick = { id -> if (isSelectionMode) selectedNoteIds = if (id in selectedNoteIds) selectedNoteIds - id else selectedNoteIds + id else allNotes.find { it.id == id }?.let { handleNoteClick(it) } },
                                onNoteLongClick = { id -> selectedNoteIds = selectedNoteIds + id },
                                onPinClick = { note -> viewModel.togglePin(note); if (!note.isPinned) notificationHelper.pinNoteToNotification(note.id, note.title, note.text, note.isList, note.noteType) else notificationHelper.unpinNoteFromNotification(note.id) },
                                onCopyClick = { text -> val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; cb.setPrimaryClip(android.content.ClipData.newPlainText("", text)); android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show() },
                                onToggleAllClick = { note -> val g = Gson(); val items = try { g.fromJson(note.text, Array<ChecklistItemData>::class.java).toList() } catch (e: Exception) { emptyList() }; val all = items.isNotEmpty() && items.all { it.isChecked }; val n = note.copy(text = g.toJson(items.map { it.copy(isChecked = !all) })); viewModel.updateNote(n); if (n.isPinned) notificationHelper.pinNoteToNotification(n.id, n.title, n.text, true) }
                            )
                        } else {
                            EmptyStateView(icon = Icons.Filled.Label, message = "No notes with label \"${selectedLabel}\"")
                        }
                    }
                    // Home / Pinned tabs
                    displayNotes.isNotEmpty() -> {
                        NotesGrid(
                            notes = displayNotes,
                            selectedNoteIds = selectedNoteIds,
                            isSelectionMode = isSelectionMode,
                            onNoteClick = { id ->
                                if (isSelectionMode) {
                                    selectedNoteIds = if (id in selectedNoteIds) selectedNoteIds - id else selectedNoteIds + id
                                } else {
                                    allNotes.find { it.id == id }?.let { handleNoteClick(it) }
                                }
                            },
                            onNoteLongClick = { id -> selectedNoteIds = selectedNoteIds + id },
                            onPinClick = { note ->
                                val willBePinned = !note.isPinned
                                viewModel.togglePin(note)
                                if (willBePinned) notificationHelper.pinNoteToNotification(note.id, note.title, note.text, note.isList, note.noteType)
                                else notificationHelper.unpinNoteFromNotification(note.id)
                            },
                            onCopyClick = { text ->
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Copied Note", text))
                                android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onToggleAllClick = { note ->
                                val gson = Gson()
                                val items = try { gson.fromJson(note.text, Array<ChecklistItemData>::class.java).toList() } catch (e: Exception) { emptyList() }
                                val allChecked = items.isNotEmpty() && items.all { it.isChecked }
                                val newNote = note.copy(text = gson.toJson(items.map { it.copy(isChecked = !allChecked) }))
                                viewModel.updateNote(newNote)
                                if (newNote.isPinned) notificationHelper.pinNoteToNotification(newNote.id, newNote.title, newNote.text, true)
                            },
                            onArchiveNote = { archiveWithUndo(it) }
                        )
                    }
                    else -> {
                        val icon = if (selectedBottomTab == 1) Icons.Filled.PushPin else Icons.Filled.Article
                        val msg = if (selectedBottomTab == 1) "No pinned items" else "No items"
                        EmptyStateView(icon = icon, message = msg)
                    }
                }
            }

            // ICS import sheet
            icsImportUri?.let { uri ->
                IcsImportSheet(
                    uri = uri,
                    viewModel = viewModel,
                    onDismiss = { icsImportUri = null }
                )
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
                    onNewPDFClick = onNavigateToNewPDF,
                    onNewAudioClick = onNavigateToNewAudio,
                    onImportIcsClick = { icsPickerLauncher.launch(arrayOf("text/calendar", "application/ics", "*/*")) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 120.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesGrid(
    notes: List<Note>,
    selectedNoteIds: Set<String>,
    isSelectionMode: Boolean,
    onNoteClick: (String) -> Unit,
    onNoteLongClick: (String) -> Unit,
    onPinClick: (Note) -> Unit,
    onCopyClick: (String) -> Unit,
    onToggleAllClick: (Note) -> Unit,
    onArchiveNote: (Note) -> Unit = {}
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp
    ) {
        items(notes, key = { it.id }) { note ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart && !isSelectionMode) {
                        onArchiveNote(note)
                        true
                    } else false
                },
                positionalThreshold = { totalDistance -> totalDistance * 0.45f }
            )

            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false,
                enableDismissFromEndToStart = !isSelectionMode,
                backgroundContent = {
                    val color by animateColorAsState(
                        targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                            MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        label = "swipe_bg"
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(color),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            Icons.Filled.Archive, "Archive",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            ) {
                NoteCard(
                    note = note,
                    isSelected = selectedNoteIds.contains(note.id),
                    onClick = { onNoteClick(note.id) },
                    onLongClick = { onNoteLongClick(note.id) },
                    onPinClick = { onPinClick(note) },
                    onCopyClick = onCopyClick,
                    onToggleAllClick = onToggleAllClick
                )
            }
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
    onPinClick: () -> Unit,
    onCopyClick: (String) -> Unit = {},
    onToggleAllClick: (Note) -> Unit = {}
) {
    val context = LocalContext.current
    val borderStroke = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
    else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val gson = remember { Gson() }

    val isDark = isSystemInDarkTheme()
    val cardColor = if (note.colorHex.isNullOrBlank()) MaterialTheme.colorScheme.surface
                    else Color(android.graphics.Color.parseColor(note.colorHex)).copy(alpha = if (isDark) 0.35f else 0.85f)

    // Parse type-specific data outside composable calls (try-catch can't wrap composables)
    val listItems: List<ChecklistItemData> = remember(note.text, note.noteType, note.isList) {
        if (note.noteType == NoteType.LIST || note.isList) {
            try { gson.fromJson(note.text, Array<ChecklistItemData>::class.java).toList() } catch (e: Exception) { emptyList() }
        } else emptyList()
    }
    val linkData: LinkNoteData? = remember(note.text, note.noteType) {
        if (note.noteType == NoteType.LINK) try { gson.fromJson(note.text, LinkNoteData::class.java) } catch (e: Exception) { null } else null
    }
    val contactData: ContactNoteData? = remember(note.text, note.noteType) {
        if (note.noteType == NoteType.CONTACT) try { gson.fromJson(note.text, ContactNoteData::class.java) } catch (e: Exception) { null } else null
    }
    val locationData: LocationNoteData? = remember(note.text, note.noteType) {
        if (note.noteType == NoteType.LOCATION) try { gson.fromJson(note.text, LocationNoteData::class.java) } catch (e: Exception) { null } else null
    }
    val audioData: AudioNoteData? = remember(note.text, note.noteType) {
        if (note.noteType == NoteType.AUDIO) try { Gson().fromJson(note.text, AudioNoteData::class.java) } catch (e: Exception) { null } else null
    }
    val appItems: List<AppNoteItem> = remember(note.text, note.noteType) {
        if (note.noteType == NoteType.APPLIST) try { gson.fromJson(note.text, Array<AppNoteItem>::class.java).toList() } catch (e: Exception) { emptyList() } else emptyList()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = borderStroke
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title + Pin row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (note.isLocked) {
                        Icon(Icons.Filled.Lock, "Locked", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    if (note.title.isNotBlank()) {
                        Text(
                            text = note.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                IconButton(
                    onClick = onPinClick,
                    modifier = Modifier.size(28.dp).offset(x = 8.dp, y = (-8).dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "Toggle Pin",
                        tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Type-specific content (no composables inside try-catch)
            when (note.noteType) {
                NoteType.LIST -> {
                    listItems.take(4).forEach { ChecklistItemPreview(it) }
                    if (listItems.size > 4) {
                        Text("+ ${listItems.size - 4} more items", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp, start = 24.dp))
                    }
                }
                NoteType.IMAGE -> {
                    if (note.text.isNotBlank()) {
                        AsyncImage(model = note.text, contentDescription = note.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)))
                    }
                }
                NoteType.LINK -> {
                    if (linkData != null) {
                        if (linkData.imageUrl.isNotBlank()) {
                            Box(modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp))) {
                                AsyncImage(model = linkData.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                if (linkData.isVideo) {
                                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
                                    Box(
                                        modifier = Modifier.align(Alignment.Center)
                                            .size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (linkData.description.isNotBlank()) {
                            Text(linkData.description, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                        }
                        Text(linkData.url, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                        if (linkData.url.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    try {
                                        val urlToOpen = if (!linkData.url.startsWith("http", true)) "https://${linkData.url}" else linkData.url
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                        context.startActivity(intent)
                                    } catch (e: Exception) { }
                                },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Browse", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                Icon(Icons.Outlined.OpenInNew, "Browse", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    } else if (note.text.isNotBlank()) {
                        Text(note.text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                NoteType.CONTACT -> {
                    if (contactData != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Phone, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(contactData.phone, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (note.text.isNotBlank()) {
                        Text(note.text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                NoteType.LOCATION -> {
                    if (locationData != null) {
                        Text(
                            text = locationData.address.ifBlank { "${locationData.lat}, ${locationData.lng}" },
                            fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp
                        )
                        if (locationData.lat != null && locationData.lng != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    val uri = Uri.parse("geo:${locationData.lat},${locationData.lng}?q=${locationData.lat},${locationData.lng}(${Uri.encode(note.title.ifBlank { "Location" })})")
                                    val intent = Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                    try { context.startActivity(intent) } catch (e: Exception) { }
                                },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Navigate", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                Icon(Icons.Filled.Navigation, "Navigate", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    } else if (note.text.isNotBlank()) {
                        Text(note.text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                NoteType.QR -> {
                    val qrBitmap = remember(note.text) {
                        if (note.text.isNotBlank()) com.hawatri.pinit.util.QrUtils.generateQrBitmap(note.text, 256) else null
                    }
                    if (qrBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.fillMaxSize().padding(6.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        note.text,
                        fontSize = 13.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val canOpen = note.text.contains("://")
                    if (canOpen) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(note.text)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                    context.startActivity(intent)
                                } catch (e: Exception) { }
                            },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Open", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Icon(Icons.Outlined.OpenInNew, "Open", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                NoteType.PDF -> {
                    val pdfBitmap = remember(note.text) {
                        if (note.text.isNotBlank()) {
                            try { com.hawatri.pinit.util.PdfUtils.renderFirstPage(context, Uri.parse(note.text), 512, 512) }
                            catch (e: Exception) { null }
                        } else null
                    }
                    if (pdfBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth().height(140.dp)
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = pdfBitmap.asImageBitmap(),
                                contentDescription = "PDF preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = note.title.ifBlank { "PDF Document" },
                            fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                NoteType.AUDIO -> {
                    val durMs = audioData?.durationMs ?: 0L
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Mic, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            if (durMs > 0) "%d:%02d".format(durMs / 60000, (durMs / 1000) % 60) else "Recording",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                NoteType.APPLIST -> {
                    AppListPreview(items = appItems)
                }
                else -> {
                    if (note.isList) {
                        listItems.take(4).forEach { ChecklistItemPreview(it) }
                        if (listItems.size > 4) {
                            Text("+ ${listItems.size - 4} more items", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp, start = 24.dp))
                        }
                    } else if (note.text.isNotBlank()) {
                        Text(text = buildFormattedString(note.text, note.formatRanges), fontSize = 14.sp, maxLines = 8, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Label chips
            if (note.labels.isNotEmpty()) {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    note.labels.forEach { label ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            if (note.reminderText != null) {
                Row(
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Notifications, "Alarm", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(note.reminderText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

            // Bottom action row
            val isListType = note.noteType == NoteType.LIST || note.isList
            val isLinkType = note.noteType == NoteType.LINK
            val isContactType = note.noteType == NoteType.CONTACT
            val isPdfType = note.noteType == NoteType.PDF

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        when {
                            isListType -> onToggleAllClick(note)
                            isLinkType -> {
                                try {
                                    val data = gson.fromJson(note.text, LinkNoteData::class.java)
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(data.url))
                                    context.startActivity(browserIntent)
                                } catch (e: Exception) { onCopyClick(note.text) }
                            }
                            isContactType -> {
                                try {
                                    val data = gson.fromJson(note.text, ContactNoteData::class.java)
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${data.phone}")))
                                } catch (e: Exception) { onCopyClick(note.text) }
                            }
                            isPdfType -> {
                                try {
                                    val pdfIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(note.text), "application/pdf")
                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    context.startActivity(pdfIntent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "No PDF viewer found", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            else -> onCopyClick(note.text)
                        }
                    }
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    isListType -> {
                        val allChecked = listItems.isNotEmpty() && listItems.all { it.isChecked }
                        Text(text = if (allChecked) "Uncheck all" else "Check All", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(imageVector = if (allChecked) Icons.Outlined.Close else Icons.Outlined.Checklist, contentDescription = "Toggle All", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    isLinkType -> {
                        Text("Open Link", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Outlined.OpenInNew, "Open", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    isContactType -> {
                        Text("Call", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Outlined.Phone, "Call", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    isPdfType -> {
                        Text("Open PDF", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Filled.PictureAsPdf, "Open", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                    else -> {
                        Text("Copy", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Outlined.ContentCopy, "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AppListPreview(items: List<AppNoteItem>) {
    val context = LocalContext.current
    val pm = context.packageManager
    val maxIcons = 4
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.take(maxIcons).forEach { item ->
            val icon = remember(item.packageName) {
                try { pm.getApplicationIcon(item.packageName) } catch (e: Exception) { null }
            }
            if (icon != null) {
                coil.compose.AsyncImage(
                    model = icon,
                    contentDescription = item.appName,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Apps, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (items.size > maxIcons) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("+${items.size - maxIcons}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ChecklistItemPreview(item: ChecklistItemData) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp).fillMaxWidth()
    ) {
        Icon(
            imageVector = if (item.isChecked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = item.text,
            fontSize = 14.sp,
            color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f)
        )
    }
}

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelBrowser(
    labelCounts: List<Map.Entry<String, Int>>,
    onLabelClick: (String) -> Unit,
    onRename: (String, String) -> Unit = { _, _ -> },
    onDelete: (String) -> Unit = {}
) {
    if (labelCounts.isEmpty()) {
        EmptyStateView(icon = Icons.Filled.Label, message = "No labels yet\nAdd labels from note or list editor")
        return
    }

    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        labelCounts.forEach { (label, count) ->
            var menuExpanded by remember(label) { mutableStateOf(false) }
            Card(
                modifier = Modifier.width(170.dp).clickable { onLabelClick(label) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Label, null,
                                modifier = Modifier.padding(6.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.MoreVert, "More", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                    onClick = {
                                        menuExpanded = false
                                        renameTarget = label
                                        renameText = label
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                    onClick = {
                                        menuExpanded = false
                                        deleteTarget = label
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("$count", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename label") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val old = renameTarget!!
                    val nu = renameText.trim()
                    if (nu.isNotBlank() && nu != old) onRename(old, nu)
                    renameTarget = null
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete label?") },
            text = { Text("\"${deleteTarget}\" will be removed from all notes. The notes themselves stay.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(deleteTarget!!)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

enum class SortOrder(val label: String) {
    NEWEST_FIRST("Newest first"),
    OLDEST_FIRST("Oldest first"),
    TITLE_AZ("Title A → Z"),
    TITLE_ZA("Title Z → A")
}

@Composable
fun PinItBottomNavigation(selectedItem: Int, onItemSelected: (Int) -> Unit, pinnedCount: Int = 0) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        listOf("Home", "Pinned", "Labels").forEachIndexed { index, item ->
            NavigationBarItem(
                icon = {
                    val iv = when(index){ 0->Icons.Filled.Home; 1->Icons.Filled.PushPin; else->Icons.Filled.Label }
                    if (index == 1 && pinnedCount > 0) {
                        BadgedBox(badge = { Badge { Text(pinnedCount.toString()) } }) {
                            Icon(iv, item)
                        }
                    } else {
                        Icon(iv, item)
                    }
                },
                label = { Text(item) }, selected = selectedItem == index, onClick = { onItemSelected(index) }
            )
        }
    }
}
