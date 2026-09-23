package com.hawatri.pinit

import android.app.Application
import com.hawatri.pinit.util.NotificationHelper
import com.hawatri.pinit.util.rescheduleAllReminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Process-start safety net. AlarmManager drops exact alarms after a force-stop,
 * process death, or some OEM battery kills — re-arming here means reminders
 * come back as soon as the user opens the app (or anything else starts us).
 */
class PinItApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            rescheduleAllReminders(this@PinItApplication)
            NotificationHelper(this@PinItApplication).refreshQuickAdd()
        }
    }
}
