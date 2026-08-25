package com.ozon.notes

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object NotificationHelper {
    fun scheduleNotification(context: Context, entry: ListEntry) {
        if (!entry.remindMe || entry.dueDate == null) return
        
        // If due date is in the past, don't schedule but we might want to show it immediately if it just passed
        // For now, stick to future only to avoid spamming on boot/restore
        if (entry.dueDate <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("entryId", entry.id)
            putExtra("title", entry.title)
            putExtra("listId", entry.listId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            entry.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    val info = AlarmManager.AlarmClockInfo(entry.dueDate, pendingIntent)
                    alarmManager.setAlarmClock(info, pendingIntent)
                } else {
                    // Fallback to exact-but-less-prioritized if possible, or inexact
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.dueDate, pendingIntent)
                }
            } else {
                val info = AlarmManager.AlarmClockInfo(entry.dueDate, pendingIntent)
                alarmManager.setAlarmClock(info, pendingIntent)
            }
        } catch (e: Exception) {
            // Fallback for security exceptions or other issues
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.dueDate, pendingIntent)
            } catch (e2: Exception) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, entry.dueDate, pendingIntent)
            }
        }
    }

    fun cancelNotification(context: Context, entryId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            entryId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
