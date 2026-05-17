package murtesa.mursync

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log

class DndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val filter = notificationManager.currentInterruptionFilter

                val isDndOn = when (filter) {
                    NotificationManager.INTERRUPTION_FILTER_NONE,
                    NotificationManager.INTERRUPTION_FILTER_ALARMS,
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY -> true
                    else -> false
                }

                val sharedPrefs = context.getSharedPreferences("MursyncPrefs", Context.MODE_PRIVATE)
                val syncWithSystem = sharedPrefs.getBoolean("syncWithSystemDnd", false)

                Log.d("DndReceiver", "System DND changed: isDndOn=$isDndOn, syncWithSystem=$syncWithSystem")

                // ONLY send to desktop if sync is enabled
                if (syncWithSystem) {
                    Log.i("DndReceiver", "Sync enabled - sending DND status to desktop: $isDndOn")

                    val serviceIntent = Intent(context, DndSyncService::class.java)
                    context.startService(serviceIntent)

                    val service = DndSyncService.getInstance()
                    if (service != null) {
                        service.sendDndStatus(isDndOn)
                        // Update shared preferences to keep UI in sync
                        sharedPrefs.edit().putBoolean("dndEnabled", isDndOn).apply()
                    }
                } else {
                    Log.d("DndReceiver", "Sync disabled - ignoring system DND change")
                }
            }
        }
    }
}