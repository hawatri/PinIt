package com.hawatri.pinit.widget

import android.content.ComponentName
import android.content.Context
import com.hawatri.pinit.R
import com.hawatri.pinit.data.NoteType

/**
 * Single source of truth that ties a note type to its dedicated Add-X widget provider,
 * its launcher-facing label, and the icon used in the picker. The 10 thin Add-X
 * provider classes register themselves here implicitly via [providerForType].
 */
object WidgetTypeRegistry {

    data class Entry(
        val type: String,
        val providerClass: Class<out AddWidgetBase>,
        val label: String,
        val iconRes: Int
    )

    val entries: List<Entry> = listOf(
        Entry(NoteType.TEXT,     AddTextWidget::class.java,     "Note",        R.drawable.ic_widget_text),
        Entry(NoteType.LIST,     AddListWidget::class.java,     "Checklist",   R.drawable.ic_check_box),
        Entry(NoteType.QR,       AddQrWidget::class.java,       "QR code",     R.drawable.ic_widget_qr),
        Entry(NoteType.LINK,     AddLinkWidget::class.java,     "Link",        R.drawable.ic_widget_link),
        Entry(NoteType.CONTACT,  AddContactWidget::class.java,  "Contact",     R.drawable.ic_widget_phone),
        Entry(NoteType.LOCATION, AddLocationWidget::class.java, "Location",    R.drawable.ic_widget_navigation),
        Entry(NoteType.APPLIST,  AddAppListWidget::class.java,  "App list",    R.drawable.ic_widget_apps),
        Entry(NoteType.IMAGE,    AddImageWidget::class.java,    "Image",       R.drawable.ic_widget_image),
        Entry(NoteType.PDF,      AddPdfWidget::class.java,      "PDF",         R.drawable.ic_widget_pdf),
        Entry(NoteType.AUDIO,    AddAudioWidget::class.java,    "Audio",       R.drawable.ic_widget_play),
    )

    fun entryForType(type: String): Entry? = entries.firstOrNull { it.type == type }

    fun typeForProvider(context: Context, provider: ComponentName): String? {
        val pkg = context.packageName
        if (provider.packageName != pkg) return null
        return entries.firstOrNull { it.providerClass.name == provider.className }?.type
    }

    fun typeForProviderClass(cls: Class<*>): String? =
        entries.firstOrNull { it.providerClass == cls }?.type

    fun allProviderClasses(): List<Class<out AddWidgetBase>> = entries.map { it.providerClass }
}
