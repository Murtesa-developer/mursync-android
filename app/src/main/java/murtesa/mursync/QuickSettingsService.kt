package murtesa.mursync

import android.content.Context
import android.content.SharedPreferences
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class QuickSettingsService : TileService() {

    private lateinit var sharedPrefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        sharedPrefs = getSharedPreferences("MursyncPrefs", Context.MODE_PRIVATE)
    }

    override fun onClick() {
        super.onClick()

        val service = DndSyncService.getInstance()
        val currentDndState = sharedPrefs.getBoolean("dndEnabled", false)
        val newDndState = !currentDndState

        // Update local state
        sharedPrefs.edit().putBoolean("dndEnabled", newDndState).apply()

        // Check if sync with system is enabled
        val syncWithSystem = sharedPrefs.getBoolean("syncWithSystemDnd", false)

        if (!syncWithSystem) {
            // Only send to desktop if not syncing with system
            service?.sendDndStatus(newDndState)
        }

        // Update tile state
        qsTile.state = if (newDndState) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile.updateTile()

        // Show toast feedback
        val message = if (newDndState) "Mursync DND Enabled" else "Mursync DND Disabled"
        android.widget.Toast.makeText(applicationContext, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onStartListening() {
        super.onStartListening()
        val isDndEnabled = sharedPrefs.getBoolean("dndEnabled", false)
        qsTile.state = if (isDndEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile.updateTile()
    }
}