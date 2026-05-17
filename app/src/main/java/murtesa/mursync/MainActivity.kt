package murtesa.mursync

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var mainContainer: LinearLayout
    private lateinit var connectionStatusText: TextView
    private lateinit var lockStatusText: TextView

    private lateinit var dndCard: MaterialCardView
    private lateinit var systemSyncCard: MaterialCardView
    private lateinit var lockCard: MaterialCardView

    private lateinit var dndSwitch: SwitchMaterial
    private lateinit var systemSyncSwitch: SwitchMaterial
    private lateinit var lockButton: MaterialButton

    private var dndReceiver: DndReceiver? = null
    private var stateReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPrefs = getSharedPreferences("MursyncPrefs", Context.MODE_PRIVATE)

        if (!sharedPrefs.getBoolean("isSetup", false)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        setupViews()
        requestPermissions()
        showMainInterface()
        startPersistentService()
        registerStateReceiver()
    }

    private fun setupViews() {
        mainContainer = findViewById(R.id.mainContainer)
        connectionStatusText = findViewById(R.id.connectionStatus)
        lockStatusText = findViewById(R.id.lockStatus)

        dndCard = findViewById(R.id.dndCard)
        systemSyncCard = findViewById(R.id.systemSyncCard)
        lockCard = findViewById(R.id.lockCard)

        dndSwitch = findViewById(R.id.dndSwitch)
        systemSyncSwitch = findViewById(R.id.systemSyncSwitch)
        lockButton = findViewById(R.id.lockButton)
    }

    private fun requestPermissions() {
        requestBatteryOptimization()
        requestNotificationPermission()
        requestOverlayPermission()
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun startPersistentService() {
        val serviceIntent = Intent(this, DndSyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    Toast.makeText(this, "Please disable battery optimization for Mursync", Toast.LENGTH_LONG).show()
                } catch (e: Exception) { }
            }
        }
    }

    private fun registerStateReceiver() {
        stateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == DndSyncService.ACTION_STATE_UPDATE) {
                    val locked = intent.getBooleanExtra(DndSyncService.EXTRA_LOCK_STATE, false)
                    updateLockUI(locked)
                }
            }
        }
        registerReceiver(stateReceiver, IntentFilter(DndSyncService.ACTION_STATE_UPDATE))
    }

    private fun updateLockUI(locked: Boolean) {
        if (locked) {
            lockStatusText.text = "🔒 Desktop LOCKED"
            lockButton.text = "Unlock (via desktop)"
            lockButton.isEnabled = false
        } else {
            lockStatusText.text = "🔓 Desktop UNLOCKED"
            lockButton.text = "Lock Desktop Now"
            lockButton.isEnabled = true
        }
    }

    private fun showMainInterface() {
        mainContainer.visibility = View.VISIBLE
        updateConnectionStatus()

        dndCard.setOnClickListener {
            val newState = !dndSwitch.isChecked
            dndSwitch.isChecked = newState
            handleDndToggle(newState)
        }
        dndSwitch.isChecked = sharedPrefs.getBoolean("dndEnabled", false)
        dndSwitch.setOnCheckedChangeListener { _, isChecked -> handleDndToggle(isChecked) }

        systemSyncCard.setOnClickListener {
            val newState = !systemSyncSwitch.isChecked
            systemSyncSwitch.isChecked = newState
            handleSystemSyncToggle(newState)
        }
        systemSyncSwitch.isChecked = sharedPrefs.getBoolean("syncWithSystemDnd", false)
        systemSyncSwitch.setOnCheckedChangeListener { _, isChecked -> handleSystemSyncToggle(isChecked) }

        lockCard.setOnClickListener {
            val service = DndSyncService.getInstance()
            if (service != null && !service.getDesktopLockState()) {
                service.sendLockCommand()
                Toast.makeText(this, "Locking desktop...", Toast.LENGTH_SHORT).show()
            } else if (service?.getDesktopLockState() == true) {
                Toast.makeText(this, "Desktop already locked", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Not connected to desktop", Toast.LENGTH_SHORT).show()
            }
        }
        lockButton.setOnClickListener {
            val service = DndSyncService.getInstance()
            if (service != null && !service.getDesktopLockState()) {
                service.sendLockCommand()
                Toast.makeText(this, "Locking desktop...", Toast.LENGTH_SHORT).show()
            } else if (service?.getDesktopLockState() == true) {
                Toast.makeText(this, "Desktop already locked", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Not connected to desktop", Toast.LENGTH_SHORT).show()
            }
        }

        val service = DndSyncService.getInstance()
        if (service != null) updateLockUI(service.getDesktopLockState())

        findViewById<MaterialButton>(R.id.reconnectButton).setOnClickListener { restartService() }
        findViewById<MaterialButton>(R.id.resetButton).setOnClickListener { resetSetup() }

        if (sharedPrefs.getBoolean("syncWithSystemDnd", false)) {
            registerDndReceiver()
        }
    }

    private fun handleDndToggle(isChecked: Boolean) {
        sharedPrefs.edit().putBoolean("dndEnabled", isChecked).apply()
        val syncWithSystem = sharedPrefs.getBoolean("syncWithSystemDnd", false)
        if (!syncWithSystem) {
            DndSyncService.getInstance()?.sendDndStatus(isChecked)
        }
        Toast.makeText(this, if (isChecked) "Desktop DND Enabled" else "Desktop DND Disabled", Toast.LENGTH_SHORT).show()
    }

    private fun handleSystemSyncToggle(isChecked: Boolean) {
        sharedPrefs.edit().putBoolean("syncWithSystemDnd", isChecked).apply()
        if (isChecked) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                Toast.makeText(this, "Please grant DND permission for system sync", Toast.LENGTH_LONG).show()
                systemSyncSwitch.isChecked = false
                sharedPrefs.edit().putBoolean("syncWithSystemDnd", false).apply()
                return
            }
            registerDndReceiver()
            Toast.makeText(this, "Syncing with Android DND enabled", Toast.LENGTH_SHORT).show()
        } else {
            unregisterDndReceiver()
            Toast.makeText(this, "Using manual DND control", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateConnectionStatus() {
        val isConnected = DndSyncService.getInstance()?.isConnected() ?: false
        val ip = sharedPrefs.getString("deviceIp", "Unknown") ?: "Unknown"
        connectionStatusText.text = if (isConnected) "● Connected to $ip" else "○ Disconnected - Reconnecting..."
        connectionStatusText.setTextColor(
            if (isConnected) getColor(R.color.status_connected) else getColor(R.color.status_disconnected)
        )
    }

    private fun registerDndReceiver() {
        if (dndReceiver == null) {
            dndReceiver = DndReceiver()
            val filter = IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            registerReceiver(dndReceiver, filter)
        }
    }

    private fun unregisterDndReceiver() {
        dndReceiver?.let {
            try { unregisterReceiver(it) } catch (e: Exception) { }
            dndReceiver = null
        }
    }

    private fun restartService() {
        val serviceIntent = Intent(this, DndSyncService::class.java)
        stopService(serviceIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "Reconnecting...", Toast.LENGTH_SHORT).show()
    }

    private fun resetSetup() {
        sharedPrefs.edit().clear().apply()
        stopService(Intent(this, DndSyncService::class.java))
        Toast.makeText(this, "Settings reset. Restarting setup.", Toast.LENGTH_LONG).show()
        startActivity(Intent(this, SetupActivity::class.java))
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (sharedPrefs.getBoolean("isSetup", false)) {
            updateConnectionStatus()
            dndSwitch.isChecked = sharedPrefs.getBoolean("dndEnabled", false)
            systemSyncSwitch.isChecked = sharedPrefs.getBoolean("syncWithSystemDnd", false)
            val service = DndSyncService.getInstance()
            if (service != null) updateLockUI(service.getDesktopLockState())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterDndReceiver()
        stateReceiver?.let { unregisterReceiver(it) }
    }
}