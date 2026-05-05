package com.hawatri.pinit.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.hawatri.pinit.util.NotificationHelper

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationHelper.ACTION_REMOVE_PIN -> {
                val noteId = intent.getStringExtra(NotificationHelper.EXTRA_NOTE_ID) ?: return
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(noteId.hashCode())
            }
            NotificationHelper.ACTION_COPY_TEXT -> {
                val textToCopy = intent.getStringExtra(NotificationHelper.EXTRA_NOTE_TEXT) ?: return
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Pinned Note", textToCopy)
                clipboard.setPrimaryClip(clip)

                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }
}