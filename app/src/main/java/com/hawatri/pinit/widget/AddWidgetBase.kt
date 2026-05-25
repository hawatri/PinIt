package com.hawatri.pinit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.hawatri.pinit.data.NoteDatabase
import com.hawatri.pinit.data.NoteType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Base for the 10 Add-X widget providers (one per note type). Each subclass declares
 * its [noteType] via [WidgetTypeRegistry]; rendering is delegated to [AddWidgetRenderer].
 *
 * Locked notes are filtered out at the picker and refuse to render even if forced.
 */
abstract class AddWidgetBase : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { AddWidgetRenderer.updateOne(context, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { AddWidgetPrefs.clear(context, it) }
    }
}

class AddTextWidget     : AddWidgetBase()
class AddListWidget     : AddWidgetBase()
class AddQrWidget       : AddWidgetBase()
class AddLinkWidget     : AddWidgetBase()
class AddContactWidget  : AddWidgetBase()
class AddLocationWidget : AddWidgetBase()
class AddAppListWidget  : AddWidgetBase()
class AddImageWidget    : AddWidgetBase()
class AddPdfWidget      : AddWidgetBase()
class AddAudioWidget    : AddWidgetBase()

/**
 * Static helpers — broadcast updates and request the launcher pin a widget bound to
 * a chosen note. The right Add-X provider is selected by [noteType] so the launcher's
 * confirmation card shows the matching label and preview.
 */
object AddWidgets {

    fun requestUpdateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        WidgetTypeRegistry.allProviderClasses().forEach { cls ->
            try {
                val ids = manager.getAppWidgetIds(ComponentName(context, cls))
                ids.forEach { AddWidgetRenderer.updateOne(context, it) }
            } catch (e: Exception) { /* ignore */ }
        }
    }

    fun requestPin(context: Context, noteId: String, noteType: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return false
        val manager = AppWidgetManager.getInstance(context) ?: return false
        if (!manager.isRequestPinAppWidgetSupported) return false

        val entry = WidgetTypeRegistry.entryForType(noteType)
            ?: WidgetTypeRegistry.entryForType(NoteType.TEXT)
            ?: return false
        val provider = ComponentName(context, entry.providerClass)

        val callback = Intent(context, AddWidgetPinReceiver::class.java).apply {
            action = AddWidgetPinReceiver.ACTION_PIN_CALLBACK
            putExtra(AddWidgetPinReceiver.EXTRA_NOTE_ID, noteId)
        }
        val pi = PendingIntent.getBroadcast(
            context, noteId.hashCode(), callback,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        return try {
            manager.requestPinAppWidget(provider, null, pi)
        } catch (e: Exception) { false }
    }
}
