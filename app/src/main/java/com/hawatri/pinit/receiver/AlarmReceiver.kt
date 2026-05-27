package com.hawatri.pinit.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hawatri.pinit.data.NoteDatabase
import com.hawatri.pinit.util.NotificationHelper
import com.hawatri.pinit.util.EXTRA_NOTE_ID
import com.hawatri.pinit.util.EXTRA_REMINDER_TIME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: return
        val firedTime = intent.getLongExtra(EXTRA_REMINDER_TIME, 0L)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = NoteDatabase.getDatabase(context).noteDao()
                val note = dao.getNoteById(noteId)
                if (note != null) {
                    NotificationHelper(context).showReminderNotification(
                        noteId = note.id,
                        title = note.title,
                        text = note.text,
                        isList = note.isList
                    )
                    if (firedTime > 0L && firedTime in note.reminders) {
                        dao.updateNote(note.copy(reminders = note.reminders - firedTime))
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
