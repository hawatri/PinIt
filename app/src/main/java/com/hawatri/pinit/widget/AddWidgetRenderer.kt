package com.hawatri.pinit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.RemoteViews
import com.google.gson.Gson
import com.hawatri.pinit.MainActivity
import com.hawatri.pinit.R
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.data.NoteDatabase
import com.hawatri.pinit.data.NoteType
import com.hawatri.pinit.ui.AudioNoteData
import com.hawatri.pinit.ui.ContactNoteData
import com.hawatri.pinit.ui.LinkNoteData
import com.hawatri.pinit.ui.LocationNoteData
import com.hawatri.pinit.util.AudioPlayback
import com.hawatri.pinit.util.ImageUriUtils
import com.hawatri.pinit.util.PdfUtils
import com.hawatri.pinit.util.QrUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Renders an Add-X widget for whichever note is currently bound to it.
 * One layout (note_widget.xml-style) is used for all 10 types but populated differently
 * per [Note.noteType]. The provider class itself is just an empty marker — the real
 * work happens here.
 *
 * Important: the provider's note type is treated as a hint only. We trust whatever
 * type the bound note actually has, so a launcher restoring an Add-Text widget on a
 * note that was edited into a different type still renders correctly.
 */
object AddWidgetRenderer {

    const val ACTION_TOGGLE_LIST_ITEM = "com.hawatri.pinit.widget.add.TOGGLE_LIST_ITEM"
    const val ACTION_TOGGLE_AUDIO     = "com.hawatri.pinit.widget.add.TOGGLE_AUDIO"

    const val EXTRA_WIDGET_ID  = "widget_id"
    const val EXTRA_NOTE_ID    = "note_id"
    const val EXTRA_ITEM_INDEX = "item_index"

