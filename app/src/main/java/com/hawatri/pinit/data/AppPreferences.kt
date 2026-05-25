package com.hawatri.pinit.data

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class BackupMode { OFF, OFFLINE, ONLINE }

object AppPreferences {
    private const val PREFS = "pinit_prefs"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_BACKUP = "backup_mode"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_EMAIL = "user_email"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs(context).getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)

    fun setThemeMode(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME, mode.name).apply()
    }

    fun getBackupMode(context: Context): BackupMode =
        runCatching { BackupMode.valueOf(prefs(context).getString(KEY_BACKUP, BackupMode.OFF.name) ?: BackupMode.OFF.name) }
            .getOrDefault(BackupMode.OFF)

    fun setBackupMode(context: Context, mode: BackupMode) {
        prefs(context).edit().putString(KEY_BACKUP, mode.name).apply()
    }

    fun getUserName(context: Context): String? = prefs(context).getString(KEY_USER_NAME, null)
    fun getUserEmail(context: Context): String? = prefs(context).getString(KEY_USER_EMAIL, null)

    fun setUser(context: Context, name: String?, email: String?) {
        prefs(context).edit().apply {
            if (name == null) remove(KEY_USER_NAME) else putString(KEY_USER_NAME, name)
            if (email == null) remove(KEY_USER_EMAIL) else putString(KEY_USER_EMAIL, email)
        }.apply()
    }

    fun signOut(context: Context) {
        prefs(context).edit().remove(KEY_USER_NAME).remove(KEY_USER_EMAIL).apply()
    }
}
