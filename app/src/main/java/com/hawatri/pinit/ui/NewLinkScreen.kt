package com.hawatri.pinit.ui

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
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.UUID

data class LinkPreviewData(val title: String, val description: String, val imageUrl: String)
data class LinkNoteData(val url: String, val title: String, val description: String, val imageUrl: String)

suspend fun fetchLinkMetadata(url: String): LinkPreviewData? = withContext(Dispatchers.IO) {
    try {
        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
        val doc = Jsoup.connect(formattedUrl).timeout(5000).get()
        val title = doc.select("meta[property=og:title]").attr("content").takeIf { it.isNotEmpty() } ?: doc.title()
        val description = doc.select("meta[property=og:description]").attr("content")
        val imageUrl = doc.select("meta[property=og:image]").attr("content")
        LinkPreviewData(title, description, imageUrl)
    } catch (e: Exception) { null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewLinkScreen(
    noteId: String? = null,
    prefillUrl: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: PinItViewModel
) {
    val context = LocalContext.current
    val notificationHelper = remember(context) { NotificationHelper(context) }
    val gson = remember { Gson() }
    val coroutineScope = rememberCoroutineScope()

    var linkText by remember { mutableStateOf(prefillUrl ?: "") }
    var previewData by remember { mutableStateOf<LinkNoteData?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isPinned by remember { mutableStateOf(false) }
    var currentNoteId by remember(noteId) { mutableStateOf(noteId ?: UUID.randomUUID().toString()) }

    val notesList by viewModel.notes.collectAsState()
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(notesList, noteId) {
        if (noteId != null && !isInitialized && notesList.isNotEmpty()) {
            val existing = notesList.find { it.id == noteId }
            if (existing != null) {
                try {
                    val data = gson.fromJson(existing.text, LinkNoteData::class.java)
                    previewData = data
                    linkText = data.url
                } catch (e: Exception) { linkText = existing.text }
                isPinned = existing.isPinned
                isInitialized = true
            }
        }
    }

    // Auto-fetch if opened via share intent with a URL
    LaunchedEffect(prefillUrl) {
        if (prefillUrl != null && previewData == null) {
            isLoading = true
            val meta = fetchLinkMetadata(prefillUrl)
            if (meta != null) {
                previewData = LinkNoteData(prefillUrl, meta.title, meta.description, meta.imageUrl)
            }
            isLoading = false
        }
    }

    fun save(pinOverride: Boolean = isPinned): String {
        val data = previewData ?: LinkNoteData(linkText, linkText, "", "")
        val note = Note(
            id = currentNoteId,
            title = data.title.ifBlank { data.url },
            text = gson.toJson(data),
            formatRanges = emptyList(),
            isPinned = pinOverride,
            isList = false,
            noteType = NoteType.LINK
        )
        val existing = notesList.find { it.id == currentNoteId }
        if (existing != null) viewModel.updateNote(note) else viewModel.addNote(note)
        return currentNoteId
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
                    IconButton(onClick = {
                        isPinned = !isPinned
                        val savedId = save(isPinned)
                        if (isPinned) {
                            val text = previewData?.title ?: linkText
                            notificationHelper.pinNoteToNotification(savedId, text, linkText)
                        } else {
                            notificationHelper.unpinNoteFromNotification(savedId)
                        }
                    }) {
                        Icon(if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, "Pin",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { if (previewData != null || linkText.isNotBlank()) { save(); onNavigateBack() } }) {
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
                if (previewData != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).clickable { previewData = null },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (previewData!!.imageUrl.isNotEmpty()) {
                            AsyncImage(
                                model = previewData!!.imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        Column {
                            Text(
                                text = previewData!!.title,
                                fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            if (previewData!!.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(previewData!!.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(previewData!!.url, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("*Mandatory field", color = Color(0xFFD32F2F), fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            TextField(
                                value = linkText,
                                onValueChange = { linkText = it },
                                placeholder = { Text("Link*", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                ),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            if (linkText.isNotEmpty() || isLoading) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                        .clickable(enabled = !isLoading) {
                                            isLoading = true
                                            coroutineScope.launch {
                                                val meta = fetchLinkMetadata(linkText)
                                                val formattedUrl = if (!linkText.startsWith("http")) "https://$linkText" else linkText
                                                previewData = if (meta != null) {
                                                    LinkNoteData(formattedUrl, meta.title, meta.description, meta.imageUrl)
                                                } else {
                                                    LinkNoteData(formattedUrl, formattedUrl, "", "")
                                                }
                                                isLoading = false
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Filled.Public, "Fetch", tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
