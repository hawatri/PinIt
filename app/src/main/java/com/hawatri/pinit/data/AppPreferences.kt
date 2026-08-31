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
    private const val KEY_LAST_SYNC = "last_sync_at"
    private const val KEY_INITIAL_MERGE_DONE = "initial_merge_done"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_MANUAL_ORDER = "manual_order"
    private const val KEY_LAST_SEEN_VERSION_CODE = "last_seen_version_code"
    private const val KEY_LAST_MODIFIED_AT = "last_modified_at"
    private const val KEY_LAST_BACKUP_AT = "last_backup_at"
    private const val KEY_STALE_DIALOG_DISMISSED_AT = "stale_dialog_dismissed_at"
    private const val KEY_STALE_DIALOG_SUPPRESSED_FOREVER = "stale_dialog_suppressed_forever"
    private const val KEY_BACKUP_REMINDERS_ENABLED = "backup_reminders_enabled"
    private const val KEY_LANGUAGE = "language_tag"

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
        prefs(context).edit()
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_LAST_SYNC)
            .remove(KEY_INITIAL_MERGE_DONE)
            .apply()
    }

    fun getLastSyncAt(context: Context): Long = prefs(context).getLong(KEY_LAST_SYNC, 0L)
    fun setLastSyncAt(context: Context, ts: Long) {
        prefs(context).edit().putLong(KEY_LAST_SYNC, ts).apply()
    }

    /** True after the first sign-in merge has run for this account on this device. */
    fun isInitialMergeDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INITIAL_MERGE_DONE, false)

    fun setInitialMergeDone(context: Context, done: Boolean) {
        prefs(context).edit().putBoolean(KEY_INITIAL_MERGE_DONE, done).apply()
    }

    fun isOnboardingDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingDone(context: Context, done: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    /** Comma-separated list of note ids in user-defined manual order. */
    fun getManualOrder(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_MANUAL_ORDER, null) ?: return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun setManualOrder(context: Context, ids: List<String>) {
        prefs(context).edit().putString(KEY_MANUAL_ORDER, ids.joinToString(",")).apply()
    }

    /** versionCode the user has already seen the post-update banner for. -1 = never seen. */
    fun getLastSeenVersionCode(context: Context): Int =
        prefs(context).getInt(KEY_LAST_SEEN_VERSION_CODE, -1)

    fun setLastSeenVersionCode(context: Context, code: Int) {
        prefs(context).edit().putInt(KEY_LAST_SEEN_VERSION_CODE, code).apply()
    }

    /**
     * Wall-clock millis of the most recent local note mutation (insert / update /
     * delete / pin toggle / archive / label edit). Used to decide whether the user
     * has unbacked-up changes vs. [getLastBackupAt]. Restore paths must not bump
     * this — restoring from cloud should leave lastModified == lastBackup.
     */
    fun getLastModifiedAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_MODIFIED_AT, 0L)

    fun setLastModifiedNow(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_MODIFIED_AT, System.currentTimeMillis()).apply()
    }

    /**
     * Wall-clock millis of the most recent successful backup (online OR offline) or
     * restore. Compared against [getLastModifiedAt] to surface "you have unsaved
     * changes" warnings. 0 = never backed up. After a successful restore, set this
     * to match lastModifiedAt so the banner clears.
     */
    fun getLastBackupAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_BACKUP_AT, 0L)

    fun setLastBackupNow(context: Context) {
        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putLong(KEY_LAST_BACKUP_AT, now)
            .putLong(KEY_LAST_MODIFIED_AT, now)
            .apply()
    }

    /** True if the user tapped "Don't ask again" on the stale-backup dialog. */
    fun isStaleDialogSuppressedForever(context: Context): Boolean =
        prefs(context).getBoolean(KEY_STALE_DIALOG_SUPPRESSED_FOREVER, false)

    fun setStaleDialogSuppressedForever(context: Context) {
        prefs(context).edit().putBoolean(KEY_STALE_DIALOG_SUPPRESSED_FOREVER, true).apply()
    }

    /** Wall-clock millis when the user last dismissed the stale-backup dialog. */
    fun getStaleDialogDismissedAt(context: Context): Long =
        prefs(context).getLong(KEY_STALE_DIALOG_DISMISSED_AT, 0L)

    fun setStaleDialogDismissedNow(context: Context) {
        prefs(context).edit().putLong(KEY_STALE_DIALOG_DISMISSED_AT, System.currentTimeMillis()).apply()
    }

    /**
     * Whether the proactive backup nudges (the persistent Home banner and the
     * 14-day stale-backup dialog) are shown. Defaults to true. The user can
     * turn these off in Settings if the constant reminders are unwanted.
     */
    fun isBackupRemindersEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BACKUP_REMINDERS_ENABLED, true)

    fun setBackupRemindersEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BACKUP_REMINDERS_ENABLED, enabled).apply()
    }

    /**
     * BCP-47 tag of the user's chosen in-app language, or "" to follow the system
     * locale. Read through `LocaleHelper`/`AppLanguage` rather than directly so an
     * unknown tag left behind by a downgrade degrades to the system default.
     */
    fun getLanguageTag(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, "") ?: ""

    fun setLanguageTag(context: Context, tag: String) {
        prefs(context).edit().putString(KEY_LANGUAGE, tag).apply()
    }
}
