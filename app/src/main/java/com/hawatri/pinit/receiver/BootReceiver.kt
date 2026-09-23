package com.hawatri.pinit.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hawatri.pinit.data.NoteDatabase
import com.hawatri.pinit.util.NotificationHelper
import com.hawatri.pinit.util.rescheduleAllReminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val notes = NoteDatabase.getDatabase(context).noteDao().getAllNotes().firstOrNull()
                val helper = NotificationHelper(context)
                notes?.filter { it.isPinned && !it.isArchived }?.forEach { note ->
                    helper.pinNoteToNotification(note.id, note.title, note.text, note.isList, note.noteType)
                }
                rescheduleAllReminders(context)
                helper.refreshQuickAdd()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }
}
