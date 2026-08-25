package com.ozon.notes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val repository = AppContainer.provideRepository(context.applicationContext)
            CoroutineScope(Dispatchers.IO).launch {
                val entries = repository.getAllEntries().first()
                entries.filter { it.remindMe && it.dueDate != null && it.dueDate!! > System.currentTimeMillis() }
                    .forEach { entry ->
                        NotificationHelper.scheduleNotification(context, entry)
                    }
            }
        }
    }
}
