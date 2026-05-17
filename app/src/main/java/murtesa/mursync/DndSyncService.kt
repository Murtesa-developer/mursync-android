package murtesa.mursync

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class DndSyncService : Service() {
    private lateinit var sharedPrefs: SharedPreferences
    private var clientSocket: Socket? = null
    private var outputWriter: PrintWriter? = null
    private var inputReader: BufferedReader? = null

    private val isRunning = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)
    private var nextConnectTime = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null
    private var connectionThread: Thread? = null
    private var readThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val lastHeartbeatResponse = AtomicLong(0L)

    private var desktopDndOn = false
    private var desktopLocked = false

    companion object {
        private const val TAG = "MursyncService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "mursync_channel"
        private const val HEARTBEAT_INTERVAL = 15000L
        private const val HEARTBEAT_TIMEOUT = 45000L
        private const val RECONNECT_DELAY = 30000L
        private var instance: DndSyncService? = null

        const val ACTION_STATE_UPDATE = "mursync.STATE_UPDATE"
        const val EXTRA_DND_STATE = "dnd_state"
        const val EXTRA_LOCK_STATE = "lock_state"

        fun getInstance(): DndSyncService? = instance
        fun isConnected(): Boolean = instance?.isConnected() ?: false
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        sharedPrefs = getSharedPreferences("MursyncPrefs", Context.MODE_PRIVATE)
        createNotificationChannel()

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mursync::WakeLock")
        wakeLock?.acquire()

        Log.i(TAG, "Service Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning.get()) {
            isRunning.set(true)
            startForeground(NOTIFICATION_ID, createNotification("Mursync is running"))
            startPersistentConnection()
        }
        return START_STICKY
    }

    private fun startPersistentConnection() {
        if (connectionThread?.isAlive == true) return
        connectionThread = Thread {
            Log.d(TAG, "Connection management thread started")
            while (isRunning.get()) {
                val now = System.currentTimeMillis()
                if (!isConnected() && !isConnecting.get() && now >= nextConnectTime) {
                    establishConnection()
                }
                try {
                    Thread.sleep(2000)
                } catch (e: InterruptedException) {
                    Log.d(TAG, "Connection thread interrupted")
                    break
                }
            }
            Log.d(TAG, "Connection management thread ended")
        }
        connectionThread?.start()
    }

    fun isConnected(): Boolean {
        val socket = clientSocket
        return socket != null && socket.isConnected && !socket.isClosed && outputWriter != null
    }

    private fun establishConnection() {
        if (isConnecting.get()) return
        isConnecting.set(true)

        val ip = sharedPrefs.getString("deviceIp", "") ?: ""
        val portStr = sharedPrefs.getString("devicePort", "8022") ?: "8022"
        val token = sharedPrefs.getString("authToken", "") ?: ""
        val desktopEnv = sharedPrefs.getString("desktopEnv", "gnome") ?: "gnome"

        if (ip.isEmpty() || token.isEmpty()) {
            Log.e(TAG, "Missing connection settings")
            updateNotification("Configuration error")
            isConnecting.set(false)
            return
        }

        try {
            Log.i(TAG, "Attempting connection to $ip:$portStr...")
            updateNotification("Connecting to $ip...")
            closeConnectionInternal()

            val socket = Socket()
            socket.connect(InetSocketAddress(ip, portStr.toInt()), 10000)
            socket.keepAlive = true
            socket.tcpNoDelay = true

            clientSocket = socket
            outputWriter = PrintWriter(BufferedWriter(OutputStreamWriter(socket.getOutputStream())), true)
            inputReader = BufferedReader(InputStreamReader(socket.getInputStream()))

            if (performHandshake(token, desktopEnv)) {
                lastHeartbeatResponse.set(System.currentTimeMillis())
                updateNotification("Connected to $ip")
                Log.i(TAG, "Handshake successful with $ip")

                startMessageReader(socket)
                startHeartbeat()
                nextConnectTime = 0L

                val dndEnabled = sharedPrefs.getBoolean("dndEnabled", false)
                sendDndStatus(dndEnabled)
            } else {
                throw Exception("Handshake failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}")
            updateNotification("Connection failed. Retrying...")
            closeConnectionInternal()
            nextConnectTime = System.currentTimeMillis() + RECONNECT_DELAY
        } finally {
            isConnecting.set(false)
        }
    }

    private fun performHandshake(token: String, desktopEnv: String): Boolean {
        return try {
            clientSocket?.soTimeout = 10000
            outputWriter?.print("HANDSHAKE|$desktopEnv|$token\n")
            outputWriter?.flush()
            val response = inputReader?.readLine()?.trim()
            clientSocket?.soTimeout = 0
            response == "ACK"
        } catch (e: Exception) {
            Log.e(TAG, "Handshake error: ${e.message}")
            false
        }
    }

    private fun startMessageReader(socketToRead: Socket) {
        readThread?.interrupt()
        readThread = Thread {
            try {
                while (isRunning.get() && clientSocket == socketToRead && isConnected()) {
                    val message = inputReader?.readLine() ?: break
                    val trimmed = message.trim()
                    when {
                        trimmed.startsWith("PONG") -> {
                            lastHeartbeatResponse.set(System.currentTimeMillis())
                            val parts = trimmed.split("|")
                            if (parts.size >= 3) {
                                try {
                                    val dndState = parts[1].toInt() == 1
                                    val lockState = parts[2].toInt() == 1
                                    if (dndState != desktopDndOn || lockState != desktopLocked) {
                                        desktopDndOn = dndState
                                        desktopLocked = lockState
                                        broadcastStateUpdate()
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing PONG: ${e.message}")
                                }
                            }
                            Log.d(TAG, "PONG received (DND=$desktopDndOn, LOCK=$desktopLocked)")
                        }
                        trimmed.startsWith("STATUS:") -> Log.d(TAG, "Server status: $trimmed")
                        trimmed.isNotEmpty() -> Log.d(TAG, "Message from server: $trimmed")
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get() && clientSocket == socketToRead) {
                    Log.e(TAG, "Reader error: ${e.message}")
                }
            } finally {
                if (isRunning.get() && clientSocket == socketToRead) {
                    handleConnectionLoss()
                }
            }
        }
        readThread?.start()
    }

    private fun broadcastStateUpdate() {
        val intent = Intent(ACTION_STATE_UPDATE).apply {
            putExtra(EXTRA_DND_STATE, desktopDndOn)
            putExtra(EXTRA_LOCK_STATE, desktopLocked)
        }
        sendBroadcast(intent)
    }

    fun getDesktopDndState(): Boolean = desktopDndOn
    fun getDesktopLockState(): Boolean = desktopLocked

    private fun startHeartbeat() {
        handler.post {
            heartbeatRunnable?.let { handler.removeCallbacks(it) }
            heartbeatRunnable = object : Runnable {
                override fun run() {
                    if (isRunning.get() && isConnected()) {
                        val now = System.currentTimeMillis()
                        if (now - lastHeartbeatResponse.get() > HEARTBEAT_TIMEOUT) {
                            Log.e(TAG, "Heartbeat timeout!")
                            handleConnectionLoss()
                            return
                        }
                        sendHeartbeat()
                        handler.postDelayed(this, HEARTBEAT_INTERVAL)
                    }
                }
            }
            handler.post(heartbeatRunnable!!)
        }
    }

    private fun sendHeartbeat() {
        Thread {
            try {
                if (isConnected()) {
                    outputWriter?.print("PING\n")
                    outputWriter?.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send PING")
            }
        }.start()
    }

    fun sendDndStatus(isDndOn: Boolean) {
        Thread {
            try {
                if (isConnected()) {
                    outputWriter?.print("DND|$isDndOn\n")
                    outputWriter?.flush()
                    Log.i(TAG, "DND status sent: $isDndOn")
                    sharedPrefs.edit().putBoolean("dndEnabled", isDndOn).apply()
                } else {
                    Log.w(TAG, "DND status not sent: Not connected")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending DND status: ${e.message}")
            }
        }.start()
    }

    fun sendLockCommand() {
        Thread {
            try {
                if (isConnected()) {
                    outputWriter?.print("LOCK\n")
                    outputWriter?.flush()
                    Log.i(TAG, "LOCK command sent")
                } else {
                    Log.w(TAG, "LOCK not sent: Not connected")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending LOCK: ${e.message}")
            }
        }.start()
    }

    private fun handleConnectionLoss() {
        if (isConnecting.get()) return
        Log.w(TAG, "Handling connection loss")
        closeConnectionInternal()
        if (nextConnectTime == 0L) {
            nextConnectTime = System.currentTimeMillis() + RECONNECT_DELAY
        }
        updateNotification("Disconnected. Reconnecting...")
    }

    private fun closeConnectionInternal() {
        try {
            heartbeatRunnable?.let { handler.removeCallbacks(it) }
            outputWriter?.close()
            inputReader?.close()
            clientSocket?.close()
        } catch (e: Exception) { }
        finally {
            outputWriter = null
            inputReader = null
            clientSocket = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mursync Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background synchronization for Do Not Disturb"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mursync")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: String) {
        handler.post {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, createNotification(status))
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "Service being destroyed")
        isRunning.set(false)
        connectionThread?.interrupt()
        closeConnectionInternal()
        connectionThread?.join(1000)
        readThread?.interrupt()
        wakeLock?.release()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}