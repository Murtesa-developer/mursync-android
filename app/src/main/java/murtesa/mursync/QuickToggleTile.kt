package murtesa.mursync

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class QuickToggleTile : TileService() {

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

        Handler(Looper.getMainLooper()).postDelayed({
            updateTile()
        }, 500)

        // Show toast feedback
        val message = if (newDndState) "Desktop DND ON" else "Desktop DND OFF"
        android.widget.Toast.makeText(applicationContext, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isDndEnabled = sharedPrefs.getBoolean("dndEnabled", false)

        if (isDndEnabled) {
            tile.label = "DND ON"
            tile.subtitle = "Desktop notifications silenced"
            tile.state = Tile.STATE_ACTIVE
        } else {
            tile.label = "DND OFF"
            tile.subtitle = "Desktop notifications active"
            tile.state = Tile.STATE_INACTIVE
        }

        tile.updateTile()
    }
}