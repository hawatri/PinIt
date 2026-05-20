package com.hawatri.pinit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.hawatri.pinit.MainActivity
import com.hawatri.pinit.R
import com.hawatri.pinit.data.NoteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class PinItWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        CoroutineScope(Dispatchers.IO).launch {
            val pinnedCount = try {
                NoteDatabase.getDatabase(context).noteDao().getAllNotes().firstOrNull()
                    ?.count { it.isPinned && !it.isArchived } ?: 0
            } catch (e: Exception) { 0 }

            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId, pinnedCount)
            }
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PinItWidget::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, PinItWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int, pinnedCount: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            // Open app on header tap
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_header, openPending)

            // Pinned count label
            views.setTextViewText(R.id.widget_pinned_count, if (pinnedCount > 0) "$pinnedCount pinned" else "")

            // New Note button
            val newNoteIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("WIDGET_ACTION", "new_note")
            }
            val newNotePending = PendingIntent.getActivity(context, 1, newNoteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_btn_note, newNotePending)

            // New List button
            val newListIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("WIDGET_ACTION", "new_list")
            }
            val newListPending = PendingIntent.getActivity(context, 2, newListIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_btn_list, newListPending)

            manager.updateAppWidget(widgetId, views)
        }
    }
}
