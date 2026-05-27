package com.hawatri.pinit.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.data.NoteType
import com.hawatri.pinit.util.NotificationHelper
import com.hawatri.pinit.viewmodel.PinItViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class AppNoteItem(val packageName: String, val appName: String)
data class AppInfo(val packageName: String, val name: String, val icon: Drawable)

suspend fun getInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    val resolveInfoList = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    resolveInfoList.map {
        AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm))
    }.distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewAppListScreen(
    noteId: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: PinItViewModel
) {
    val context = LocalContext.current
    val notificationHelper = remember(context) { NotificationHelper(context) }
    val gson = remember { Gson() }

    var showBottomSheet by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    val selectedApps = remember { mutableStateListOf<AppInfo>() }
    var isPinned by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var colorHex by remember { mutableStateOf<String?>(null) }
    var labels by remember { mutableStateOf(listOf<String>()) }
    var showLabelsSheet by remember { mutableStateOf(false) }
    var currentNoteId by remember(noteId) { mutableStateOf(noteId ?: UUID.randomUUID().toString()) }

    val notesList by viewModel.notes.collectAsState()
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { installedApps = getInstalledApps(context) }

    LaunchedEffect(notesList, noteId) {
        if (noteId != null && !isInitialized && notesList.isNotEmpty()) {
            val existing = notesList.find { it.id == noteId }
            if (existing != null) {
                try {
                    val savedItems = gson.fromJson(existing.text, Array<AppNoteItem>::class.java).toList()
                    val pm = context.packageManager
                    val restoredApps = savedItems.mapNotNull { item ->
                        try {
                            val icon = pm.getApplicationIcon(item.packageName)
                            AppInfo(item.packageName, item.appName, icon)
                        } catch (e: Exception) { null }
                    }
                    selectedApps.clear()
                    selectedApps.addAll(restoredApps)
                } catch (e: Exception) { /* ignore */ }
                isPinned = existing.isPinned
                isLocked = existing.isLocked
                colorHex = existing.colorHex
                labels = existing.labels
                isInitialized = true
            }
        }
    }

    fun save(pinOverride: Boolean = isPinned, archiveOverride: Boolean? = null): String {
        val items = selectedApps.map { AppNoteItem(it.packageName, it.name) }
        val title = if (items.isEmpty()) "App Shortcuts" else "${items.size} App${if (items.size > 1) "s" else ""}"
        val existing = notesList.find { it.id == currentNoteId }
        val note = Note(
            id = currentNoteId,
            title = title,
            text = gson.toJson(items),
            formatRanges = emptyList(),
            isPinned = pinOverride,
            isArchived = archiveOverride ?: existing?.isArchived ?: false,
            isList = false,
            noteType = NoteType.APPLIST,
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
                    if (selectedApps.isNotEmpty()) {
                        // Share - share app list as text
                        TooltipIconButton(
                            tooltip = "Share",
                            icon = Icons.Filled.Share,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = {
                                val shareText = selectedApps.joinToString("\n") { it.name }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share app list"))
                            }
                        )

                        // Archive
                        TooltipIconButton(
                            tooltip = "Archive",
                            icon = Icons.Filled.Archive,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = {
                                if (isPinned) notificationHelper.unpinNoteFromNotification(currentNoteId)
                                save(pinOverride = false, archiveOverride = true)
                                onNavigateBack()
                            }
                        )
                    }
                    TooltipIconButton(
                        tooltip = if (isPinned) "Unpin from notifications" else "Pin to notifications",
                        icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            isPinned = !isPinned
                            val savedId = save(isPinned)
                            val items = selectedApps.map { AppNoteItem(it.packageName, it.name) }
                            val titleText = if (items.isEmpty()) "App Shortcuts" else "${items.size} App${if (items.size > 1) "s" else ""}"
                            if (isPinned) notificationHelper.pinNoteToNotification(savedId, titleText, gson.toJson(items), isList = false, noteType = NoteType.APPLIST)
                            else notificationHelper.unpinNoteFromNotification(savedId)
                        }
                    )
                    TooltipIconButton(
                        tooltip = "Labels",
                        icon = Icons.Filled.Label,
                        tint = if (labels.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { showLabelsSheet = true }
                    )
                    if (selectedApps.isNotEmpty()) {
                        TooltipIconButton(
                            tooltip = if (isLocked) "Unlock note" else "Lock note",
                            icon = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { isLocked = !isLocked; save() }
                        )
                        ColorPickerMenuButton(
                            selectedColor = colorHex,
                            onColorSelected = { colorHex = it.ifBlank { null }; save() }
                        )
                    }
                    TooltipIconButton(
                        tooltip = "Save",
                        icon = Icons.Filled.Check,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { if (selectedApps.isNotEmpty()) { save(); onNavigateBack() } }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    selectedApps.forEach { app ->
                        SelectedAppItem(app = app, onRemove = { selectedApps.remove(app) })
                    }
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                            .clickable { showBottomSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, "Add App", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(onDismissRequest = { showBottomSheet = false }, containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text("Pick App or Shortcut", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Apps", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxHeight(0.8f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(installedApps) { app ->
                            AppPickerItem(app = app) {
                                if (!selectedApps.any { it.packageName == app.packageName }) selectedApps.add(app)
                                showBottomSheet = false
                            }
                        }
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
            onLabelsChange = { labels = it; if (selectedApps.isNotEmpty()) save() },
            onDismiss = { showLabelsSheet = false }
        )
    }
}

@Composable
fun SelectedAppItem(app: AppInfo, onRemove: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
        Box {
            AsyncImage(model = app.icon, contentDescription = app.name, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
            Box(
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).size(20.dp).clip(CircleShape)
                    .background(Color(0xFF4A525E)).clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                HorizontalDivider(modifier = Modifier.width(10.dp), color = Color.White, thickness = 2.dp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(app.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
fun AppPickerItem(app: AppInfo, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        AsyncImage(model = app.icon, contentDescription = app.name, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(modifier = Modifier.height(8.dp))
        Text(app.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
