package com.hawatri.pinit.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hawatri.pinit.data.NoteDatabase
import com.hawatri.pinit.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val notes = NoteDatabase.getDatabase(context).noteDao().getAllNotes().firstOrNull()
                val helper = NotificationHelper(context)
                notes?.filter { it.isPinned && !it.isArchived }?.forEach { note ->
                    helper.pinNoteToNotification(note.id, note.title, note.text, note.isList, note.noteType)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
