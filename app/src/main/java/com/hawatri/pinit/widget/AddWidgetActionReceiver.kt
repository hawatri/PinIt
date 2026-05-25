package com.hawatri.pinit.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.hawatri.pinit.data.NoteDatabase
import com.hawatri.pinit.ui.ChecklistItemData
import com.hawatri.pinit.util.AudioPlayback
import com.hawatri.pinit.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles broadcast actions fired from interactive Add-X widget elements:
 *   ACTION_TOGGLE_LIST_ITEM — flip a checklist item, persist, refresh widget
 *   ACTION_TOGGLE_AUDIO     — start/stop playback via the shared AudioPlayback singleton
 */
class AddWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AddWidgetRenderer.EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val noteId = intent.getStringExtra(AddWidgetRenderer.EXTRA_NOTE_ID) ?: return

        when (intent.action) {
            AddWidgetRenderer.ACTION_TOGGLE_LIST_ITEM -> {
                val index = intent.getIntExtra(AddWidgetRenderer.EXTRA_ITEM_INDEX, -1)
                if (index < 0) return
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val dao = NoteDatabase.getDatabase(context).noteDao()
                        val note = dao.getNoteById(noteId) ?: return@launch
                        val gson = Gson()
                        val items = try {
                            gson.fromJson(note.text, Array<ChecklistItemData>::class.java).toMutableList()
                        } catch (e: Exception) { return@launch }
                        if (index !in items.indices) return@launch
                        items[index] = items[index].copy(isChecked = !items[index].isChecked)
                        val newText = gson.toJson(items)
                        dao.updateNote(note.copy(text = newText))
                        if (note.isPinned) {
                            NotificationHelper(context).pinNoteToNotification(
                                note.id, note.title, newText, isList = true, noteType = note.noteType
                            )
                        }
                    } finally {
                        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            AddWidgetRenderer.updateOne(context, widgetId)
                        } else {
                            AddWidgets.requestUpdateAll(context)
                        }
                        pendingResult.finish()
                    }
                }
            }
            AddWidgetRenderer.ACTION_TOGGLE_AUDIO -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val dao = NoteDatabase.getDatabase(context).noteDao()
                        val note = dao.getNoteById(noteId) ?: return@launch
                        val data = try {
                            Gson().fromJson(note.text, com.hawatri.pinit.ui.AudioNoteData::class.java)
                        } catch (e: Exception) { null }
                        val path = data?.path
                        if (!path.isNullOrBlank()) {
                            AudioPlayback.toggle(context, noteId, path)
                        }
                    } finally {
                        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            AddWidgetRenderer.updateOne(context, widgetId)
                        } else {
                            AddWidgets.requestUpdateAll(context)
                        }
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
