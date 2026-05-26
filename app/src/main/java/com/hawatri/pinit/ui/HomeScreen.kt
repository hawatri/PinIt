package com.hawatri.pinit.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
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
import androidx.compose.ui.draw.blur
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
import androidx.compose.animation.togetherWith
import com.hawatri.pinit.util.NotificationHelper
import com.hawatri.pinit.viewmodel.PinItViewModel
import com.hawatri.pinit.data.AppPreferences
import com.hawatri.pinit.widget.AddWidgets
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyStaggeredGridState

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.animation.ExperimentalAnimationApi::class)
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
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSignIn: () -> Unit = {},
    viewModel: PinItViewModel
) {
    var showFabMenu by remember { mutableStateOf(false) }
    var selectedBottomTab by remember { mutableIntStateOf(0) }
    val allNotes by viewModel.notes.collectAsState()
    var selectedNoteIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedNoteIds.isNotEmpty()
    var showBulkLabelsSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notificationHelper = remember(context) { NotificationHelper(context) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLabel by remember { mutableStateOf<String?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    // Persisted manual order — list of note ids the user dragged into shape. Empty
    // means "never reordered" so we fall back to NEWEST_FIRST.
    var manualOrder by remember { mutableStateOf<List<String>>(AppPreferences.getManualOrder(context)) }
    var sortOrder by remember {
        mutableStateOf(if (manualOrder.isNotEmpty()) SortOrder.MANUAL else SortOrder.NEWEST_FIRST)
    }
    // Reorder mode is on while the user is dragging cards around. Tap ✓ to commit
    // the new order, ✗ to discard. Disables every other gesture (clicks, long-press,
    // selection mode) so the only thing the user can do is drag.
    var reorderMode by remember { mutableStateOf(false) }
    var draftOrder by remember { mutableStateOf<List<Note>>(emptyList()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var lastDeletedNote by remember { mutableStateOf<Note?>(null) }
    var icsImportUri by remember { mutableStateOf<android.net.Uri?>(icsShareUri) }
    LaunchedEffect(icsShareUri) { if (icsShareUri != null) icsImportUri = icsShareUri }
    val icsPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) icsImportUri = uri
    }
    var lastBackPressMs by remember { mutableLongStateOf(0L) }

    // Back navigation:
    //   - Selection mode → clear selection
    //   - FAB menu open → close it
    //   - Bulk labels sheet → close it
    //   - Labels tab with a drilled-in label → pop the label
    //   - Pinned (1) or Labels (2) tab → switch to Home
    //   - Home tab → double-tap within 2s to exit, otherwise toast
    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            isSelectionMode -> selectedNoteIds = emptySet()
            showFabMenu -> showFabMenu = false
            showBulkLabelsSheet -> showBulkLabelsSheet = false
            selectedBottomTab == 2 && selectedLabel != null -> selectedLabel = null
            selectedBottomTab != 0 -> selectedBottomTab = 0
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressMs < 2000) {
                    (context as? android.app.Activity)?.finish()
                } else {
                    lastBackPressMs = now
                    android.widget.Toast.makeText(context, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    // Notification permission gate for pin actions (Android 13+)
    val pendingPinAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
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
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingPinAction.value = action
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
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

    // Fallback for devices with no biometric enrolled — opens the system PIN /
    // pattern / password sheet via KeyguardManager. Resolves the pending locked
    // note on Activity.RESULT_OK; any other result clears the pending state.
    val credentialLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingLockedNote.value?.let { onNoteClick(it) }
        }
        pendingLockedNote.value = null
    }

    fun handleNoteClick(note: Note) {
        if (note.isLocked) {
            val biometricManager = BiometricManager.from(context)
            val canBiometric = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            when (canBiometric) {
                BiometricManager.BIOMETRIC_SUCCESS -> {
                    pendingLockedNote.value = note
                    biometricPrompt.authenticate(biometricPromptInfo)
                }
                else -> {
                    // No biometric / device credential combo accepted by BiometricPrompt.
                    // Fall through to KeyguardManager: PIN, pattern, or password sheet.
                    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    if (keyguard?.isDeviceSecure == true) {
                        @Suppress("DEPRECATION")
                        val intent = keyguard.createConfirmDeviceCredentialIntent(
                            "Unlock Note",
                            "Enter your PIN, pattern, or password to open this note"
                        )
                        if (intent != null) {
                            pendingLockedNote.value = note
                            credentialLauncher.launch(intent)
                        } else {
                            // Couldn't build the credential intent — open directly.
                            // The home-screen blur stays because note.isLocked is unchanged.
                            onNoteClick(note)
                        }
                    } else {
                        // Device has no screen lock (or swipe-only). Nothing to prompt
                        // with, so open the note. The blur on the card stays in place.
                        onNoteClick(note)
                    }
                }
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

    /** Bulk archive with one Undo snackbar that un-archives every snapshotted note. */
    fun archiveSelectedWithUndo(idsToArchive: Set<String>) {
        if (idsToArchive.isEmpty()) return
        val snapshot = viewModel.notes.value.filter { it.id in idsToArchive }
        snapshot.forEach { viewModel.toggleArchive(it) }
        val label = if (snapshot.size == 1) "Archived" else "Archived ${snapshot.size}"
        scope.launch {
            val result = snackbarHostState.showSnackbar(label, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) {
                snapshot.forEach { original ->
                    viewModel.notes.value.find { it.id == original.id }?.let { viewModel.toggleArchive(it) }
                }
            }
        }
    }

    /** Bulk delete with one Undo snackbar that re-inserts every snapshotted note. */
    fun deleteSelectedWithUndo(idsToDelete: Set<String>) {
        if (idsToDelete.isEmpty()) return
        val snapshot = viewModel.notes.value.filter { it.id in idsToDelete }
        snapshot.forEach { note ->
            if (note.isPinned) notificationHelper.unpinNoteFromNotification(note.id)
            viewModel.deleteNote(note.id)
        }
        val label = if (snapshot.size == 1) "Deleted" else "Deleted ${snapshot.size}"
        scope.launch {
            val result = snackbarHostState.showSnackbar(label, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) {
                snapshot.forEach { viewModel.addNote(it) }
            }
        }
    }

    fun deleteLabelWithUndo(name: String) {
        // Snapshot the *exact* state of every affected note before the delete so undo
        // can restore the label list verbatim — important if a note had multiple
        // labels and only one was being removed.
        val affected = viewModel.notes.value.filter { name in it.labels }
        if (affected.isEmpty()) return
        viewModel.deleteLabel(name)
        scope.launch {
            val msg = if (affected.size == 1) "Label \"$name\" deleted"
                      else "Label \"$name\" removed from ${affected.size} notes"
            val result = snackbarHostState.showSnackbar(msg, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) {
                affected.forEach { viewModel.updateNote(it) }
            }
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

                if (reorderMode) {
                    // Drag-to-reorder top bar — ✗ discards the draft, ✓ commits and exits.
                    TopAppBar(
                        title = { Text("Drag to reorder") },
                        navigationIcon = {
                            IconButton(onClick = {
                                reorderMode = false
                                draftOrder = emptyList()
                                if (manualOrder.isEmpty()) sortOrder = SortOrder.NEWEST_FIRST
                            }) { Icon(Icons.Filled.Close, "Discard") }
                        },
                        actions = {
                            IconButton(onClick = {
                                val ids = draftOrder.map { it.id }
                                manualOrder = ids
                                AppPreferences.setManualOrder(context, ids)
                                reorderMode = false
                                draftOrder = emptyList()
                            }) { Icon(Icons.Filled.Check, "Save order") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                } else if (isSelectionMode) {
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

                            // Apply labels to all selected notes
                            IconButton(onClick = { showBulkLabelsSheet = true }) {
                                Icon(Icons.Filled.Label, "Add label")
                            }

                            IconButton(onClick = {
                                val ids = selectedNoteIds
                                selectedNoteIds = emptySet()
                                archiveSelectedWithUndo(ids)
                            }) { Icon(Icons.Filled.Archive, "Archive") }

                            IconButton(onClick = {
                                val ids = selectedNoteIds
                                selectedNoteIds = emptySet()
                                deleteSelectedWithUndo(ids)
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
                            onSortClick = { showSortMenu = true },
                            onSettingsClick = onNavigateToSettings,
                            onSignInClick = onNavigateToSignIn
                        )
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            SortOrder.entries.forEach { order ->
                                val isSelected = sortOrder == order
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    onClick = {
                                        showSortMenu = false
                                        if (order == SortOrder.MANUAL) {
                                            // Enter reorder mode — the snapshot is taken below
                                            // via LaunchedEffect(reorderMode) so we can use the
                                            // already-computed displayNotes.
                                            reorderMode = true
                                            sortOrder = SortOrder.MANUAL
                                        } else {
                                            sortOrder = order
                                        }
                                    },
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
                        SortOrder.MANUAL -> {
                            val rank = manualOrder.withIndex().associate { it.value to it.index }
                            list.sortedWith(
                                compareBy(
                                    { rank[it.id] ?: Int.MAX_VALUE },
                                    { -it.timestamp }
                                )
                            )
                        }
                    }
                }

                // Snapshot the current visible list as the reorder draft when the user
                // enters reorder mode. Subsequent drags mutate draftOrder; ✓ commits.
                LaunchedEffect(reorderMode) {
                    if (reorderMode) draftOrder = displayNotes
                }

                androidx.compose.animation.AnimatedContent(
                    targetState = Triple(selectedBottomTab, selectedLabel, displayNotes.isEmpty()),
                    transitionSpec = {
                        (androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(220)) +
                            androidx.compose.animation.slideInHorizontally(
                                animationSpec = androidx.compose.animation.core.tween(220),
                                initialOffsetX = { fullWidth -> if (targetState.first > initialState.first) fullWidth / 6 else -fullWidth / 6 }
                            )).togetherWith(
                                androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(160))
                            )
                    },
                    label = "tab_content"
                ) { _ ->
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
                            onDelete = { name -> deleteLabelWithUndo(name) }
                        )
                    }
                    // Labels tab — label selected: show filtered notes
                    selectedBottomTab == 2 && selectedLabel != null -> {
                        Column {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedLabel = null }) { Icon(Icons.Filled.ArrowBack, "Back") }
                            Text(selectedLabel!!, style = MaterialTheme.typography.titleMedium)
                        }
                        if (displayNotes.isNotEmpty()) {
                            NotesGrid(notes = displayNotes, selectedNoteIds = selectedNoteIds, isSelectionMode = isSelectionMode,
                                onNoteClick = { id -> if (isSelectionMode) selectedNoteIds = if (id in selectedNoteIds) selectedNoteIds - id else selectedNoteIds + id else allNotes.find { it.id == id }?.let { handleNoteClick(it) } },
                                onNoteLongClick = { id -> selectedNoteIds = selectedNoteIds + id },
                                onPinClick = { note -> runWithNotifPermission { viewModel.togglePin(note); if (!note.isPinned) notificationHelper.pinNoteToNotification(note.id, note.title, note.text, note.isList, note.noteType) else notificationHelper.unpinNoteFromNotification(note.id) } },
                                onCopyClick = { text -> val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager; cb.setPrimaryClip(android.content.ClipData.newPlainText("", text)); android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show() },
                                onToggleAllClick = { note -> val g = Gson(); val items = try { g.fromJson(note.text, Array<ChecklistItemData>::class.java).toList() } catch (e: Exception) { emptyList() }; val all = items.isNotEmpty() && items.all { it.isChecked }; val n = note.copy(text = g.toJson(items.map { it.copy(isChecked = !all) })); viewModel.updateNote(n); if (n.isPinned) notificationHelper.pinNoteToNotification(n.id, n.title, n.text, true) }
                            )
                        } else {
                            EmptyStateView(icon = Icons.Filled.Label, message = "No notes with label \"${selectedLabel}\"")
                        }
                        }
                    }
                    // Home / Pinned tabs
                    displayNotes.isNotEmpty() -> {
                        // In reorder mode the user is editing draftOrder; outside it
                        // we just show the sorted displayNotes. draftOrder is committed
                        // to manualOrder when the user taps ✓ in the reorder top bar.
                        val gridSource = if (reorderMode) draftOrder else displayNotes
                        NotesGrid(
                            notes = gridSource,
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
                                runWithNotifPermission {
                                    val willBePinned = !note.isPinned
                                    viewModel.togglePin(note)
                                    if (willBePinned) notificationHelper.pinNoteToNotification(note.id, note.title, note.text, note.isList, note.noteType)
                                    else notificationHelper.unpinNoteFromNotification(note.id)
                                }
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
                            reorderMode = reorderMode,
                            onMove = { from, to ->
                                draftOrder = draftOrder.toMutableList().apply {
                                    add(to, removeAt(from))
                                }
                            }
                        )
                    }
                    else -> {
                        val icon = if (selectedBottomTab == 1) Icons.Filled.PushPin else Icons.Filled.Article
                        val msg = if (selectedBottomTab == 1) "No pinned items" else "No items"
                        EmptyStateView(icon = icon, message = msg)
                    }
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

            // Bulk labels editor — applies labels to all selected notes
            if (showBulkLabelsSheet) {
                val selectedNotes = remember(selectedNoteIds, allNotes) {
                    allNotes.filter { it.id in selectedNoteIds }
                }
                // Show labels common to every selected note (intersection) so the user can
                // see which labels they all share, and add/remove from there.
                val commonLabels = remember(selectedNotes) {
                    if (selectedNotes.isEmpty()) emptyList()
                    else selectedNotes.map { it.labels.toSet() }.reduce { acc, set -> acc intersect set }.toList()
                }
                val allKnownLabels = remember(allNotes) { allNotes.flatMap { it.labels }.distinct() }
                LabelsEditorSheet(
                    currentLabels = commonLabels,
                    allExistingLabels = allKnownLabels,
                    onLabelsChange = { newLabels ->
                        // Add the labels the user selected to every selected note
                        // (preserve any per-note labels that aren't in the common set)
                        selectedNotes.forEach { note ->
                            val merged = (note.labels + newLabels).distinct()
                            // Also remove any labels the user unticked from the common set
                            val removed = commonLabels - newLabels.toSet()
                            val final = merged.filterNot { it in removed }
                            viewModel.updateNote(note.copy(labels = final))
                        }
                        showBulkLabelsSheet = false
                        selectedNoteIds = emptySet()
                    },
                    onDismiss = { showBulkLabelsSheet = false }
                )
            }

            if (selectedBottomTab == 0 && !isSelectionMode) {
                // Backdrop dim — fades in when FAB menu opens
                androidx.compose.animation.AnimatedVisibility(
                    visible = showFabMenu,
                    enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(150)),
                    exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(150))
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).clickable { showFabMenu = false })
                }
                FabMenu(
                    visible = showFabMenu,
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
    reorderMode: Boolean = false,
    onMove: (from: Int, to: Int) -> Unit = { _, _ -> }
) {
    val gridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
    val reorderState = rememberReorderableLazyStaggeredGridState(gridState) { from, to ->
        onMove(from.index, to.index)
    }

    LazyVerticalStaggeredGrid(
        state = gridState,
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp
    ) {
        items(notes, key = { it.id }) { note ->
            ReorderableItem(reorderState, key = note.id) { isDragging ->
                val tint by animateColorAsState(
                    targetValue = if (isDragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                  else androidx.compose.ui.graphics.Color.Transparent,
                    label = "drag_tint"
                )
                Box(modifier = Modifier.background(tint)) {
                    NoteCard(
                        note = note,
                        isSelected = selectedNoteIds.contains(note.id),
                        onClick = { if (!reorderMode) onNoteClick(note.id) },
                        onLongClick = { if (!reorderMode) onNoteLongClick(note.id) },
                        onPinClick = { if (!reorderMode) onPinClick(note) },
                        onCopyClick = if (reorderMode) ({}) else onCopyClick,
                        onToggleAllClick = if (reorderMode) ({}) else onToggleAllClick
                    )
                    // In reorder mode, an invisible overlay sits on top of the card
                    // and owns the drag handle. Without this, NoteCard's own
                    // combinedClickable/long-press fires first and pops the card menu
                    // instead of starting the drag.
                    if (reorderMode) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .longPressDraggableHandle()
                        )
                    }
                }
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

    var showCardMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (isSelected) onLongClick() else showCardMenu = true
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = borderStroke
    ) {
        Box {
            DropdownMenu(expanded = showCardMenu, onDismissRequest = { showCardMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Add to Home Screen") },
                    leadingIcon = { Icon(Icons.Filled.AddToHomeScreen, null) },
                    onClick = {
                        showCardMenu = false
                        if (note.isLocked) {
                            android.widget.Toast.makeText(context, "Locked notes can't be shown on the home screen", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            val ok = AddWidgets.requestPin(context, note.id, note.noteType)
                            if (!ok) {
                                android.widget.Toast.makeText(context, "Your launcher doesn't support widget pinning", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                DropdownMenuItem(
                    text = { Text("Select") },
                    leadingIcon = { Icon(Icons.Filled.CheckCircle, null) },
                    onClick = {
                        showCardMenu = false
                        onLongClick()
                    }
                )
            }
            // Blur the body when locked. blur() requires Android 12+ (API 31); on
            // older devices we fall back to a heavy overlay.
            val supportsBlur = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
            val contentModifier = if (note.isLocked && supportsBlur) {
                Modifier.blur(18.dp)
            } else Modifier
            Column(modifier = contentModifier.padding(16.dp)) {
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
                    val playingId by com.hawatri.pinit.util.AudioPlayback.playingNoteId.collectAsState()
                    val isPlayingThis = playingId == note.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            audioData?.path?.takeIf { it.isNotBlank() }?.let {
                                com.hawatri.pinit.util.AudioPlayback.toggle(context, note.id, it)
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Mic, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (isPlayingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlayingThis) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                if (isPlayingThis) "Stop" else "Play",
                                modifier = Modifier.size(20.dp),
                                tint = if (isPlayingThis) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (durMs > 0) "%d:%02d".format(durMs / 60000, (durMs / 1000) % 60) else "Recording",
                            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
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
            val isAudioType = note.noteType == NoteType.AUDIO
            val isImageType = note.noteType == NoteType.IMAGE
            val isAppListType = note.noteType == NoteType.APPLIST

            // Helper: fire a share intent for the IMAGE / PDF row, picking the right
            // mime so receivers (Gmail, Drive, WhatsApp, etc.) preview it correctly.
            fun shareUri(mime: String) {
                try {
                    val uri = Uri.parse(note.text)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    context.startActivity(Intent.createChooser(send, "Share"))
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Couldn't share", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

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
                            isAudioType -> {
                                // Share the recording file via FileProvider
                                val path = audioData?.path
                                if (!path.isNullOrBlank()) {
                                    try {
                                        val file = java.io.File(path)
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context, "${context.packageName}.provider", file
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "audio/*"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share recording"))
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Could not share recording", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            isImageType -> shareUri("image/*")
                            isAppListType -> {
                                // Launch the first app in the list — tapping a card already
                                // opens the editor, so the action row does the more useful
                                // thing of actually launching one.
                                val items = try {
                                    gson.fromJson(note.text, Array<AppNoteItem>::class.java).toList()
                                } catch (e: Exception) { emptyList() }
                                val first = items.firstOrNull()?.packageName
                                if (first != null) {
                                    val launch = context.packageManager.getLaunchIntentForPackage(first)
                                    if (launch != null) {
                                        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        context.startActivity(launch)
                                    } else {
                                        android.widget.Toast.makeText(context, "App not installed", android.widget.Toast.LENGTH_SHORT).show()
                                    }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Secondary share action — own click handler so it doesn't
                            // bubble up and trigger the row's "open" path.
                            Icon(
                                Icons.Filled.Share, "Share PDF",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { shareUri("application/pdf") }
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Icon(Icons.Filled.PictureAsPdf, "Open", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                    isAudioType -> {
                        Text("Share", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Filled.Share, "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    isImageType -> {
                        Text("Share", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Filled.Share, "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    isAppListType -> {
                        Text("Open app", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Outlined.OpenInNew, "Open", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    else -> {
                        Text("Copy", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Outlined.ContentCopy, "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
            // Lock overlay — sits on top of the blurred content for locked notes.
            // It absorbs all taps (clickable below) so the underlying Copy/Call/Open
            // row can't be triggered. Tapping the overlay routes through the card's
            // onClick, which already gates locked notes behind biometric auth.
            if (note.isLocked) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = if (supportsBlur) 0.15f else 0.85f))
                        .combinedClickable(onClick = onClick, onLongClick = onLongClick),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Lock, "Locked",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Tap to unlock", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
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
    TITLE_ZA("Title Z → A"),
    MANUAL("Manual order")
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
