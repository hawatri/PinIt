package com.hawatri.pinit.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.google.gson.Gson
import com.hawatri.pinit.R
import com.hawatri.pinit.data.NoteDatabase
import com.hawatri.pinit.data.NoteType
import com.hawatri.pinit.ui.ChecklistItemData
import kotlinx.coroutines.runBlocking

/**
 * Backs the ListView inside add_widget.xml. Returns one RemoteViews per row:
 *   LIST notes  → checkbox icon + item text (clickable to toggle)
 *   TEXT notes  → bullet line per logical line of the body
 */
class AddWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return Factory(applicationContext, widgetId)
    }

    private class Factory(
        private val context: Context,
        private val widgetId: Int
    ) : RemoteViewsFactory {

        private data class Row(val text: String, val checked: Boolean?, val index: Int)

        private var rows: List<Row> = emptyList()

        override fun onCreate() {}

        override fun onDataSetChanged() {
            val nid = AddWidgetPrefs.getNoteId(context, widgetId) ?: run {
                rows = emptyList(); return
            }
            val note = runBlocking {
                try { NoteDatabase.getDatabase(context).noteDao().getNoteById(nid) }
                catch (e: Exception) { null }
            }
            if (note == null || note.isLocked || note.isArchived) {
                rows = emptyList()
                return
            }

            val isList = note.noteType == NoteType.LIST || note.isList
            rows = if (isList) {
                try {
                    Gson().fromJson(note.text, Array<ChecklistItemData>::class.java)
                        .toList()
                        .mapIndexed { i, item -> Row(item.text, item.isChecked, i) }
                } catch (e: Exception) { emptyList() }
            } else {
                note.text.split("\n").mapIndexedNotNull { i, line ->
                    val trimmed = line.trimEnd()
                    if (trimmed.isBlank()) null else Row(trimmed, null, i)
                }
            }
        }

        override fun onDestroy() { rows = emptyList() }
        override fun getCount(): Int = rows.size

        override fun getViewAt(position: Int): RemoteViews {
            val row = rows.getOrNull(position) ?: return loading()
            val views = RemoteViews(context.packageName, R.layout.add_widget_item)
            views.setTextViewText(R.id.add_widget_item_text, row.text)
            if (row.checked != null) {
                views.setViewVisibility(R.id.add_widget_item_check, android.view.View.VISIBLE)
                views.setImageViewResource(
                    R.id.add_widget_item_check,
                    if (row.checked) R.drawable.ic_check_box else R.drawable.ic_check_box_outline
                )
                val fillIn = Intent().apply {
                    putExtra(AddWidgetRenderer.EXTRA_ITEM_INDEX, row.index)
                }
                views.setOnClickFillInIntent(R.id.add_widget_item_root, fillIn)
            } else {
                views.setViewVisibility(R.id.add_widget_item_check, android.view.View.GONE)
                views.setOnClickFillInIntent(R.id.add_widget_item_root, Intent())
            }
            return views
        }

        private fun loading() = RemoteViews(context.packageName, R.layout.add_widget_item).apply {
            setTextViewText(R.id.add_widget_item_text, "")
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = true
    }
}
