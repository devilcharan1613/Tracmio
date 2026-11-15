package com.tracmio.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tracmio.app.data.PreferencesAndThemeStore

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = PreferencesAndThemeStore(context.applicationContext)
            if (prefs.isDailyReminderEnabled()) {
                val (hour, minute) = prefs.getDailyReminderTime()
                val settings = prefs.getInitialSettings()
                TracmioReminderScheduler.scheduleDailyReminder(context, hour, minute, settings.userName)
            }
            if (prefs.isMonthlyBackupEnabled()) {
                TracmioReminderScheduler.scheduleMonthlyBackupReminder(context, enabled = true, dayOfMonth = 1, hour = 20, minute = 0)
            }
        }
    }
}
