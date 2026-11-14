package com.hawatri.pinit.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.google.gson.JsonParser
import com.hawatri.pinit.R
import com.hawatri.pinit.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Finds out whether a newer PinIt is available and where the user should get it.
 *
 * Play policy says a Play-installed app may only update through Play, so the
 * source depends on who installed this copy:
 *  - **Play Store** → Play In-App Updates tells us if Play has a newer build for
 *    this device; the action opens the Play listing.
 *  - **Anything else** (GitHub APK, sideload) → the latest GitHub release tag is
 *    compared with our versionName; the action opens that release page.
 *
 * Only the app's own version is ever sent anywhere (GitHub sees a plain
 * unauthenticated GET; Play talks to the Play Store app on the device).
 */
object UpdateChecker {

    /** [id] is stable per release so we notify / show the banner once per version. */
    data class Update(val id: String, val versionName: String?, val openUrl: String)

    private const val GITHUB_LATEST = "https://api.github.com/repos/hawatri/PinIt/releases/latest"
    private const val CHANNEL_ID = "pinit_updates"
    private const val NOTIFICATION_ID = -9997
    private const val WORK_NAME = "pinit_update_check"

    fun isPlayInstall(context: Context): Boolean {
        val installer = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (e: Exception) { null }
        return installer == "com.android.vending"
    }

    /** Returns the available update, or null when up to date / offline / anything fails. */
    suspend fun check(context: Context): Update? = try {
        if (isPlayInstall(context)) checkPlay(context) else checkGitHub(context)
    } catch (e: Exception) {
        null
    }

    private suspend fun checkPlay(context: Context): Update? {
        val info = AppUpdateManagerFactory.create(context).requestAppUpdateInfo()
        if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return null
        return Update(
            id = "play:${info.availableVersionCode()}",
            versionName = null, // Play only exposes the versionCode
            openUrl = "https://play.google.com/store/apps/details?id=${context.packageName}"
        )
    }

    private suspend fun checkGitHub(context: Context): Update? = withContext(Dispatchers.IO) {
        val conn = (URL(GITHUB_LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "PinIt-Android")
        }
        try {
            if (conn.responseCode != 200) return@withContext null
            val json = conn.inputStream.bufferedReader().use { JsonParser.parseString(it.readText()).asJsonObject }
            val tag = json.get("tag_name")?.asString ?: return@withContext null
            val url = json.get("html_url")?.asString ?: "https://github.com/hawatri/PinIt/releases/latest"
            val current = currentVersionName(context) ?: return@withContext null
            if (compareVersions(tag, current) <= 0) return@withContext null
            Update(id = "gh:$tag", versionName = tag.removePrefix("v").removePrefix("V"), openUrl = url)
        } finally {
            conn.disconnect()
        }
    }

    private fun currentVersionName(context: Context): String? = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (e: Exception) { null }

    /**
     * Numeric compare of dotted versions ("v1.4.1" vs "1.4.0-beta1"). A pre-release
     * suffix ranks below the same version without one, so 1.4.0 > 1.4.0-beta1.
     */
    internal fun compareVersions(a: String, b: String): Int {
        fun parse(v: String): Pair<List<Int>, Boolean> {
            val clean = v.trim().removePrefix("v").removePrefix("V")
            val core = clean.substringBefore('-')
            val nums = core.split('.').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
            return nums to clean.contains('-')
        }
        val (na, preA) = parse(a)
        val (nb, preB) = parse(b)
        for (i in 0 until maxOf(na.size, nb.size)) {
            val d = na.getOrElse(i) { 0 } - nb.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return when {
            preA == preB -> 0
            preA -> -1
            else -> 1
        }
    }

    fun openUpdate(context: Context, update: Update) {
        try {
            context.startActivity(updateIntent(context, update).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }

    private fun updateIntent(context: Context, update: Update): Intent {
        // Prefer the Play Store app itself for Play installs; the https URL is the fallback.
        if (update.id.startsWith("play:")) {
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                .setPackage("com.android.vending")
            if (market.resolveActivity(context.packageManager) != null) return market
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(update.openUrl))
    }

    /** Posts the "update available" notification once per release. */
    fun notifyIfNew(context: Context, update: Update) {
        if (AppPreferences.getLastNotifiedUpdate(context) == update.id) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                LocaleHelper.getString(context, R.string.notif_channel_updates),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val pi = PendingIntent.getActivity(
            context, NOTIFICATION_ID, updateIntent(context, update),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = update.versionName
            ?.let { LocaleHelper.getString(context, R.string.update_available_text_version, it) }
            ?: LocaleHelper.getString(context, R.string.update_available_text)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pinit)
            .setContentTitle(LocaleHelper.getString(context, R.string.update_available_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .addAction(0, LocaleHelper.getString(context, R.string.action_update_now), pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            AppPreferences.setLastNotifiedUpdate(context, update.id)
        } catch (e: SecurityException) { }
    }

    /** Daily, network-only. KEEP so app launches don't reset the schedule. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(6, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        UpdateChecker.check(applicationContext)?.let { UpdateChecker.notifyIfNew(applicationContext, it) }
        return Result.success()
    }
}
