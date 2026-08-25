package com.ozon.notes

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getStringExtra("entryId") ?: return
        val action = intent.action ?: return
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(entryId.hashCode())

        if (action == "ACTION_CHECK") {
            val repository = AppContainer.provideRepository(context.applicationContext)
            CoroutineScope(Dispatchers.IO).launch {
                val entries = repository.getAllEntries().first()
                val entry = entries.find { it.id == entryId }
                if (entry != null) {
                    repository.saveEntry(entry.copy(isChecked = true))
                }
            }
        }
    }
}
