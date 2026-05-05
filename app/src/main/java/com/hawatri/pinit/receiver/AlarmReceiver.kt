package com.hawatri.pinit.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hawatri.pinit.util.NotificationHelper

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getStringExtra("EXTRA_NOTE_ID") ?: return
        val noteTitle = intent.getStringExtra("EXTRA_NOTE_TITLE") ?: "Pinned Task Reminder"
        
        // Re-use your existing NotificationHelper to show the note
        val notificationHelper = NotificationHelper(context)
        notificationHelper.pinNoteToNotification(
            noteId = noteId,
            title = "Reminder: $noteTitle",
            text = "It's time to check this task!", 
            isList = false // You can query the DB here if you want to show the full checklist instead
        )
    }
}
