package com.posfingerprint.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var etUrl: EditText
    private lateinit var etKey: EditText
    private lateinit var etDb: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvKeys: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        etUrl = findViewById(R.id.etUrl)
        etKey = findViewById(R.id.etKey)
        etDb = findViewById(R.id.etDb)
        tvStatus = findViewById(R.id.tvStatus)
        tvKeys = findViewById(R.id.tvKeys)
        etUrl.setText(prefs.serverUrl)
        etKey.setText(prefs.apiKey)
        etDb.setText(prefs.database)

        findViewById<Button>(R.id.btnConnect).setOnClickListener { connect() }
        findViewById<Button>(R.id.btnDisconnect).setOnClickListener { disconnect() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (prefs.connected) startPolling()
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun startPolling() {
        ContextCompat.startForegroundService(this, Intent(this, PollService::class.java))
    }

    private fun connect() {
        val url = etUrl.text.toString().trim().trimEnd('/')
        val key = etKey.text.toString().trim()
        if (!(url.startsWith("http://") || url.startsWith("https://")) || key.isEmpty()) {
            Toast.makeText(this, "Enter the Odoo URL (http:// or https://) and the API key", Toast.LENGTH_LONG).show()
            return
        }
        prefs.serverUrl = url
        prefs.apiKey = key
        prefs.database = etDb.text.toString()
        tvStatus.text = "Connecting…"
        Thread {
            try {
                val res = Api.call(prefs, "/pos_fp/app/ping")
                runOnUiThread {
                    if (res.optBoolean("ok", false)) {
                        Toast.makeText(this, "Connected as " + res.optString("name"), Toast.LENGTH_SHORT).show()
                        startPolling()
                    } else {
                        prefs.clearConnection()
                        tvStatus.text = res.optString("error", "Connection refused")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    prefs.clearConnection()
                    tvStatus.text = "Could not connect: " + (e.message ?: "unknown error")
                }
            }
        }.start()
    }

    private fun disconnect() {
        stopService(Intent(this, PollService::class.java))
        prefs.clearConnection()
        etKey.setText("")
        refresh()
    }

    private fun refresh() {
        tvStatus.text = when {
            !prefs.connected -> "Not connected"
            RequestState.online -> "● Online - waiting for requests"
            RequestState.lastError.isNotEmpty() -> "○ Offline: " + RequestState.lastError
            else -> "○ Connecting…"
        }
        tvKeys.text = try {
            "Fingerprints registered on this phone: " + KeyStoreHelper.aliases().size
        } catch (e: Exception) {
            ""
        }
    }
}
