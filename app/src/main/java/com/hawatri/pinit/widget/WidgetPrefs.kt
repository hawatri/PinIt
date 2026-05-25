package com.hawatri.pinit.widget

import android.content.Context
import android.content.SharedPreferences

/**
 * Maps each placed widget instance (appWidgetId) to a noteId. The user picks a note
 * in WidgetPickerActivity when they drop the widget on the home screen.
 */
object WidgetPrefs {
    private const val PREFS = "pinit_widget_prefs"
    private const val KEY_PREFIX = "widget_note_"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setNoteId(context: Context, widgetId: Int, noteId: String) {
        prefs(context).edit().putString(KEY_PREFIX + widgetId, noteId).apply()
    }

    fun getNoteId(context: Context, widgetId: Int): String? =
        prefs(context).getString(KEY_PREFIX + widgetId, null)

    fun clear(context: Context, widgetId: Int) {
        prefs(context).edit().remove(KEY_PREFIX + widgetId).apply()
    }

    fun allBindings(context: Context): Map<Int, String> {
        val all = prefs(context).all
        val out = mutableMapOf<Int, String>()
        all.forEach { (k, v) ->
            if (k.startsWith(KEY_PREFIX) && v is String) {
                k.removePrefix(KEY_PREFIX).toIntOrNull()?.let { out[it] = v }
            }
        }
        return out
    }
}
