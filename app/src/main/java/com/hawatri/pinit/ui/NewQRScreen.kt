package com.hawatri.pinit.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.data.NoteType
import com.hawatri.pinit.util.NotificationHelper
import com.hawatri.pinit.util.QrUtils
import com.hawatri.pinit.viewmodel.PinItViewModel
import java.util.UUID
import java.util.concurrent.Executors
import com.hawatri.pinit.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewQRScreen(
    noteId: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: PinItViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationHelper = remember(context) { NotificationHelper(context) }

    var scannedValue by remember { mutableStateOf<String?>(null) }
    var noteTitle by remember { mutableStateOf(context.getString(R.string.qr_code)) }
    var isPinned by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var colorHex by remember { mutableStateOf<String?>(null) }
    var labels by remember { mutableStateOf(listOf<String>()) }
    var showLabelsSheet by remember { mutableStateOf(false) }
    var currentNoteId by remember(noteId) { mutableStateOf(noteId ?: UUID.randomUUID().toString()) }

    val notesList by viewModel.notes.collectAsState()
    var isInitialized by remember { mutableStateOf(false) }
    var scanningActive by remember { mutableStateOf(noteId == null) }
    var showSaveToGalleryDialog by remember { mutableStateOf(false) }
    var savedToGalleryAsked by remember { mutableStateOf(false) }

    LaunchedEffect(notesList, noteId) {
        if (noteId != null && !isInitialized && notesList.isNotEmpty()) {
            val existing = notesList.find { it.id == noteId }
            if (existing != null) {
                noteTitle = existing.title
                scannedValue = existing.text
                isPinned = existing.isPinned
                isLocked = existing.isLocked
                colorHex = existing.colorHex
                labels = existing.labels
                scanningActive = false
                savedToGalleryAsked = true
                isInitialized = true
            }
        }
    }

    val qrBitmap = remember(scannedValue) {
        scannedValue?.takeIf { it.isNotBlank() }?.let { QrUtils.generateQrBitmap(it, 512) }
    }

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasCameraPermission = granted }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(input)
                input?.close()
                if (bitmap != null) {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    BarcodeScanning.getClient().process(image)
                        .addOnSuccessListener { codes ->
                            val raw = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                            if (raw != null) {
                                scannedValue = raw
                                scanningActive = false
                                showSaveToGalleryDialog = true
                            } else {
                                Toast.makeText(context, context.getString(R.string.qr_no_code_found), Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, context.getString(R.string.msg_failed_to_read_image), Toast.LENGTH_SHORT).show()
                        }
                }
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.msg_could_not_open_image), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) { if (!hasCameraPermission && scanningActive) cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }

    fun save(pinOverride: Boolean = isPinned, archiveOverride: Boolean? = null): String {
        val existing = notesList.find { it.id == currentNoteId }
        val note = Note(
            id = currentNoteId,
            title = noteTitle,
            text = scannedValue ?: "",
            formatRanges = emptyList(),
            isPinned = pinOverride,
            isArchived = archiveOverride ?: existing?.isArchived ?: false,
            isList = false,
            noteType = NoteType.QR,
            colorHex = colorHex,
            isLocked = isLocked,
            labels = labels
        )
        if (existing != null) viewModel.updateNote(note) else viewModel.addNote(note)
        return currentNoteId
    }

    fun openScannedValue() {
        val v = scannedValue?.trim() ?: return
        if (v.isBlank()) return
        try {
            val uri = when {
                v.startsWith("http://", true) || v.startsWith("https://", true) -> Uri.parse(v)
                v.contains("://") -> Uri.parse(v)
                else -> {
                    Toast.makeText(context, context.getString(R.string.qr_not_a_link_copied), Toast.LENGTH_SHORT).show()
                    val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("QR", v))
                    return
                }
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.msg_could_not_open), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = Color.White)
                    }
                },
                actions = {
                    if (scanningActive) {
                        TooltipIconButton(
                            tooltip = stringResource(R.string.qr_pick_from_gallery),
                            icon = Icons.Filled.Image,
                            tint = Color.White,
                            onClick = { galleryLauncher.launch("image/*") }
                        )
                    }
                    if (scannedValue != null && !scanningActive) {
                        // Share
                        TooltipIconButton(
                            tooltip = stringResource(R.string.action_share),
                            icon = Icons.Filled.Share,
                            tint = Color.White,
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, scannedValue ?: "")
                                }
                                context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_qr_content)))
                            }
                        )
                        // Archive
                        TooltipIconButton(
                            tooltip = stringResource(R.string.action_archive),
                            icon = Icons.Filled.Archive,
                            tint = Color.White,
                            onClick = {
                                if (isPinned) notificationHelper.unpinNoteFromNotification(currentNoteId)
                                save(pinOverride = false, archiveOverride = true)
                                onNavigateBack()
                            }
                        )
                        TooltipIconButton(
                            tooltip = if (isPinned) stringResource(R.string.unpin_from_notifications) else stringResource(R.string.pin_to_notifications),
                            icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else Color.White,
                            onClick = {
                                isPinned = !isPinned
                                val savedId = save(isPinned)
                                if (isPinned) notificationHelper.pinNoteToNotification(savedId, noteTitle, scannedValue ?: "", isList = false, noteType = NoteType.QR)
                                else notificationHelper.unpinNoteFromNotification(savedId)
                            }
                        )
                        TooltipIconButton(
                            tooltip = stringResource(R.string.action_rescan),
                            icon = Icons.Filled.QrCodeScanner,
                            tint = Color.White,
                            onClick = { scanningActive = true; scannedValue = null; savedToGalleryAsked = false }
                        )
                        TooltipIconButton(
                            tooltip = stringResource(R.string.labels),
                            icon = Icons.Filled.Label,
                            tint = if (labels.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.White,
                            onClick = { showLabelsSheet = true }
                        )
                        TooltipIconButton(
                            tooltip = if (isLocked) stringResource(R.string.unlock_note) else stringResource(R.string.lock_note),
                            icon = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            tint = if (isLocked) MaterialTheme.colorScheme.primary else Color.White,
                            onClick = { isLocked = !isLocked; save() }
                        )
                        ColorPickerMenuButton(
                            selectedColor = colorHex,
                            onColorSelected = { colorHex = it.ifBlank { null }; save() },
                            iconTint = Color.White
                        )
                        TooltipIconButton(
                            tooltip = stringResource(R.string.action_save),
                            icon = Icons.Filled.Check,
                            tint = Color.White,
                            onClick = { save(); onNavigateBack() }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            if (scannedValue != null && !scanningActive) {
                ExtendedFloatingActionButton(
                    onClick = { openScannedValue() },
                    icon = { Icon(Icons.Filled.OpenInNew, null) },
                    text = { Text(stringResource(R.string.action_open)) },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        },
        containerColor = if (scanningActive) Color.Black else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (scanningActive) {
                if (hasCameraPermission) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            ProcessCameraProvider.getInstance(ctx).addListener({
                                val provider = ProcessCameraProvider.getInstance(ctx).get()
                                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                                val analysis = ImageAnalysis.Builder()
                                    .setTargetResolution(Size(1280, 720))
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build().also {
                                        it.setAnalyzer(Executors.newSingleThreadExecutor(), QrCodeAnalyzer { result ->
                                            scannedValue = result
                                            scanningActive = false
                                            if (!savedToGalleryAsked) showSaveToGalleryDialog = true
                                        })
                                    }
                                try {
                                    provider.unbindAll()
                                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                                } catch (e: Exception) { e.printStackTrace() }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
                        Box(modifier = Modifier.align(Alignment.Center).size(250.dp).border(4.dp, Color(0xFFC0C8D0), RoundedCornerShape(16.dp)))
                        Text(stringResource(R.string.qr_point_camera), color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp))

                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).clickable { galleryLauncher.launch("image/*") },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Icon(Icons.Filled.Image, null, tint = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.qr_pick_from_gallery), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.qr_camera_permission), color = Color.White, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.action_grant_permission)) }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { galleryLauncher.launch("image/*") }) { Text(stringResource(R.string.qr_pick_from_gallery_instead)) }
                    }
                }
            } else {
                // Preview / edit layout
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    if (qrBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            modifier = Modifier.size(140.dp)
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.qr_code),
                                modifier = Modifier.fillMaxSize().padding(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = scannedValue ?: "",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }

    if (showSaveToGalleryDialog && qrBitmap != null) {
        AlertDialog(
            onDismissRequest = { showSaveToGalleryDialog = false; savedToGalleryAsked = true },
            title = { Text(stringResource(R.string.qr_save_to_gallery)) },
            text = { Text(stringResource(R.string.qr_save_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    val uri = QrUtils.saveQrToGallery(context, qrBitmap)
                    Toast.makeText(context, if (uri != null) context.getString(R.string.qr_saved_to_gallery) else context.getString(R.string.msg_failed_to_save), Toast.LENGTH_SHORT).show()
                    showSaveToGalleryDialog = false
                    savedToGalleryAsked = true
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveToGalleryDialog = false; savedToGalleryAsked = true }) { Text(stringResource(R.string.action_skip)) }
            }
        )
    }

    if (showLabelsSheet) {
        val allLabels = remember(notesList) { notesList.flatMap { it.labels }.distinct() }
        LabelsEditorSheet(
            currentLabels = labels,
            allExistingLabels = allLabels,
            onLabelsChange = { labels = it; if (scannedValue != null) save() },
            onDismiss = { showLabelsSheet = false }
        )
    }
}
