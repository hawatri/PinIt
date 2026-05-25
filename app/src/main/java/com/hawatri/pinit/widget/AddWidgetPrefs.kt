package com.hawatri.pinit.widget

import android.content.Context
import android.content.SharedPreferences

/**
 * Maps each placed Add-X widget instance (appWidgetId) to a noteId. The user picks a
 * note in [AddWidgetConfigActivity] when they drop the widget on the home screen.
 */
object AddWidgetPrefs {
    private const val PREFS = "pinit_add_widget_prefs"
    private const val KEY_NOTE_PREFIX = "widget_note_"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setNoteId(context: Context, widgetId: Int, noteId: String) {
        prefs(context).edit().putString(KEY_NOTE_PREFIX + widgetId, noteId).apply()
    }

    fun getNoteId(context: Context, widgetId: Int): String? =
        prefs(context).getString(KEY_NOTE_PREFIX + widgetId, null)

    fun clear(context: Context, widgetId: Int) {
        prefs(context).edit().remove(KEY_NOTE_PREFIX + widgetId).apply()
    }
}
