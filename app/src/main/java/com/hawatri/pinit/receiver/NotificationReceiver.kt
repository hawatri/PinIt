package com.hawatri.pinit.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hawatri.pinit.util.NotificationHelper

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationHelper.ACTION_REMOVE_PIN) {
            val noteId = intent.getStringExtra(NotificationHelper.EXTRA_NOTE_ID) ?: return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Cancel the specific notification using its hashcode
            manager.cancel(noteId.hashCode())
        }
    }
}