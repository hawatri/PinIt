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

fun formatAlarmText(calendar: Calendar): String {
    val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return formatter.format(calendar.time)
}

private fun hasNotificationPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}

fun scheduleCustomAlarm(
    context: Context, 
    noteId: String, 
    noteTitle: String, 
    dateMillis: Long?, 
    hour: Int, 
    minute: Int
): Boolean {
    if (dateMillis == null) return false

    if (!hasNotificationPermission(context)) {
        Toast.makeText(context, "Please allow notifications for reminders", Toast.LENGTH_LONG).show()
        return false
    }

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // Android 12+ Security Check
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        Toast.makeText(context, "Exact alarm permission missing. Reminder may be delayed.", Toast.LENGTH_LONG).show()
    }

    // Material 3 DatePicker returns milliseconds in UTC. 
    // We must extract the year/month/day in UTC to prevent timezone shifting.
    val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = dateMillis
    }
    
    // Apply those dates, plus the selected time, to a local timezone calendar
    val localCalendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, utcCalendar.get(Calendar.YEAR))
        set(Calendar.MONTH, utcCalendar.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utcCalendar.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
    }

    // Don't set alarms in the past
    if (localCalendar.timeInMillis <= System.currentTimeMillis()) {
        Toast.makeText(context, "Cannot set reminder in the past", Toast.LENGTH_SHORT).show()
        return false
    }

    val intent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra(EXTRA_NOTE_ID, noteId)
        putExtra(EXTRA_NOTE_TITLE, noteTitle)
    }

    // Use the noteId's hashcode so an alarm for a specific note can be updated or overwritten
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        noteId.hashCode(), 
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Use exact alarms when available; otherwise fall back to inexact while-idle.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            localCalendar.timeInMillis,
            pendingIntent
        )
    } else {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            localCalendar.timeInMillis,
            pendingIntent
        )
    }
    
    Toast.makeText(context, "Reminder set!", Toast.LENGTH_SHORT).show()
    return true
}

fun setTomorrowAlarm(context: Context, noteId: String, noteTitle: String): Boolean {
    if (!hasNotificationPermission(context)) {
        Toast.makeText(context, "Please allow notifications for reminders", Toast.LENGTH_LONG).show()
        return false
    }

    // Get exactly 8:00 AM tomorrow in local time
    val tomorrow = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1) // Add one day
        set(Calendar.HOUR_OF_DAY, 8) // 8 AM
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }

    // We can reuse the same exact alarm logic!
    // Since we already calculated the exact local time, we bypass the UTC merge logic 
    // by passing the time directly to AlarmManager.
    
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        Toast.makeText(context, "Exact alarm permission missing. Reminder may be delayed.", Toast.LENGTH_LONG).show()
    }

    val intent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra(EXTRA_NOTE_ID, noteId)
        putExtra(EXTRA_NOTE_TITLE, noteTitle)
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        noteId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            tomorrow.timeInMillis,
            pendingIntent
        )
    } else {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            tomorrow.timeInMillis,
            pendingIntent
        )
    }

    Toast.makeText(context, "Reminder set for tomorrow at 8:00 AM", Toast.LENGTH_SHORT).show()
    return true
}

fun cancelAlarm(context: Context, noteId: String) {
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
