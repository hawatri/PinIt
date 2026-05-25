package com.hawatri.pinit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.hawatri.pinit.MainActivity
import com.hawatri.pinit.R
import com.hawatri.pinit.data.NoteType

/**
 * Base for the 10 Create-X widget providers. Each subclass is a 1x1 shortcut tile
 * that opens MainActivity with a WIDGET_ACTION extra so PinItApp routes to the right
 * "new note of type X" screen. Tile shows a type icon with a small "+" badge.
 */
abstract class CreateWidgetBase : AppWidgetProvider() {

    abstract val noteType: String
    abstract val widgetAction: String
    abstract val label: String

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> render(context, manager, id) }
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.create_widget)
        val iconRes = WidgetTypeRegistry.entryForType(noteType)?.iconRes ?: R.mipmap.ic_launcher
        views.setImageViewResource(R.id.create_widget_icon, iconRes)
        views.setTextViewText(R.id.create_widget_label, label)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("WIDGET_ACTION", widgetAction)
        }
        val pi = PendingIntent.getActivity(
            context, ("${widgetAction}_${widgetId}").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.create_widget_root, pi)

        try {
            manager.updateAppWidget(widgetId, views)
        } catch (e: Exception) { /* ignore */ }
    }
}

class CreateTextWidget : CreateWidgetBase() {
    override val noteType = NoteType.TEXT
    override val widgetAction = "new_note"
    override val label = "Note"
}
class CreateListWidget : CreateWidgetBase() {
    override val noteType = NoteType.LIST
    override val widgetAction = "new_list"
    override val label = "List"
}
class CreateQrWidget : CreateWidgetBase() {
    override val noteType = NoteType.QR
    override val widgetAction = "new_qr"
    override val label = "QR"
}
class CreateLinkWidget : CreateWidgetBase() {
    override val noteType = NoteType.LINK
    override val widgetAction = "new_link"
    override val label = "Link"
}
class CreateContactWidget : CreateWidgetBase() {
    override val noteType = NoteType.CONTACT
    override val widgetAction = "new_contact"
    override val label = "Contact"
}
class CreateLocationWidget : CreateWidgetBase() {
    override val noteType = NoteType.LOCATION
    override val widgetAction = "new_location"
    override val label = "Location"
}
class CreateAppListWidget : CreateWidgetBase() {
    override val noteType = NoteType.APPLIST
    override val widgetAction = "new_app_list"
    override val label = "App list"
}
class CreateImageWidget : CreateWidgetBase() {
    override val noteType = NoteType.IMAGE
    override val widgetAction = "new_image"
    override val label = "Image"
}
class CreatePdfWidget : CreateWidgetBase() {
    override val noteType = NoteType.PDF
    override val widgetAction = "new_pdf"
    override val label = "PDF"
}
class CreateAudioWidget : CreateWidgetBase() {
    override val noteType = NoteType.AUDIO
    override val widgetAction = "new_audio"
    override val label = "Audio"
}
