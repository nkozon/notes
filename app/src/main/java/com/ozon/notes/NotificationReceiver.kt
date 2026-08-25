package com.ozon.notes

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getStringExtra("entryId") ?: return
        val title = intent.getStringExtra("title") ?: "Note Reminder"
        val listId = intent.getStringExtra("listId") ?: ""

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "upcoming_notes_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Upcoming Notes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for upcoming note entries"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("listId", listId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 
            entryId.hashCode(), 
            mainIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val checkIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "ACTION_CHECK"
            putExtra("entryId", entryId)
        }
        val checkPendingIntent = PendingIntent.getBroadcast(
            context,
            entryId.hashCode() + 1,
            checkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rescheduleIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("listId", listId)
            putExtra("rescheduleEntryId", entryId)
        }
        val reschedulePendingIntent = PendingIntent.getActivity(
            context,
            entryId.hashCode() + 2,
            rescheduleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.checkbox_on_background, "Check", checkPendingIntent)
            .addAction(android.R.drawable.ic_menu_today, "Reschedule", reschedulePendingIntent)
            .build()

        notificationManager.notify(entryId.hashCode(), notification)
    }
}
