package com.hawatri.pinit.ui

import android.content.Intent
import android.net.Uri
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

data class LinkPreviewData(val title: String, val description: String, val imageUrl: String, val isVideo: Boolean = false)
data class LinkNoteData(val url: String, val title: String, val description: String, val imageUrl: String, val isVideo: Boolean = false)

private fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    return if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) "https://$trimmed" else trimmed
}

private val YT_ID_REGEX = Regex(
    "(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|v/|shorts/)|youtu\\.be/)([A-Za-z0-9_-]{11})",
    RegexOption.IGNORE_CASE
)

private fun extractYouTubeId(url: String): String? = YT_ID_REGEX.find(url)?.groupValues?.getOrNull(1)

private fun isKnownVideoHost(url: String): Boolean {
    val lower = url.lowercase()
    return extractYouTubeId(url) != null ||
        lower.contains("vimeo.com/") ||
        lower.contains("dailymotion.com/video/") ||
        lower.contains("twitch.tv/videos/") ||
        lower.contains("tiktok.com/") && lower.contains("/video/") ||
        lower.contains("instagram.com/reel/") ||
        lower.contains("instagram.com/p/")
}

suspend fun fetchLinkMetadata(url: String): LinkPreviewData? = withContext(Dispatchers.IO) {
    try {
        val formattedUrl = normalizeUrl(url)
        val ytId = extractYouTubeId(formattedUrl)

        val doc = Jsoup.connect(formattedUrl)
            .userAgent("Mozilla/5.0 (Android) PinIt/1.0")
            .timeout(8000)
            .followRedirects(true)
            .get()
        val title = doc.select("meta[property=og:title]").attr("content").takeIf { it.isNotEmpty() }
            ?: doc.select("meta[name=twitter:title]").attr("content").takeIf { it.isNotEmpty() }
            ?: doc.title()
        val description = doc.select("meta[property=og:description]").attr("content").takeIf { it.isNotEmpty() }
            ?: doc.select("meta[name=twitter:description]").attr("content").takeIf { it.isNotEmpty() }
            ?: doc.select("meta[name=description]").attr("content")
        val rawImage = doc.select("meta[property=og:image]").attr("content").takeIf { it.isNotEmpty() }
            ?: doc.select("meta[name=twitter:image]").attr("content").takeIf { it.isNotEmpty() }
            ?: doc.select("link[rel=apple-touch-icon]").attr("href").takeIf { it.isNotEmpty() }
            ?: doc.select("link[rel=icon]").attr("href")
        val absImage = when {
            rawImage.isNotBlank() -> try { java.net.URI(formattedUrl).resolve(rawImage).toString() } catch (e: Exception) { rawImage }
            ytId != null -> "https://img.youtube.com/vi/$ytId/hqdefault.jpg"
            else -> ""
        }

        val ogType = doc.select("meta[property=og:type]").attr("content").lowercase()
        val hasOgVideo = doc.select("meta[property=og:video]").isNotEmpty() ||
            doc.select("meta[property=og:video:url]").isNotEmpty() ||
            doc.select("meta[name=twitter:player]").isNotEmpty() ||
            doc.select("meta[name=twitter:card]").attr("content").equals("player", ignoreCase = true)
        val isVideo = ytId != null || ogType.startsWith("video") || hasOgVideo || isKnownVideoHost(formattedUrl)

        LinkPreviewData(title, description, absImage, isVideo)
    } catch (e: Exception) {
        // Network failed — still return a YouTube fallback if applicable
        val ytId = extractYouTubeId(normalizeUrl(url))
        if (ytId != null) {
            LinkPreviewData(
                title = "YouTube",
                description = "",
                imageUrl = "https://img.youtube.com/vi/$ytId/hqdefault.jpg",
                isVideo = true
            )
        } else null
    }
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

    fun resolvePreview(forUrl: String) {
        if (forUrl.isBlank()) return
        isLoading = true
        coroutineScope.launch {
            val meta = fetchLinkMetadata(forUrl)
            val formattedUrl = normalizeUrl(forUrl)
            previewData = if (meta != null) {
                LinkNoteData(formattedUrl, meta.title, meta.description, meta.imageUrl, meta.isVideo)
            } else {
                LinkNoteData(formattedUrl, formattedUrl, "", "", isKnownVideoHost(formattedUrl))
            }
            isLoading = false
        }
    }

    LaunchedEffect(prefillUrl) {
        if (prefillUrl != null && previewData == null) {
            resolvePreview(prefillUrl)
        }
    }

    fun save(pinOverride: Boolean = isPinned): String {
        val data = previewData ?: LinkNoteData(normalizeUrl(linkText), linkText, "", "")
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

    fun openInBrowser() {
        val urlToOpen = previewData?.url ?: normalizeUrl(linkText)
        if (urlToOpen.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
        } catch (e: Exception) { }
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
                    if (previewData != null) {
                        IconButton(onClick = {
                            // Re-fetch preview
                            resolvePreview(previewData!!.url)
                        }) {
                            Icon(Icons.Filled.Refresh, "Refresh preview", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = {
                        isPinned = !isPinned
                        val savedId = save(isPinned)
                        if (isPinned) {
                            val data = previewData ?: LinkNoteData(normalizeUrl(linkText), linkText, "", "")
                            notificationHelper.pinNoteToNotification(savedId, data.title.ifBlank { data.url }, gson.toJson(data), isList = false, noteType = NoteType.LINK)
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
        floatingActionButton = {
            if (previewData != null || linkText.isNotBlank()) {
                ExtendedFloatingActionButton(
                    onClick = { openInBrowser() },
                    icon = { Icon(Icons.Filled.Public, null) },
                    text = { Text("Browse") },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).imePadding(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Spacer(modifier = Modifier.weight(1f))

            if (previewData != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (previewData!!.imageUrl.isNotBlank()) {
                            Box(modifier = Modifier.fillMaxWidth().height(180.dp).clickable { openInBrowser() }) {
                                AsyncImage(
                                    model = previewData!!.imageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (previewData!!.isVideo) {
                                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
                                    Box(
                                        modifier = Modifier.align(Alignment.Center)
                                            .size(64.dp).clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.PlayArrow, "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Column(modifier = Modifier.padding(16.dp)) {
                            TextField(
                                value = previewData!!.title,
                                onValueChange = { newTitle ->
                                    previewData = previewData!!.copy(title = newTitle)
                                },
                                placeholder = { Text("Title", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 2
                            )
                            if (previewData!!.description.isNotBlank()) {
                                Text(
                                    previewData!!.description,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 4, overflow = TextOverflow.Ellipsis,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp).clickable { openInBrowser() }) {
                                Icon(Icons.Filled.Public, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    previewData!!.url,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                TextButton(
                    onClick = { previewData = null },
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                ) {
                    Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit link")
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                ) {
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
                                        .clickable(enabled = !isLoading) { resolvePreview(linkText) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoading) CircularProgressIndicator(color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Filled.ArrowForward, "Fetch", tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