    fun updateOne(context: Context, widgetId: Int) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val noteId = AddWidgetPrefs.getNoteId(context, widgetId)
        if (noteId == null) {
            manager.updateAppWidget(widgetId, emptyView(context, widgetId, "Tap to choose a note"))
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val note = try {
                NoteDatabase.getDatabase(context).noteDao().getNoteById(noteId)
            } catch (e: Exception) { null }

            val views = try {
                when {
                    note == null      -> emptyView(context, widgetId, "Note no longer exists")
                    note.isLocked     -> emptyView(context, widgetId, "Locked notes can't be shown")
                    note.isArchived   -> emptyView(context, widgetId, "Note is archived")
                    else              -> render(context, widgetId, note)
                }
            } catch (e: Exception) {
                emptyView(context, widgetId, "Tap to choose a note")
            }
            try {
                manager.updateAppWidget(widgetId, views)
                if (note != null && (note.noteType == NoteType.LIST || note.isList || note.noteType == NoteType.TEXT)) {
                    manager.notifyAppWidgetViewDataChanged(widgetId, R.id.add_widget_list)
                }
            } catch (e: Exception) {
                manager.updateAppWidget(widgetId, emptyView(context, widgetId, "Tap to choose a note"))
            }
        }
    }

    private fun emptyView(context: Context, widgetId: Int, msg: String): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.add_widget)
        views.setTextViewText(R.id.add_widget_title, "PinIt")
        views.setViewVisibility(R.id.add_widget_list, android.view.View.GONE)
        views.setViewVisibility(R.id.add_widget_panel, android.view.View.GONE)
        views.setViewVisibility(R.id.add_widget_action_btn, android.view.View.GONE)
        views.setViewVisibility(R.id.add_widget_empty, android.view.View.VISIBLE)
        views.setTextViewText(R.id.add_widget_empty_text, msg)
        // Tapping the empty widget reopens the picker
        val configIntent = Intent(context, AddWidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, widgetId, configIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.add_widget_root, pi)
        return views
    }

    private fun render(context: Context, widgetId: Int, note: Note): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.add_widget)
        views.setTextViewText(R.id.add_widget_title, note.title.ifBlank { typeLabel(note.noteType) })
        views.setImageViewResource(R.id.add_widget_type_icon, typeIcon(note.noteType))
        views.setViewVisibility(R.id.add_widget_empty, android.view.View.GONE)

        // Tapping header opens the note in the app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("WIDGET_OPEN_NOTE_ID", note.id)
        }
        val openPi = PendingIntent.getActivity(
            context, ("open_${note.id}").hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.add_widget_header, openPi)
        views.setOnClickPendingIntent(R.id.add_widget_open_btn, openPi)

        when (note.noteType) {
            NoteType.LIST -> renderList(context, views, widgetId, note)
            NoteType.AUDIO -> renderAudio(context, views, widgetId, note)
            NoteType.LINK -> renderLink(context, views, widgetId, note)
            NoteType.CONTACT -> renderContact(context, views, widgetId, note)
            NoteType.LOCATION -> renderLocation(context, views, widgetId, note)
            NoteType.QR -> renderQr(context, views, widgetId, note)
            NoteType.IMAGE -> renderImage(context, views, widgetId, note)
            NoteType.PDF -> renderPdf(context, views, widgetId, note)
            NoteType.APPLIST -> renderAppList(context, views, note)
            else -> renderText(context, views, widgetId, note)
        }
        return views
    }

    // --- Type-specific renderers --------------------------------------------------

    private fun renderText(context: Context, views: RemoteViews, widgetId: Int, note: Note) {
        views.setViewVisibility(R.id.add_widget_list, android.view.View.VISIBLE)
        views.setViewVisibility(R.id.add_widget_panel, android.view.View.GONE)
        views.setViewVisibility(R.id.add_widget_action_btn, android.view.View.GONE)

        val intent = Intent(context, AddWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.add_widget_list, intent)

        // setPendingIntentTemplate REQUIRES MUTABLE on Android 12+ — IMMUTABLE crashes
        // the host with "Couldn't load widget".
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("WIDGET_OPEN_NOTE_ID", note.id)
        }
        val template = PendingIntent.getActivity(
            context, ("text_open_${widgetId}").hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setPendingIntentTemplate(R.id.add_widget_list, template)
    }

    private fun renderList(context: Context, views: RemoteViews, widgetId: Int, note: Note) {
        views.setViewVisibility(R.id.add_widget_list, android.view.View.VISIBLE)
        views.setViewVisibility(R.id.add_widget_panel, android.view.View.GONE)
        views.setViewVisibility(R.id.add_widget_action_btn, android.view.View.GONE)

        val intent = Intent(context, AddWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.add_widget_list, intent)

        val templateIntent = Intent(context, AddWidgetActionReceiver::class.java).apply {
            action = ACTION_TOGGLE_LIST_ITEM
            putExtra(EXTRA_WIDGET_ID, widgetId)
            putExtra(EXTRA_NOTE_ID, note.id)
        }
        val template = PendingIntent.getBroadcast(
            context, ("list_toggle_${widgetId}").hashCode(), templateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setPendingIntentTemplate(R.id.add_widget_list, template)
    }

    private fun renderAudio(context: Context, views: RemoteViews, widgetId: Int, note: Note) {
        val data = try { Gson().fromJson(note.text, AudioNoteData::class.java) } catch (e: Exception) { null }
        views.setViewVisibility(R.id.add_widget_list, android.view.View.GONE)
        views.setViewVisibility(R.id.add_widget_panel, android.view.View.VISIBLE)
        views.setViewVisibility(R.id.add_widget_panel_image, android.view.View.GONE)
        val durMs = data?.durationMs ?: 0L
        val durLabel = if (durMs > 0) "%d:%02d".format(durMs / 60000, (durMs / 1000) % 60) else "Recording"
        views.setTextViewText(R.id.add_widget_panel_text, durLabel)

        val isPlaying = AudioPlayback.playingNoteId.value == note.id
        views.setViewVisibility(R.id.add_widget_action_btn, android.view.View.VISIBLE)
        views.setImageViewResource(R.id.add_widget_action_icon, if (isPlaying) R.drawable.ic_widget_stop else R.drawable.ic_widget_play)
        views.setTextViewText(R.id.add_widget_action_label, if (isPlaying) "Stop" else "Play")

        val toggle = Intent(context, AddWidgetActionReceiver::class.java).apply {
            action = ACTION_TOGGLE_AUDIO
            putExtra(EXTRA_WIDGET_ID, widgetId)
            putExtra(EXTRA_NOTE_ID, note.id)
        }
        val pi = PendingIntent.getBroadcast(
            context, ("audio_${widgetId}").hashCode(), toggle,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.add_widget_action_btn, pi)
    }

    private fun renderLink(context: Context, views: RemoteViews, widgetId: Int, note: Note) {
        val data = try { Gson().fromJson(note.text, LinkNoteData::class.java) } catch (e: Exception) { null }
        views.setViewVisibility(R.id.add_widget_list, android.view.View.GONE)
        views.setViewVisibility(R.id.add_widget_panel, android.view.View.VISIBLE)
        views.setViewVisibility(R.id.add_widget_panel_image, android.view.View.GONE)
        views.setTextViewText(R.id.add_widget_panel_text, data?.url ?: note.text)

        views.setViewVisibility(R.id.add_widget_action_btn, android.view.View.VISIBLE)
        views.setImageViewResource(R.id.add_widget_action_icon, R.drawable.ic_widget_open)
        views.setTextViewText(R.id.add_widget_action_label, "Browse")

        val url = data?.url ?: note.text
        try {
            val open = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pi = PendingIntent.getActivity(
                context, ("link_${widgetId}").hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.add_widget_action_btn, pi)
        } catch (e: Exception) { /* bad url, leave button without action */ }
    }

    private fun renderContact(context: Context, views: RemoteViews, widgetId: Int, note: Note) {
        val data = try { Gson().fromJson(note.text, ContactNoteData::class.java) } catch (e: Exception) { null }
        views.setViewVisibility(R.id.add_widget_list, android.view.View.GONE)
        views.setViewVisibility(R.id.add_widget_panel, android.view.View.VISIBLE)
        views.setViewVisibility(R.id.add_widget_panel_image, android.view.View.GONE)
        views.setTextViewText(R.id.add_widget_panel_text, data?.phone ?: note.text)

        views.setViewVisibility(R.id.add_widget_action_btn, android.view.View.VISIBLE)
        views.setImageViewResource(R.id.add_widget_action_icon, R.drawable.ic_widget_phone)
        views.setTextViewText(R.id.add_widget_action_label, "Call")

        val phone = data?.phone ?: ""
        if (phone.isNotBlank()) {
            val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pi = PendingIntent.getActivity(
                context, ("contact_${widgetId}").hashCode(), dial,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.add_widget_action_btn, pi)
        }
    }

    private fun renderLocation(context: Context, views: RemoteViews, widgetId: Int, note: Note) {
        val data = try { Gson().fromJson(note.text, LocationNoteData::class.java) } catch (e: Exception) { null }
        views.setViewVisibility(R.id.add_widget_list, android.view.View.GONE)
        views.setViewVisibility(R.id.add_widget_panel, android.view.View.VISIBLE)
        views.setViewVisibility(R.id.add_widget_panel_image, android.view.View.GONE)
        views.setTextViewText(R.id.add_widget_panel_text, data?.address?.ifBlank { data.name } ?: "Location")

        views.setViewVisibility(R.id.add_widget_action_btn, android.view.View.VISIBLE)
        views.setImageViewResource(R.id.add_widget_action_icon, R.drawable.ic_widget_navigation)
        views.setTextViewText(R.id.add_widget_action_label, "Navigate")

        if (data?.lat != null && data.lng != null) {
            val geoUri = Uri.parse("geo:${data.lat},${data.lng}?q=${data.lat},${data.lng}(${Uri.encode(data.name.ifBlank { "Location" })})")
            val nav = Intent(Intent.ACTION_VIEW, geoUri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            val pi = PendingIntent.getActivity(
                context, ("loc_${widgetId}").hashCode(), nav,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.add_widget_action_btn, pi)
        }
    }

    private fun renderQr(context: Context, views: RemoteViews, widgetId: Int, note: Note) {
        val bm: Bitmap? = try { QrUtils.generateQrBitmap(note.text, 384) } catch (e: Exception) { null }
        views.setViewVisibility(R.id.add_widget_list, android.view.View.GONE)
        views.setViewVisibility(R.id.add_widget_panel, android.view.View.VISIBLE)
        if (bm != null) {
            views.setViewVisibility(R.id.add_widget_panel_image, android.view.View.VISIBLE)
            views.setImageViewBitmap(R.id.add_widget_panel_image, bm)
        } else {
            views.setViewVisibility(R.id.add_widget_panel_image, android.view.View.GONE)
        }
        views.setTextViewText(R.id.add_widget_panel_text, note.text)

        val payload = note.text.trim()
        val uri = when {
            payload.startsWith("http://", true) || payload.startsWith("https://", true) -> Uri.parse(payload)
            payload.contains("://") -> Uri.parse(payload)
            else -> null
        }
        if (uri != null) {
            views.setViewVisibility(R.id.add_widget_action_btn, android.view.View.VISIBLE)
            views.setImageViewResource(R.id.add_widget_action_icon, R.drawable.ic_widget_open)
            views.setTextViewText(R.id.add_widget_action_label, "Open")
            val open = Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            val pi = PendingIntent.getActivity(
                context, ("qr_${widgetId}").hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.add_widget_action_btn, pi)
        } else {
            views.setViewVisibility(R.id.add_widget_action_btn, android.view.View.GONE)
        }
    }

    private fun renderImage(context: Context, views: RemoteViews, widgetId: Int, note: Note) {
        val bm: Bitmap? = try { ImageUriUtils.decodeBitmap(context, note.text, 1024) } catch (e: Exception) { null }
        views.setViewVisibility(R.id.add_widget_list, android.view.View.GONE)
        views.setViewVisibility(R.id.add_widget_panel, android.view.View.VISIBLE)
        if (bm != null) {
            views.setViewVisibility(R.id.add_widget_panel_image, android.view.View.VISIBLE)
            views.setImageViewBitmap(R.id.add_widget_panel_image, bm)
            views.setTextViewText(R.id.add_widget_panel_text, "")
        } else {
            views.setViewVisibility(R.id.add_widget_panel_image, android.view.View.GONE)
            views.setTextViewText(R.id.add_widget_panel_text, "Image unavailable")
        }
        views.setViewVisibility(R.id.add_widget_action_btn, android.view.View.VISIBLE)
        views.setImageViewResource(R.id.add_widget_action_icon, R.drawable.ic_widget_open)
        views.setTextViewText(R.id.add_widget_action_label, "Open")

        try {
            val uri = Uri.parse(note.text)
            val open = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/*")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            val pi = PendingIntent.getActivity(
                context, ("img_${widgetId}").hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.add_widget_action_btn, pi)
        } catch (e: Exception) { /* ignore */ }
    }

    private fun renderPdf(context: Context, views: RemoteViews, widgetId: Int, note: Note) {
        val bm: Bitmap? = try {
            val uri = Uri.parse(note.text)
            PdfUtils.renderFirstPage(context, uri, 1024, 1024)
        } catch (e: Exception) { null }

        views.setViewVisibility(R.id.add_widget_list, android.view.View.GONE)
        views.setViewVisibility(R.id.add_widget_panel, android.view.View.VISIBLE)
        if (bm != null) {
            views.setViewVisibility(R.id.add_widget_panel_image, android.view.View.VISIBLE)
            views.setImageViewBitmap(R.id.add_widget_panel_image, bm)
            views.setTextViewText(R.id.add_widget_panel_text, "")
        } else {
            views.setViewVisibility(R.id.add_widget_panel_image, android.view.View.GONE)
            views.setTextViewText(R.id.add_widget_panel_text, "PDF Document")
        }
        views.setViewVisibility(R.id.add_widget_action_btn, android.view.View.VISIBLE)
        views.setImageViewResource(R.id.add_widget_action_icon, R.drawable.ic_widget_open)
        views.setTextViewText(R.id.add_widget_action_label, "Open")

        try {
            val uri = Uri.parse(note.text)
            val open = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            val pi = PendingIntent.getActivity(
                context, ("pdf_${widgetId}").hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.add_widget_action_btn, pi)
        } catch (e: Exception) { /* ignore */ }
    }

    private fun renderAppList(context: Context, views: RemoteViews, note: Note) {
        views.setViewVisibility(R.id.add_widget_list, android.view.View.GONE)
        views.setViewVisibility(R.id.add_widget_panel, android.view.View.VISIBLE)
        views.setViewVisibility(R.id.add_widget_panel_image, android.view.View.GONE)
        views.setTextViewText(R.id.add_widget_panel_text, "Tap to open the app list")
        views.setViewVisibility(R.id.add_widget_action_btn, android.view.View.GONE)
    }

    private fun typeIcon(type: String): Int = when (type) {
        NoteType.LIST -> R.drawable.ic_check_box
        NoteType.AUDIO -> R.drawable.ic_widget_play
        NoteType.LINK -> R.drawable.ic_widget_link
        NoteType.CONTACT -> R.drawable.ic_widget_phone
        NoteType.LOCATION -> R.drawable.ic_widget_navigation
        NoteType.QR -> R.drawable.ic_widget_qr
        NoteType.IMAGE -> R.drawable.ic_widget_image
        NoteType.PDF -> R.drawable.ic_widget_pdf
        NoteType.APPLIST -> R.drawable.ic_widget_apps
        NoteType.TEXT -> R.drawable.ic_widget_text
        else -> R.mipmap.ic_launcher
    }

    private fun typeLabel(type: String): String = when (type) {
        NoteType.LIST -> "Checklist"
        NoteType.AUDIO -> "Recording"
        NoteType.LINK -> "Link"
        NoteType.CONTACT -> "Contact"
        NoteType.LOCATION -> "Location"
        NoteType.QR -> "QR"
        NoteType.IMAGE -> "Image"
        NoteType.PDF -> "PDF"
        NoteType.APPLIST -> "Apps"
        else -> "Note"
    }
}
