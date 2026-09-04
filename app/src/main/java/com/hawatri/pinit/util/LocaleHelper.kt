package com.hawatri.pinit.util

import android.content.Context
import android.content.res.Configuration
import com.hawatri.pinit.data.AppPreferences
import java.util.Locale

/**
 * The languages PinIt ships translations for. [tag] is a BCP-47 tag matching the
 * `values-<qualifier>` resource folder; [SYSTEM] carries an empty tag and means
 * "follow whatever the platform picked" (including the Android 13+ per-app
 * language setting).
 *
 * [displayName] is deliberately written in the language itself — a user hunting
 * for their own language in the picker shouldn't have to read English to find it.
 */
enum class AppLanguage(val tag: String, val displayName: String) {
    SYSTEM("", "System default"),
    ENGLISH("en", "English"),
    PORTUGUESE_BR("pt-BR", "Português (Brasil)"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    ITALIAN("it", "Italiano"),
    HINDI("hi", "हिन्दी"),
    ARABIC("ar", "العربية"),
    CHINESE_SIMPLIFIED("zh-CN", "简体中文"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어"),
    RUSSIAN("ru", "Русский"),
    INDONESIAN("id", "Bahasa Indonesia");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: SYSTEM
    }
}

/**
 * Applies the user's in-app language choice to a [Context].
 *
 * Every surface that resolves strings needs to go through here, not just the
 * Activity: notifications, widgets and broadcast receivers each build their own
 * Context and would otherwise silently fall back to the system locale while the
 * app UI is in the chosen one. `Activity.attachBaseContext` covers Compose (which
 * reads `LocalContext.current.resources`); the rest call [wrap] at use time.
 */
object LocaleHelper {

    /** Returns a context whose resources resolve in the user's chosen language. */
    fun wrap(context: Context): Context {
        val language = AppLanguage.fromTag(AppPreferences.getLanguageTag(context))
        if (language == AppLanguage.SYSTEM) return context

        val locale = Locale.forLanguageTag(language.tag)
        val config = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(config)
    }

    /**
     * Resolves a string in the user's chosen language regardless of which kind of
     * Context the caller happens to hold. Use from receivers, services and widget
     * renderers.
     */
    fun getString(context: Context, resId: Int): String =
        wrap(context).getString(resId)

    fun getString(context: Context, resId: Int, vararg formatArgs: Any): String =
        wrap(context).getString(resId, *formatArgs)
}
