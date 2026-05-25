package com.hawatri.pinit.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.google.gson.Gson
import com.hawatri.pinit.data.NoteDatabase
import com.hawatri.pinit.ui.ChecklistItemData
import com.hawatri.pinit.util.AudioPlayback
import com.hawatri.pinit.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getStringExtra(NotificationHelper.EXTRA_NOTE_ID)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (intent.action) {
            NotificationHelper.ACTION_REMOVE_PIN -> {
                if (noteId == null) return
                manager.cancel(noteId.hashCode())
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val dao = NoteDatabase.getDatabase(context).noteDao()
                        val note = dao.getNoteById(noteId)
                        if (note != null) dao.updateNote(note.copy(isPinned = false))
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            NotificationHelper.ACTION_COPY_TEXT -> {
                val textToCopy = intent.getStringExtra(NotificationHelper.EXTRA_NOTE_TEXT) ?: return
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Pinned Note", textToCopy))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }
            NotificationHelper.ACTION_TOGGLE_AUDIO -> {
                if (noteId == null) return
                val path = intent.getStringExtra(NotificationHelper.EXTRA_AUDIO_PATH) ?: return
                AudioPlayback.toggle(context, noteId, path)
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val dao = NoteDatabase.getDatabase(context).noteDao()
                        val note = dao.getNoteById(noteId)
                        if (note != null && note.isPinned) {
                            NotificationHelper(context).pinNoteToNotification(
                                note.id, note.title, note.text, note.isList, note.noteType
                            )
                        }
                    } finally { pendingResult.finish() }
                }
            }
            NotificationHelper.ACTION_TOGGLE_ITEM, 
            NotificationHelper.ACTION_CHECK_ALL,
            NotificationHelper.ACTION_ADD_TASK -> {
                if (noteId == null) return
                // Keep the receiver alive long enough to hit the database
                val pendingResult = goAsync() 
                
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = NoteDatabase.getDatabase(context).noteDao()
                    val notes = dao.getAllNotes().firstOrNull()
                    val note = notes?.find { it.id == noteId }
                    
                    if (note != null && note.isList) {
                        val gson = Gson()
                        try {
                            val items = gson.fromJson(note.text, Array<ChecklistItemData>::class.java).toMutableList()
                            
                            when (intent.action) {
                                NotificationHelper.ACTION_TOGGLE_ITEM -> {
                                    val index = intent.getIntExtra(NotificationHelper.EXTRA_ITEM_INDEX, -1)
                                    if (index in items.indices) {
                                        items[index] = items[index].copy(isChecked = !items[index].isChecked)
                                    }
                                }
                                NotificationHelper.ACTION_CHECK_ALL -> {
                                    val allChecked = items.all { it.isChecked }
                                    for (i in items.indices) {
                                        items[i] = items[i].copy(isChecked = !allChecked)
                                    }
                                }
                                NotificationHelper.ACTION_ADD_TASK -> {
                                    val replyText = RemoteInput.getResultsFromIntent(intent)
                                        ?.getCharSequence(NotificationHelper.EXTRA_REPLY_TEXT)?.toString()
                                    
                                    if (!replyText.isNullOrBlank()) {
                                        items.add(ChecklistItemData(text = replyText.trim(), isChecked = false))
                                    }
                                }
                            }

                            val newJson = gson.toJson(items)
                            dao.updateNote(note.copy(text = newJson))
                            
                            // Immediately refresh the notification with updated data
                            NotificationHelper(context).pinNoteToNotification(note.id, note.title, newJson, true)
                        } catch (e: Exception) {}
                    }
                    pendingResult.finish()
                }
            }
        }
    }
}