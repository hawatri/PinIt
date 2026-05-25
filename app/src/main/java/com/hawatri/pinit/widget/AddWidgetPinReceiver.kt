package com.hawatri.pinit.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Success callback for [AppWidgetManager.requestPinAppWidget]. Fires when the launcher
 * confirms the user dropped an Add-X widget on the home screen via the long-press flow.
 *
 * The intent carries:
 *   - EXTRA_APPWIDGET_ID  — filled in by the launcher on success
 *   - EXTRA_NOTE_ID       — the note this new widget was requested for
 *
 * We persist the binding immediately and trigger a render so the widget shows real
 * content right after it lands on the home screen — no configure activity, no picker.
 */
class AddWidgetPinReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_PIN_CALLBACK = "com.hawatri.pinit.widget.ADD_PIN_CALLBACK"
        const val EXTRA_NOTE_ID = "pin_note_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PIN_CALLBACK) return
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: return
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        AddWidgetPrefs.setNoteId(context, widgetId, noteId)
        AddWidgetRenderer.updateOne(context, widgetId)
    }
}
