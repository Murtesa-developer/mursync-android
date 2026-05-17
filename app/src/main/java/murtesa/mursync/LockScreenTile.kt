package murtesa.mursync

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class LockScreenTile : TileService() {

    private lateinit var sharedPrefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        sharedPrefs = getSharedPreferences("MursyncPrefs", Context.MODE_PRIVATE)
    }

    override fun onClick() {
        val service = DndSyncService.getInstance()
        if (service == null || !service.isConnected()) {
            android.widget.Toast.makeText(applicationContext, "Not connected to desktop", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val isLocked = service.getDesktopLockState()
        if (isLocked) {
            android.widget.Toast.makeText(applicationContext, "Desktop already locked", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            service.sendLockCommand()
            android.widget.Toast.makeText(applicationContext, "Locking desktop...", android.widget.Toast.LENGTH_SHORT).show()
        }
        // Tile will be updated by broadcast receiver (see below)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
        // Register to receive state updates from service
        registerReceiver()
    }

    override fun onStopListening() {
        super.onStopListening()
        try { unregisterReceiver() } catch (e: Exception) { }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val service = DndSyncService.getInstance()
        val isLocked = service?.getDesktopLockState() ?: false

        if (isLocked) {
            tile.label = "Screen Locked"
            tile.subtitle = "Desktop is locked"
            tile.state = Tile.STATE_ACTIVE
        } else {
            tile.label = "Lock Screen"
            tile.subtitle = "Tap to lock desktop"
            tile.state = Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    private fun registerReceiver() {
        val filter = android.content.IntentFilter(DndSyncService.ACTION_STATE_UPDATE)
        registerReceiver(stateReceiver, filter)
    }

    private fun unregisterReceiver() {
        try { unregisterReceiver(stateReceiver) } catch (e: Exception) { }
    }

    private val stateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: android.content.Intent) {
            if (intent.action == DndSyncService.ACTION_STATE_UPDATE) {
                updateTileState()
            }
        }
    }
}