package com.hawatri.pinit.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hawatri.pinit.R
import com.hawatri.pinit.receiver.NotificationReceiver

class NotificationHelper(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "pinned_notes_channel"

    companion object {
        const val ACTION_REMOVE_PIN = "ACTION_REMOVE_PIN"
        const val ACTION_COPY_TEXT = "ACTION_COPY_TEXT" // New Action
        const val EXTRA_NOTE_ID = "EXTRA_NOTE_ID"
        const val EXTRA_NOTE_TEXT = "EXTRA_NOTE_TEXT"   // New Extra
    }

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Pinned Notes",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    fun pinNoteToNotification(noteId: String, title: String, text: String) {
        val displayTitle = title.ifBlank { "Pinned Note" }

        // Intent for the "Remove" button
        val removeIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_REMOVE_PIN
            putExtra(EXTRA_NOTE_ID, noteId)
        }
        val removePendingIntent = PendingIntent.getBroadcast(
            context,
            noteId.hashCode(),
            removeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for the "Copy" button
        val copyIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_COPY_TEXT
            putExtra(EXTRA_NOTE_TEXT, text)
        }
        val copyPendingIntent = PendingIntent.getBroadcast(
            context,
            (noteId + "_copy").hashCode(), // Different request code to prevent overriding
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayTitle)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .addAction(0, "Copy", copyPendingIntent) // Add Copy Action
            .addAction(android.R.drawable.ic_delete, "Remove", removePendingIntent) // Existing Remove Action

        manager.notify(noteId.hashCode(), builder.build())
    }

    fun unpinNoteFromNotification(noteId: String) {
        manager.cancel(noteId.hashCode())
    }
}