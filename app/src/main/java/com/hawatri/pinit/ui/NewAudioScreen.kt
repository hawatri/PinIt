package com.hawatri.pinit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.data.NoteType
import com.hawatri.pinit.util.NotificationHelper
import com.hawatri.pinit.viewmodel.PinItViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class AudioNoteData(val path: String, val durationMs: Long = 0L)

private enum class RecordingState { IDLE, RECORDING, RECORDED, PLAYING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAudioScreen(
    noteId: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: PinItViewModel
) {
    val context = LocalContext.current
    val notificationHelper = remember(context) { NotificationHelper(context) }
    val gson = remember { Gson() }
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf(RecordingState.IDLE) }
    var currentFilePath by remember { mutableStateOf<String?>(null) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var playerPosition by remember { mutableFloatStateOf(0f) }
    var noteTitle by remember { mutableStateOf("") }
    var isPinned by remember { mutableStateOf(false) }
    var labels by remember { mutableStateOf(listOf<String>()) }
    var showLabelsSheet by remember { mutableStateOf(false) }
    var currentNoteId by remember(noteId) { mutableStateOf(noteId ?: UUID.randomUUID().toString()) }

    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    val notesList by viewModel.notes.collectAsState()
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(notesList, noteId) {
        if (noteId != null && !isInitialized && notesList.isNotEmpty()) {
            val existing = notesList.find { it.id == noteId }
            if (existing != null) {
                noteTitle = existing.title
                isPinned = existing.isPinned
                labels = existing.labels
                try {
                    val data = gson.fromJson(existing.text, AudioNoteData::class.java)
                    currentFilePath = data.path
                    durationMs = data.durationMs
                    if (File(data.path).exists()) state = RecordingState.RECORDED
                } catch (e: Exception) { }
                isInitialized = true
            }
        }
    }

    // Timer for recording
    LaunchedEffect(state) {
        if (state == RecordingState.RECORDING) {
            elapsedSeconds = 0
            while (state == RecordingState.RECORDING) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    // Player position updater
    LaunchedEffect(state) {
        if (state == RecordingState.PLAYING) {
            while (state == RecordingState.PLAYING) {
                val p = player
                if (p != null && p.isPlaying) {
                    playerPosition = p.currentPosition.toFloat() / p.duration.coerceAtLeast(1).toFloat()
                }
                delay(200)
            }
        }
    }

    // Cleanup on leave
    DisposableEffect(Unit) {
        onDispose {
            try { recorder?.stop(); recorder?.release() } catch (e: Exception) { }
            recorder = null
            try { player?.stop(); player?.release() } catch (e: Exception) { }
            player = null
        }
    }

    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
    }

    fun startRecording() {
        if (!hasAudioPermission) { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO); return }
        val dir = File(context.filesDir, "recordings").also { it.mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.m4a")
        currentFilePath = file.absolutePath
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
                  else @Suppress("DEPRECATION") MediaRecorder()
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setAudioEncodingBitRate(128000)
        rec.setAudioSamplingRate(44100)
        rec.setOutputFile(file.absolutePath)
        try { rec.prepare(); rec.start(); recorder = rec; state = RecordingState.RECORDING }
        catch (e: Exception) { rec.release(); android.widget.Toast.makeText(context, "Recording failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show() }
    }

    fun stopRecording() {
        try { recorder?.stop() } catch (e: Exception) { }
        try { recorder?.release() } catch (e: Exception) { }
        recorder = null
        durationMs = elapsedSeconds * 1000L
        state = RecordingState.RECORDED
    }

    fun startPlaying() {
        val path = currentFilePath ?: return
        val file = File(path)
        if (!file.exists()) { android.widget.Toast.makeText(context, "Recording file not found", android.widget.Toast.LENGTH_SHORT).show(); return }
        val mp = MediaPlayer()
        mp.setDataSource(path)
        mp.setOnCompletionListener { state = RecordingState.RECORDED; playerPosition = 0f }
        mp.prepare()
        durationMs = mp.duration.toLong()
        mp.start()
        player = mp
        state = RecordingState.PLAYING
    }

    fun pausePlaying() {
        player?.pause()
        state = RecordingState.RECORDED
    }

    fun save(pinOverride: Boolean = isPinned): String {
        val data = AudioNoteData(currentFilePath ?: "", durationMs)
        val note = Note(
            id = currentNoteId,
            title = noteTitle.ifBlank { "Recording ${formatDuration(durationMs)}" },
            text = gson.toJson(data),
            formatRanges = emptyList(),
            isPinned = pinOverride,
            isList = false,
            noteType = NoteType.AUDIO,
            labels = labels
        )
        val existing = notesList.find { it.id == currentNoteId }
        if (existing != null) viewModel.updateNote(note) else viewModel.addNote(note)
        return currentNoteId
    }

    // Pulsing animation for recording indicator
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                actions = {
                    if (state == RecordingState.RECORDED || state == RecordingState.PLAYING) {
                        IconButton(onClick = {
                            isPinned = !isPinned
                            val savedId = save(isPinned)
                            if (isPinned) notificationHelper.pinNoteToNotification(savedId, noteTitle.ifBlank { "Audio" }, "Audio recording")
                            else notificationHelper.unpinNoteFromNotification(savedId)
                        }) {
                            Icon(if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, "Pin",
                                tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { showLabelsSheet = true }) {
                            Icon(Icons.Filled.Label, "Label",
                                tint = if (labels.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { if (currentFilePath != null) { save(); onNavigateBack() } }) {
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
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title field (shown when recorded)
            AnimatedVisibility(state == RecordingState.RECORDED || state == RecordingState.PLAYING) {
                OutlinedTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it },
                    placeholder = { Text("Recording title (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(bottom = 32.dp)
                )
            }

            // Timer / duration display
            Text(
                text = when (state) {
                    RecordingState.RECORDING -> formatDuration(elapsedSeconds * 1000L)
                    else -> formatDuration(durationMs)
                },
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = if (state == RecordingState.RECORDING) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Recording status label
            Text(
                text = when (state) {
                    RecordingState.IDLE -> if (hasAudioPermission) "Tap to record" else "Microphone permission required"
                    RecordingState.RECORDING -> "Recording…"
                    RecordingState.RECORDED -> "Recording ready"
                    RecordingState.PLAYING -> "Playing"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Main record / play button
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(if (state == RecordingState.RECORDING) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(
                        when (state) {
                            RecordingState.RECORDING -> MaterialTheme.colorScheme.error
                            RecordingState.PLAYING -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        when (state) {
                            RecordingState.IDLE, RecordingState.RECORDED -> startRecording()
                            RecordingState.RECORDING -> stopRecording()
                            RecordingState.PLAYING -> pausePlaying()
                        }
                    },
                    modifier = Modifier.size(96.dp)
                ) {
                    Icon(
                        imageVector = when (state) {
                            RecordingState.RECORDING -> Icons.Filled.Stop
                            RecordingState.PLAYING -> Icons.Filled.Pause
                            else -> Icons.Filled.Mic
                        },
                        contentDescription = "Record",
                        tint = when (state) {
                            RecordingState.RECORDING -> Color.White
                            RecordingState.PLAYING -> Color.White
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // Playback row (when recorded but not playing)
            AnimatedVisibility(state == RecordingState.RECORDED) {
                Row(
                    modifier = Modifier.padding(top = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play button
                    IconButton(
                        onClick = { startPlaying() },
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Icon(Icons.Filled.PlayArrow, "Play", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(32.dp))
                    }
                    // Discard & re-record button
                    IconButton(
                        onClick = {
                            currentFilePath?.let { File(it).delete() }
                            currentFilePath = null
                            durationMs = 0L
                            playerPosition = 0f
                            state = RecordingState.IDLE
                        },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Icon(Icons.Filled.Delete, "Discard", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(24.dp))
                    }
                }
            }

            // Seek progress (when playing)
            AnimatedVisibility(state == RecordingState.PLAYING) {
                LinearProgressIndicator(
                    progress = { playerPosition },
                    modifier = Modifier.fillMaxWidth(0.7f).padding(top = 24.dp)
                )
            }
        }
    }

    if (showLabelsSheet) {
        val allLabels = remember(notesList) { notesList.flatMap { it.labels }.distinct() }
        LabelsEditorSheet(
            currentLabels = labels,
            allExistingLabels = allLabels,
            onLabelsChange = { labels = it; if (currentFilePath != null) save() },
            onDismiss = { showLabelsSheet = false }
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
