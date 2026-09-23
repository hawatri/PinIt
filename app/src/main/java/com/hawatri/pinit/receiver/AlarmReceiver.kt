package com.hawatri.pinit.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hawatri.pinit.data.NoteDatabase
import com.hawatri.pinit.util.NotificationHelper
import com.hawatri.pinit.util.EXTRA_NOTE_ID
import com.hawatri.pinit.util.EXTRA_REMINDER_TIME
import com.hawatri.pinit.util.formatAlarmText
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
                if (note != null && !note.isArchived) {
                    val helper = NotificationHelper(context)
                    helper.showReminderNotification(
                        noteId = note.id,
                        title = note.title,
                        text = note.text,
                        isList = note.isList,
                        noteType = note.noteType
                    )
                    val remaining = if (firedTime > 0L) note.reminders - firedTime else note.reminders
                    dao.updateNote(
                        note.copy(
                            reminders = remaining,
                            isPinned = true,
                            reminderText = remaining.minOrNull()?.let { formatAlarmText(it) }
                        )
                    )
                    helper.pinNoteToNotification(
                        note.id, note.title, note.text, note.isList, note.noteType
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
