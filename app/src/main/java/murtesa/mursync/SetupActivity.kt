package murtesa.mursync

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText

class SetupActivity : AppCompatActivity() {

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var progressLayout: LinearLayout
    private lateinit var progressText: TextView
    private lateinit var setupContainer: LinearLayout
    private lateinit var deviceListContainer: LinearLayout
    private lateinit var ipInput: TextInputEditText
    private lateinit var portInput: TextInputEditText

    private var selectedDesktopEnv = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        sharedPrefs = getSharedPreferences("MursyncPrefs", Context.MODE_PRIVATE)
        setupViews()
    }

    private fun setupViews() {
        progressLayout = findViewById(R.id.progressLayout)
        progressText = findViewById(R.id.progressText)
        setupContainer = findViewById(R.id.setupContainer)
        deviceListContainer = findViewById(R.id.deviceListContainer)
        ipInput = findViewById(R.id.ipInput)
        portInput = findViewById(R.id.portInput)

        val gnomeCard = findViewById<MaterialCardView>(R.id.gnomeCard)
        val kdeCard = findViewById<MaterialCardView>(R.id.kdeCard)
        val nextButton = findViewById<MaterialButton>(R.id.nextButton)
        val searchButton = findViewById<MaterialButton>(R.id.searchButton)
        val connectButton = findViewById<MaterialButton>(R.id.connectButton)
        val backButton = findViewById<MaterialButton>(R.id.backButton)

        gnomeCard.setOnClickListener {
            selectedDesktopEnv = "gnome"
            gnomeCard.setCardBackgroundColor(getColor(R.color.selected_card))
            kdeCard.setCardBackgroundColor(getColor(R.color.default_card))
            nextButton.isEnabled = true
        }

        kdeCard.setOnClickListener {
            selectedDesktopEnv = "kde"
            kdeCard.setCardBackgroundColor(getColor(R.color.selected_card))
            gnomeCard.setCardBackgroundColor(getColor(R.color.default_card))
            nextButton.isEnabled = true
        }

        nextButton.setOnClickListener {
            setupContainer.visibility = View.GONE
            deviceListContainer.visibility = View.VISIBLE
        }

        backButton.setOnClickListener {
            deviceListContainer.visibility = View.GONE
            setupContainer.visibility = View.VISIBLE
        }

        searchButton.setOnClickListener {
            Toast.makeText(this, "Searching for devices...", Toast.LENGTH_SHORT).show()
            findDevicesOnNetwork()
        }

        connectButton.setOnClickListener {
            val targetIp = ipInput.text.toString().trim()
            val targetPort = portInput.text.toString().trim().ifEmpty { "8022" }

            if (targetIp.isNotEmpty()) {
                connectToDevice(targetIp, targetPort)
            } else {
                Toast.makeText(this, "Please enter device IP", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun findDevicesOnNetwork() {
        Thread {
            val baseIp = getLocalIpAddress()?.substringBeforeLast(".")
            if (baseIp != null) {
                for (i in 1..254) {
                    val testIp = "$baseIp.$i"
                    try {
                        val socket = java.net.Socket()
                        socket.connect(java.net.InetSocketAddress(testIp, 8022), 100)
                        socket.close()
                        runOnUiThread {
                            Toast.makeText(this, "Found device at: $testIp", Toast.LENGTH_LONG).show()
                            ipInput.setText(testIp)
                        }
                        break
                    } catch (e: Exception) { }
                }
            }
        }.start()
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") != true) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) { }
        return null
    }

    private fun connectToDevice(targetIp: String, targetPort: String) {
        progressLayout.visibility = View.VISIBLE
        progressText.text = "Connecting to $targetIp..."

        Thread {
            var socket: java.net.Socket? = null
            try {
                socket = java.net.Socket()
                // Connect with 5s timeout
                socket.connect(java.net.InetSocketAddress(targetIp, targetPort.toInt()), 5000)
                // Read timeout of 10s
                socket.soTimeout = 10000

                runOnUiThread { progressText.text = "Sending handshake..." }

                val output = socket.getOutputStream()
                val token = generateToken()
                val handshake = "HANDSHAKE|$selectedDesktopEnv|$token\n"
                output.write(handshake.toByteArray())
                output.flush()

                runOnUiThread { progressText.text = "Waiting for server response..." }

                val reader = socket.getInputStream().bufferedReader()
                val response = reader.readLine()?.trim()

                if (response == "ACK") {
                    sharedPrefs.edit().apply {
                        putBoolean("isSetup", true)
                        putString("deviceIp", targetIp)
                        putString("devicePort", targetPort)
                        putString("authToken", token)
                        putString("desktopEnv", selectedDesktopEnv)
                        putBoolean("dndEnabled", false)
                        putBoolean("lockScreenEnabled", true)
                        putBoolean("syncWithSystemDnd", false)
                    }.commit()

                    runOnUiThread {
                        progressLayout.visibility = View.GONE
                        Toast.makeText(this, "Setup complete!", Toast.LENGTH_LONG).show()
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                } else {
                    runOnUiThread {
                        progressLayout.visibility = View.GONE
                        val msg = if (response == null) "Server closed connection" else "Server rejected handshake: $response"
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressLayout.visibility = View.GONE
                    Toast.makeText(this, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                try { socket?.close() } catch (e: Exception) { }
            }
        }.start()
    }

    private fun generateToken(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..32).map { chars.random() }.joinToString("")
    }
}
