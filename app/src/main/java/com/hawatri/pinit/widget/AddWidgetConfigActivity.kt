package com.hawatri.pinit.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.hawatri.pinit.R
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.data.NoteDatabase
import com.hawatri.pinit.data.NoteType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Launched by the launcher when the user drops any Add-X widget on the home screen.
 * The provider class identifies which note type to filter by — the picker only shows
 * matching notes, the title and header icon reflect the type.
 *
 * Locked notes never appear in the picker.
 *
 * Themed via Theme.PinIt.Picker (DayNight) so light/dark mode follow the system.
 */
class AddWidgetConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var filterType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Default result = canceled so the widget is removed if user backs out
        setResult(Activity.RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Determine which type to filter by from the widget's provider component
        filterType = try {
            val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
            val provider: ComponentName? = info?.provider
            if (provider != null) WidgetTypeRegistry.typeForProvider(this, provider) else null
        } catch (e: Exception) { null }

        setContentView(R.layout.add_widget_picker_activity)
        val list = findViewById<ListView>(R.id.picker_list)
        val emptyContainer = findViewById<LinearLayout>(R.id.picker_empty_container)
        val emptyText = findViewById<TextView>(R.id.picker_empty)
        val emptyIcon = findViewById<ImageView>(R.id.picker_empty_icon)
        val title = findViewById<TextView>(R.id.picker_title)
        val subtitle = findViewById<TextView>(R.id.picker_subtitle)
        val headerIcon = findViewById<ImageView>(R.id.picker_header_icon)

        title.text = headerForType(filterType)
        subtitle.text = subtitleForType(filterType)
        emptyText.text = emptyMsgForType(filterType)
        val typeIcon = filterType?.let { WidgetTypeRegistry.entryForType(it)?.iconRes }
            ?: R.drawable.ic_widget_text
        headerIcon.setImageResource(typeIcon)
        emptyIcon.setImageResource(typeIcon)

        CoroutineScope(Dispatchers.IO).launch {
            val notes = try {
                NoteDatabase.getDatabase(this@AddWidgetConfigActivity).noteDao()
                    .getAllNotes().firstOrNull()
                    ?.filter { !it.isArchived && !it.isLocked }
                    ?.filter { filterType == null || it.noteType == filterType || (filterType == NoteType.LIST && it.isList) }
                    ?.sortedByDescending { it.timestamp }
                    ?: emptyList()
            } catch (e: Exception) { emptyList() }

            withContext(Dispatchers.Main) {
                if (notes.isEmpty()) {
                    emptyContainer.visibility = View.VISIBLE
                    list.visibility = View.GONE
                    subtitle.visibility = View.GONE
                } else {
                    emptyContainer.visibility = View.GONE
                    list.visibility = View.VISIBLE
                    subtitle.visibility = View.VISIBLE
                    subtitle.text = "${notes.size} ${if (notes.size == 1) "match" else "matches"}"
                    list.adapter = PickerAdapter(notes) { note ->
                        AddWidgetPrefs.setNoteId(this@AddWidgetConfigActivity, widgetId, note.id)
                        AddWidgetRenderer.updateOne(this@AddWidgetConfigActivity, widgetId)
                        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        setResult(Activity.RESULT_OK, resultValue)
                        finish()
                    }
                }
            }
        }
    }

    private fun headerForType(type: String?): String = when (type) {
        NoteType.TEXT -> "Choose a Note"
        NoteType.LIST -> "Choose a Checklist"
        NoteType.QR -> "Choose a QR code"
        NoteType.LINK -> "Choose a Link"
        NoteType.CONTACT -> "Choose a Contact"
        NoteType.LOCATION -> "Choose a Location"
        NoteType.APPLIST -> "Choose an App list"
        NoteType.IMAGE -> "Choose an Image"
        NoteType.PDF -> "Choose a PDF"
        NoteType.AUDIO -> "Choose an Audio note"
        else -> "Choose a note"
    }

    private fun subtitleForType(type: String?): String = when (type) {
        NoteType.TEXT -> "Pick a text note to display"
        NoteType.LIST -> "Pick a checklist to pin to home"
        NoteType.QR -> "Pick a QR code to display"
        NoteType.LINK -> "Pick a saved link"
        NoteType.CONTACT -> "Pick a contact for one-tap dial"
        NoteType.LOCATION -> "Pick a location for quick navigation"
        NoteType.APPLIST -> "Pick an app list"
        NoteType.IMAGE -> "Pick an image note"
        NoteType.PDF -> "Pick a PDF note"
        NoteType.AUDIO -> "Pick a voice memo"
        else -> "Pick a note to display"
    }

    private fun emptyMsgForType(type: String?): String = when (type) {
        NoteType.TEXT -> "No text notes yet.\nCreate one in PinIt first."
        NoteType.LIST -> "No checklists yet.\nCreate one in PinIt first."
        NoteType.QR -> "No QR codes yet.\nCreate one in PinIt first."
        NoteType.LINK -> "No link notes yet.\nCreate one in PinIt first."
        NoteType.CONTACT -> "No contact notes yet.\nCreate one in PinIt first."
        NoteType.LOCATION -> "No location notes yet.\nCreate one in PinIt first."
        NoteType.APPLIST -> "No app lists yet.\nCreate one in PinIt first."
        NoteType.IMAGE -> "No image notes yet.\nCreate one in PinIt first."
        NoteType.PDF -> "No PDF notes yet.\nCreate one in PinIt first."
        NoteType.AUDIO -> "No audio notes yet.\nCreate one in PinIt first."
        else -> "No notes yet.\nCreate one in PinIt first."
    }

    private class PickerAdapter(
        private val notes: List<Note>,
        private val onClick: (Note) -> Unit
    ) : BaseAdapter() {
        override fun getCount(): Int = notes.size
        override fun getItem(position: Int): Any = notes[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: LayoutInflater.from(parent.context).inflate(R.layout.add_widget_picker_item, parent, false)
            val note = notes[position]
            view.findViewById<ImageView>(R.id.picker_item_icon).setImageResource(typeIconRes(note.noteType))
            view.findViewById<TextView>(R.id.picker_item_title).text = note.title.ifBlank { "Untitled" }
            view.findViewById<TextView>(R.id.picker_item_subtitle).text = typeLabel(note.noteType)
            view.setOnClickListener { onClick(note) }
            return view
        }

        private fun typeIconRes(type: String): Int =
            WidgetTypeRegistry.entryForType(type)?.iconRes ?: R.drawable.ic_widget_text

        private fun typeLabel(type: String): String =
            WidgetTypeRegistry.entryForType(type)?.label ?: "Note"
    }
}
