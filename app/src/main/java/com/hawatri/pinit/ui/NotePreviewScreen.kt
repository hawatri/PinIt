package com.hawatri.pinit.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import com.hawatri.pinit.R
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.data.NoteType
import com.hawatri.pinit.viewmodel.PinItViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Read-only, full-screen look at a PDF or link note — what tapping one of those
 * cards opens. PDFs render every page in a scrolling column; links load the live
 * page in a WebView. "Edit" swaps to the note's edit screen, "Open" hands off to
 * an external viewer/browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotePreviewScreen(
    noteId: String,
    onNavigateBack: () -> Unit,
    onEdit: (Note) -> Unit,
    viewModel: PinItViewModel
) {
    val context = LocalContext.current
    val notes by viewModel.notes.collectAsState()
    val note = notes.find { it.id == noteId }

    val linkData = remember(note?.text, note?.noteType) {
        if (note?.noteType == NoteType.LINK) {
            try { Gson().fromJson(note.text, LinkNoteData::class.java) } catch (e: Exception) { null }
        } else null
    }
    val linkUrl = remember(linkData) {
        linkData?.url?.trim()?.takeIf { it.isNotEmpty() }?.let {
            if (it.startsWith("http://", true) || it.startsWith("https://", true)) it else "https://$it"
        }
    }

    // PDF page indicator ("3 / 12") lives in the top bar, so the PDF pane reports up.
    var pageIndicator by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var webCanGoBack by remember { mutableStateOf(false) }

    // Back walks the in-page history first, like a browser, then leaves the preview.
    BackHandler(enabled = webCanGoBack) { webView?.goBack() }

    val title = when {
        note == null -> ""
        note.noteType == NoteType.LINK ->
            linkData?.title?.takeIf { it.isNotBlank() }
                ?: note.title.takeIf { it.isNotBlank() }
                ?: linkUrl?.let { Uri.parse(it).host } ?: ""
        else -> note.title.ifBlank { stringResource(R.string.pdf_document) }
    }

    fun openExternally() {
        val n = note ?: return
        try {
            val intent = when (n.noteType) {
                NoteType.PDF -> Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(n.text), "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                else -> Intent(Intent.ACTION_VIEW, Uri.parse(webView?.url ?: linkUrl ?: return))
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, context.getString(R.string.msg_no_app_to_open), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        val sub = if (note?.noteType == NoteType.LINK) linkUrl?.let { Uri.parse(it).host } else pageIndicator
                        if (!sub.isNullOrBlank()) {
                            Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    TooltipIconButton(stringResource(R.string.action_back), onNavigateBack, Icons.AutoMirrored.Filled.ArrowBack)
                },
                actions = {
                    if (note != null) {
                        TooltipIconButton(stringResource(R.string.action_edit), { onEdit(note) }, Icons.Filled.Edit)
                        TooltipIconButton(stringResource(R.string.action_open), { openExternally() }, Icons.Outlined.OpenInNew)
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                note == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                note.noteType == NoteType.PDF -> PdfPagesPane(
                    uriString = note.text,
                    onPageIndicator = { pageIndicator = it },
                    onEdit = { onEdit(note) }
                )
                linkUrl != null -> WebPagePane(
                    url = linkUrl,
                    onWebView = { webView = it },
                    onCanGoBack = { webCanGoBack = it },
                    onOpenExternally = { openExternally() }
                )
                else -> PreviewMessage(stringResource(R.string.preview_link_error)) {
                    TextButton(onClick = { onEdit(note) }) { Text(stringResource(R.string.action_edit)) }
                }
            }
        }
    }
}

@Composable
private fun PreviewMessage(message: String, action: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action()
    }
}

/** Holds an open PdfRenderer. PdfRenderer allows one open page at a time, hence the mutex. */
private class PdfDocumentHandle(private val pfd: ParcelFileDescriptor) {
    private val renderer = PdfRenderer(pfd)
    private val lock = Mutex()
    val pageCount: Int = renderer.pageCount
    val aspectRatios: List<Float> = (0 until pageCount).map { i ->
        renderer.openPage(i).use { it.width.toFloat() / it.height.coerceAtLeast(1) }
    }

    suspend fun render(index: Int, targetWidth: Int): Bitmap? = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                renderer.openPage(index).use { page ->
                    val w = targetWidth.coerceIn(1, 2048)
                    val h = (w / aspectRatios[index]).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    Canvas(bmp).drawColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            } catch (e: Exception) { null }
        }
    }

    fun close() {
        try { renderer.close() } catch (e: Exception) { }
        try { pfd.close() } catch (e: Exception) { }
    }
}

@Composable
private fun PdfPagesPane(uriString: String, onPageIndicator: (String?) -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    // null = still opening, Result.failure = can't be read (moved, deleted, permission revoked, encrypted)
    val doc by produceState<Result<PdfDocumentHandle>?>(null, uriString) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val pfd = context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")
                    ?: error("no descriptor")
                PdfDocumentHandle(pfd)
            }
        }
    }
    DisposableEffect(doc) {
        val handle = doc?.getOrNull()
        onDispose { handle?.close() }
    }

    val result = doc
    when {
        result == null -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        result.isFailure || result.getOrNull()?.pageCount == 0 -> {
            LaunchedEffect(Unit) { onPageIndicator(null) }
            PreviewMessage(stringResource(R.string.preview_pdf_error)) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
            }
        }
        else -> {
            val handle = result.getOrThrow()
            val listState = rememberLazyListState()
            val current by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
            LaunchedEffect(current, handle.pageCount) { onPageIndicator("$current / ${handle.pageCount}") }

            BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                val pageWidthPx = with(LocalDensity.current) { (maxWidth - 24.dp).roundToPx() }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(handle.pageCount) { index ->
                        val bitmap by produceState<Bitmap?>(null, handle, index, pageWidthPx) {
                            value = handle.render(index, pageWidthPx)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth().aspectRatio(handle.aspectRatios[index])
                        ) {
                            bitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = stringResource(R.string.pdf_preview),
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebPagePane(
    url: String,
    onWebView: (WebView?) -> Unit,
    onCanGoBack: (Boolean) -> Unit,
    onOpenExternally: () -> Unit
) {
    var progress by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        if (failed) {
            PreviewMessage(stringResource(R.string.preview_link_error)) {
                TextButton(onClick = { failed = false; reloadKey++ }) { Text(stringResource(R.string.action_retry)) }
                TextButton(onClick = onOpenExternally) { Text(stringResource(R.string.action_open)) }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                // Keep http(s) inside the preview; hand intent:, mailto:, tel:, app links etc. to the system.
                                val scheme = request.url.scheme?.lowercase()
                                if (scheme == "http" || scheme == "https") return false
                                try { view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (e: Exception) { }
                                return true
                            }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) failed = true
                            }
                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                onCanGoBack(view.canGoBack())
                            }
                        }
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                        }
                        onWebView(this)
                    }
                },
                update = { view ->
                    val key = "$url#$reloadKey"
                    if (view.tag != key) { view.tag = key; view.loadUrl(url) }
                },
                onRelease = { view ->
                    onWebView(null)
                    onCanGoBack(false)
                    view.stopLoading()
                    view.destroy()
                }
            )
            if (progress in 0..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                )
            }
        }
    }
}
