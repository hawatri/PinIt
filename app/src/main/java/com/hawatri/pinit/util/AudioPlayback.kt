package com.hawatri.pinit.util

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide singleton for in-app audio playback. Shared by:
 *   - NoteCard (Compose) — observes [playingNoteId] and shows a play/stop button
 *   - NotificationReceiver — toggles via the notification's Play / Stop action
 */
object AudioPlayback {
    private var player: MediaPlayer? = null
    private val _playingNoteId = MutableStateFlow<String?>(null)
    val playingNoteId: StateFlow<String?> = _playingNoteId

    /** Returns true if started, false if stopped (toggle behaviour). */
    fun toggle(context: Context, noteId: String, path: String): Boolean {
        if (_playingNoteId.value == noteId) {
            stop()
            return false
        }
        stop() // stop any other note still playing
        return try {
            val mp = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener {
                    stop()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    true
                }
                prepare()
                start()
            }
            player = mp
            _playingNoteId.value = noteId
            true
        } catch (e: Exception) {
            stop()
            false
        }
    }

    fun stop() {
        try { player?.stop() } catch (e: Exception) {}
        try { player?.release() } catch (e: Exception) {}
        player = null
        _playingNoteId.value = null
    }

    fun isPlaying(noteId: String): Boolean = _playingNoteId.value == noteId
}
