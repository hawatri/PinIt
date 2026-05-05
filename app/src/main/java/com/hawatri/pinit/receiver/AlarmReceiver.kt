package com.hawatri.pinit.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hawatri.pinit.data.NoteDatabase
import com.hawatri.pinit.util.NotificationHelper
import com.hawatri.pinit.util.EXTRA_NOTE_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val note = NoteDatabase.getDatabase(context).noteDao().getNoteById(noteId)
                if (note != null) {
                    NotificationHelper(context).showReminderNotification(
                        noteId = note.id,
                        title = note.title,
                        text = note.text,
                        isList = note.isList
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
