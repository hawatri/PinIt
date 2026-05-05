package com.hawatri.pinit.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.hawatri.pinit.receiver.AlarmReceiver
import java.util.Calendar
import java.util.TimeZone

fun scheduleCustomAlarm(
    context: Context, 
    noteId: String, 
    noteTitle: String, 
    dateMillis: Long?, 
    hour: Int, 
    minute: Int
) {
    if (dateMillis == null) return

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // Android 12+ Security Check
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        Toast.makeText(context, "Please grant Exact Alarm permission in app settings", Toast.LENGTH_LONG).show()
        // Optional: Launch Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        return
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
        return
    }

    val intent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra("EXTRA_NOTE_ID", noteId)
        putExtra("EXTRA_NOTE_TITLE", noteTitle)
    }

    // Use the noteId's hashcode so an alarm for a specific note can be updated or overwritten
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        noteId.hashCode(), 
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Fires the alarm exactly at the given time, waking up the device if necessary
    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        localCalendar.timeInMillis,
        pendingIntent
    )
    
    Toast.makeText(context, "Reminder set!", Toast.LENGTH_SHORT).show()
}

fun setTomorrowAlarm(context: Context, noteId: String, noteTitle: String) {
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
        Toast.makeText(context, "Please grant Exact Alarm permission", Toast.LENGTH_SHORT).show()
        return
    }

    val intent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra("EXTRA_NOTE_ID", noteId)
        putExtra("EXTRA_NOTE_TITLE", noteTitle)
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        noteId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        tomorrow.timeInMillis,
        pendingIntent
    )

    Toast.makeText(context, "Reminder set for tomorrow at 8:00 AM", Toast.LENGTH_SHORT).show()
}
