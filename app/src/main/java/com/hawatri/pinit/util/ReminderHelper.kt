package com.hawatri.pinit.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.hawatri.pinit.receiver.AlarmReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

const val EXTRA_NOTE_ID = "EXTRA_NOTE_ID"
const val EXTRA_NOTE_TITLE = "EXTRA_NOTE_TITLE"
const val EXTRA_REMINDER_TIME = "EXTRA_REMINDER_TIME"

fun formatAlarmText(calendar: Calendar): String {
    val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return formatter.format(calendar.time)
}

fun formatAlarmText(timeMillis: Long): String {
    val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return formatter.format(java.util.Date(timeMillis))
}

private fun hasNotificationPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}

private fun requestCodeFor(noteId: String, timeMillis: Long): Int =
    "$noteId:$timeMillis".hashCode()

private fun buildAlarmIntent(context: Context, noteId: String, noteTitle: String, timeMillis: Long): Intent =
    Intent(context, AlarmReceiver::class.java).apply {
        // setData makes each (noteId, time) intent uniquely matchable for cancellation
        data = android.net.Uri.parse("pinit://reminder/$noteId/$timeMillis")
        putExtra(EXTRA_NOTE_ID, noteId)
        putExtra(EXTRA_NOTE_TITLE, noteTitle)
        putExtra(EXTRA_REMINDER_TIME, timeMillis)
    }

/** Schedules an alarm at [timeMillis]. Multiple alarms per note are supported via unique request codes. */
fun scheduleAlarmAt(context: Context, noteId: String, noteTitle: String, timeMillis: Long): Boolean {
    if (!hasNotificationPermission(context)) {
        Toast.makeText(context, "Please allow notifications for reminders", Toast.LENGTH_LONG).show()
        return false
    }
    if (timeMillis <= System.currentTimeMillis()) {
        Toast.makeText(context, "Cannot set reminder in the past", Toast.LENGTH_SHORT).show()
        return false
    }

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        Toast.makeText(context, "Exact alarm permission missing. Reminder may be delayed.", Toast.LENGTH_LONG).show()
    }

    val intent = buildAlarmIntent(context, noteId, noteTitle, timeMillis)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        requestCodeFor(noteId, timeMillis),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
    } else {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
    }
    return true
}

/** Cancels a single scheduled alarm. */
fun cancelAlarmAt(context: Context, noteId: String, timeMillis: Long) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = buildAlarmIntent(context, noteId, "", timeMillis)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        requestCodeFor(noteId, timeMillis),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
}

/** Cancels every alarm in [times] for the given note. */
fun cancelAllAlarms(context: Context, noteId: String, times: List<Long>) {
    times.forEach { cancelAlarmAt(context, noteId, it) }
}

fun computeAlarmMillis(dateMillis: Long?, hour: Int, minute: Int): Long? {
    if (dateMillis == null) return null
    val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateMillis }
    val localCalendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, utcCalendar.get(Calendar.YEAR))
        set(Calendar.MONTH, utcCalendar.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utcCalendar.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return localCalendar.timeInMillis
}

fun tomorrowAt8AmMillis(): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, 1)
    set(Calendar.HOUR_OF_DAY, 8)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

// ===== Legacy single-alarm API (kept for backwards compatibility with code that still calls into it) =====

fun scheduleCustomAlarm(
    context: Context,
    noteId: String,
    noteTitle: String,
    dateMillis: Long?,
    hour: Int,
    minute: Int
): Boolean {
    val millis = computeAlarmMillis(dateMillis, hour, minute) ?: return false
    val ok = scheduleAlarmAt(context, noteId, noteTitle, millis)
    if (ok) Toast.makeText(context, "Reminder set!", Toast.LENGTH_SHORT).show()
    return ok
}

fun setTomorrowAlarm(context: Context, noteId: String, noteTitle: String): Boolean {
    val ok = scheduleAlarmAt(context, noteId, noteTitle, tomorrowAt8AmMillis())
    if (ok) Toast.makeText(context, "Reminder set for tomorrow at 8:00 AM", Toast.LENGTH_SHORT).show()
    return ok
}

fun cancelAlarm(context: Context, noteId: String) {
    // Legacy cancel — used the noteId hashcode as the request code
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        noteId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
}
