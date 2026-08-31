package com.hawatri.pinit.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
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
import com.hawatri.pinit.util.LocaleHelper
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

    /** Applies the in-app language, same as MainActivity. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

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
                    subtitle.text = resources.getQuantityString(
                        R.plurals.plural_matches, notes.size, notes.size
                    )
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

    private fun headerForType(type: String?): String = getString(
        when (type) {
            NoteType.TEXT -> R.string.picker_header_text
            NoteType.LIST -> R.string.picker_header_list
            NoteType.QR -> R.string.picker_header_qr
            NoteType.LINK -> R.string.picker_header_link
            NoteType.CONTACT -> R.string.picker_header_contact
            NoteType.LOCATION -> R.string.picker_header_location
            NoteType.APPLIST -> R.string.picker_header_applist
            NoteType.IMAGE -> R.string.picker_header_image
            NoteType.PDF -> R.string.picker_header_pdf
            NoteType.AUDIO -> R.string.picker_header_audio
            else -> R.string.picker_header_default
        }
    )

    private fun subtitleForType(type: String?): String = getString(
        when (type) {
            NoteType.TEXT -> R.string.picker_sub_text
            NoteType.LIST -> R.string.picker_sub_list
            NoteType.QR -> R.string.picker_sub_qr
            NoteType.LINK -> R.string.picker_sub_link
            NoteType.CONTACT -> R.string.picker_sub_contact
            NoteType.LOCATION -> R.string.picker_sub_location
            NoteType.APPLIST -> R.string.picker_sub_applist
            NoteType.IMAGE -> R.string.picker_sub_image
            NoteType.PDF -> R.string.picker_sub_pdf
            NoteType.AUDIO -> R.string.picker_sub_audio
            else -> R.string.picker_sub_default
        }
    )

    private fun emptyMsgForType(type: String?): String = getString(
        when (type) {
            NoteType.TEXT -> R.string.picker_empty_text
            NoteType.LIST -> R.string.picker_empty_list
            NoteType.QR -> R.string.picker_empty_qr
            NoteType.LINK -> R.string.picker_empty_link
            NoteType.CONTACT -> R.string.picker_empty_contact
            NoteType.LOCATION -> R.string.picker_empty_location
            NoteType.APPLIST -> R.string.picker_empty_applist
            NoteType.IMAGE -> R.string.picker_empty_image
            NoteType.PDF -> R.string.picker_empty_pdf
            NoteType.AUDIO -> R.string.picker_empty_audio
            else -> R.string.picker_empty_default
        }
    )

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
            view.findViewById<TextView>(R.id.picker_item_title).text =
                note.title.ifBlank { view.context.getString(R.string.untitled) }
            view.findViewById<TextView>(R.id.picker_item_subtitle).text =
                typeLabel(view.context, note.noteType)
            view.setOnClickListener { onClick(note) }
            return view
        }

        private fun typeIconRes(type: String): Int =
            WidgetTypeRegistry.entryForType(type)?.iconRes ?: R.drawable.ic_widget_text

        private fun typeLabel(context: Context, type: String): String =
            WidgetTypeRegistry.entryForType(type)
                ?.let { context.getString(it.labelRes) }
                ?: context.getString(R.string.type_note)
    }
}
