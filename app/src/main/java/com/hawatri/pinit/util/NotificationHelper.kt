package com.hawatri.pinit.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.RemoteInput
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.hawatri.pinit.MainActivity
import com.hawatri.pinit.R
import com.hawatri.pinit.receiver.NotificationReceiver
import com.hawatri.pinit.ui.ChecklistItemData

class NotificationHelper(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "pinned_notes_channel"

    companion object {
        const val ACTION_REMOVE_PIN = "ACTION_REMOVE_PIN"
        const val ACTION_COPY_TEXT = "ACTION_COPY_TEXT"
        const val ACTION_TOGGLE_ITEM = "ACTION_TOGGLE_ITEM" 
        const val ACTION_CHECK_ALL = "ACTION_CHECK_ALL"     
        const val ACTION_ADD_TASK = "ACTION_ADD_TASK"       // NEW
        
        const val EXTRA_NOTE_ID = "EXTRA_NOTE_ID"
        const val EXTRA_NOTE_TEXT = "EXTRA_NOTE_TEXT"
        const val EXTRA_ITEM_INDEX = "EXTRA_ITEM_INDEX"     
        const val EXTRA_REPLY_TEXT = "EXTRA_REPLY_TEXT"     // NEW
    }

    init { createChannel() }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Pinned Notes", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
    }

    fun pinNoteToNotification(noteId: String, title: String, text: String, isList: Boolean = false) {
        val displayTitle = title.ifBlank { "Pinned Note" }

        // Core intents
        val removeIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_REMOVE_PIN
            putExtra(EXTRA_NOTE_ID, noteId)
        }
        val removePendingIntent = PendingIntent.getBroadcast(context, noteId.hashCode(), removeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(context, noteId.hashCode(), openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayTitle)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())

        if (isList) {
            // BUILD CUSTOM XML VIEW
            val customView = RemoteViews(context.packageName, R.layout.notif_custom_list)

            try {
                val gson = Gson()
                val items = gson.fromJson(text, Array<ChecklistItemData>::class.java).toList()
                
                // Clear both columns so they don't duplicate on update
                customView.removeAllViews(R.id.notif_items_left_col)
                customView.removeAllViews(R.id.notif_items_right_col)

                // Increased limit to 10 (5 rows * 2 columns)
                val displayLimit = 10 
                
                items.take(displayLimit).forEachIndexed { index, item ->
                    val itemView = RemoteViews(context.packageName, R.layout.notif_list_item)
                    itemView.setTextViewText(R.id.item_text, item.text)
                    
                    val iconRes = if (item.isChecked) R.drawable.ic_check_box else R.drawable.ic_check_box_outline
                    itemView.setImageViewResource(R.id.item_checkbox, iconRes)

                    val toggleIntent = Intent(context, NotificationReceiver::class.java).apply {
                        action = ACTION_TOGGLE_ITEM
                        putExtra(EXTRA_NOTE_ID, noteId)
                        putExtra(EXTRA_ITEM_INDEX, index)
                    }
                    val togglePendingIntent = PendingIntent.getBroadcast(
                        context, (noteId + index).hashCode(), toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    itemView.setOnClickPendingIntent(R.id.item_checkbox, togglePendingIntent)
                    
                    // NEW: Alternate between left (even) and right (odd) columns
                    if (index % 2 == 0) {
                        customView.addView(R.id.notif_items_left_col, itemView)
                    } else {
                        customView.addView(R.id.notif_items_right_col, itemView)
                    }
                }

                if (items.size > displayLimit) {
                    customView.setViewVisibility(R.id.notif_more_text, android.view.View.VISIBLE)
                    customView.setTextViewText(R.id.notif_more_text, "+ ${items.size - displayLimit} more")
                } else {
                    customView.setViewVisibility(R.id.notif_more_text, android.view.View.GONE)
                }

            } catch (e: Exception) {}

            // NEW: Build the Inline RemoteInput Action
            val remoteInput = RemoteInput.Builder(EXTRA_REPLY_TEXT)
                .setLabel("Add Item")
                .build()

            val replyIntent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_ADD_TASK
                putExtra(EXTRA_NOTE_ID, noteId)
            }
            
            // CRITICAL: RemoteInput PendingIntents MUST be FLAG_MUTABLE
            val replyPendingIntent = PendingIntent.getBroadcast(
                context, 
                (noteId + "reply").hashCode(), 
                replyIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE 
            )

            val replyAction = NotificationCompat.Action.Builder(
                0, "Add Task", replyPendingIntent
            ).addRemoteInput(remoteInput).build()

            // 1. Build the Check All PendingIntent
            val checkAllIntent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_CHECK_ALL
                putExtra(EXTRA_NOTE_ID, noteId)
            }
            val checkAllPendingIntent = PendingIntent.getBroadcast(
                context, (noteId + "all").hashCode(), checkAllIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 2. Attach the custom view
            builder.setCustomContentView(customView)
            builder.setCustomBigContentView(customView)
            
            // 3. NEW: Add all three as Native System Actions!
            builder.addAction(replyAction)                                  // Add Task
            builder.addAction(0, "Check All", checkAllPendingIntent)        // Check All
            builder.addAction(0, "Remove", removePendingIntent)             // Remove

        } else {
            // STANDARD TEXT NOTE
            builder.setContentTitle(displayTitle)
            builder.setContentText(text)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
            
            val copyIntent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_COPY_TEXT
                putExtra(EXTRA_NOTE_TEXT, text)
            }
            builder.addAction(0, "Copy", PendingIntent.getBroadcast(context, (noteId + "_copy").hashCode(), copyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            builder.addAction(0, "Remove", removePendingIntent)
        }

        manager.notify(noteId.hashCode(), builder.build())
    }

    fun showReminderNotification(noteId: String, title: String, text: String, isList: Boolean = false) {
        val displayTitle = if (title.isBlank()) "Reminder" else "Reminder: $title"
        val content = if (isList) "Open to view your checklist items" else text

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            (noteId + "_reminder_open").hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayTitle)
            .setContentText(content.ifBlank { "It's time for your reminder" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.ifBlank { "It's time for your reminder" }))
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .build()

        manager.notify((noteId + "_reminder").hashCode(), notification)
    }

    fun unpinNoteFromNotification(noteId: String) {
        manager.cancel(noteId.hashCode())
    }
}