package com.posfingerprint.app

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Date

/** Settings: the one-time Odoo connection, background reliability and local data. */
class SettingsActivity : AppCompatActivity() {

    private enum class Tone { GOOD, WARN, BAD, NEUTRAL }

    private lateinit var prefs: Prefs
    private lateinit var root: View

    private lateinit var tvConnState: TextView
    private lateinit var tvConnBadge: TextView
    private lateinit var formConnect: View
    private lateinit var panelConnected: View
    private lateinit var tilUrl: TextInputLayout
    private lateinit var tilKey: TextInputLayout
    private lateinit var tilDb: TextInputLayout
    private lateinit var etUrl: TextInputEditText
    private lateinit var etKey: TextInputEditText
    private lateinit var etDb: TextInputEditText
    private lateinit var tvConnError: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var btnConnect: MaterialButton
    private lateinit var btnTest: MaterialButton

    private lateinit var rowNotif: View
    private lateinit var rowBattery: View
    private lateinit var rowFullScreen: View
    private lateinit var rowBiometric: View
    private lateinit var rowKeys: View
    private lateinit var rowHistory: View
    private lateinit var rowErase: View
    private lateinit var rowHelp: View
    private lateinit var rowVersion: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Security.protect(this)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        root = findViewById(R.id.root)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        tvConnState = findViewById(R.id.tvConnState)
        tvConnBadge = findViewById(R.id.tvConnBadge)
        formConnect = findViewById(R.id.formConnect)
        panelConnected = findViewById(R.id.panelConnected)
        tilUrl = findViewById(R.id.tilUrl)
        tilKey = findViewById(R.id.tilKey)
        tilDb = findViewById(R.id.tilDb)
        etUrl = findViewById(R.id.etUrl)
        etKey = findViewById(R.id.etKey)
        etDb = findViewById(R.id.etDb)
        tvConnError = findViewById(R.id.tvConnError)
        progress = findViewById(R.id.progress)
        btnConnect = findViewById(R.id.btnConnect)
        btnTest = findViewById(R.id.btnTest)

        btnConnect.setOnClickListener { connect() }
        etDb.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                connect(); true
            } else false
        }
        btnTest.setOnClickListener { testConnection() }
        findViewById<View>(R.id.btnDisconnect).setOnClickListener { confirmDisconnect() }

        rowNotif = findViewById(R.id.rowNotif)
        rowBattery = findViewById(R.id.rowBattery)
        rowFullScreen = findViewById(R.id.rowFullScreen)
        rowBiometric = findViewById(R.id.rowBiometric)
        rowKeys = findViewById(R.id.rowKeys)
        rowHistory = findViewById(R.id.rowHistory)
        rowErase = findViewById(R.id.rowErase)
        rowHelp = findViewById(R.id.rowHelp)
        rowVersion = findViewById(R.id.rowVersion)

        setupRow(rowNotif, R.drawable.ic_notifications, "Notifications", "Alerts for POS login requests") {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }
        setupRow(rowBattery, R.drawable.ic_battery, "Background running", "Receive requests while closed") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        setupRow(rowFullScreen, R.drawable.ic_fullscreen, "Full-screen requests", "Show requests on the lock screen") {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                )
            }
        }
        if (Build.VERSION.SDK_INT < 34) {
            rowFullScreen.isVisible = false
            findViewById<View>(R.id.divFullScreen).isVisible = false
        }
        setupRow(rowBiometric, R.drawable.ic_fingerprint, "Fingerprint sensor", "Needs a fingerprint in Android") {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        }
        setupRow(rowKeys, R.drawable.ic_key, "Fingerprint keys", "Stored in this phone's secure hardware", null)
        setupRow(rowHistory, R.drawable.ic_history, "Clear activity history", "Empty the list on the home screen") {
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear activity history?")
                .setMessage("Your fingerprints and POS terminals stay. Only the activity list is cleared.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->
                    prefs.clearHistory()
                    Snackbar.make(root, "Activity history cleared", Snackbar.LENGTH_SHORT).show()
                }
                .show()
        }
        setupRow(rowErase, R.drawable.ic_delete, "Erase all data", "Remove the connection, fingerprints and history") {
            confirmErase()
        }
        rowErase.findViewById<TextView>(R.id.rowTitle).setTextColor(getColor(R.color.error))
        setupRow(rowHelp, R.drawable.ic_info, "How it works", "Pairing, registering and approving logins") { showHelp() }
        setupRow(rowVersion, R.drawable.ic_shield, "Version", "POS Fingerprint for Odoo", null)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    // ---------------------------------------------------------------- rendering

    private fun render() {
        val connected = prefs.connected
        formConnect.isVisible = !connected
        panelConnected.isVisible = connected

        if (connected) {
            tvConnState.text = "Linked once, stays connected"
            badge(tvConnBadge, "Connected", Tone.GOOD)
            detail(R.id.rowServer, "Server", prefs.serverUrl)
            detail(R.id.rowDevice, "This phone in Odoo", prefs.deviceName.ifEmpty { "Unnamed" })
            detail(R.id.rowDb, "Database", prefs.database.ifEmpty { "Default" })
            detail(
                R.id.rowSince, "Connected since",
                if (prefs.connectedAt > 0) DateFormat.getMediumDateFormat(this).format(Date(prefs.connectedAt)) else "Before version 2.0"
            )
            if (Security.isEncrypted(prefs.serverUrl)) {
                detail(R.id.rowEncryption, "Connection", "Encrypted (HTTPS)")
                findViewById<View>(R.id.rowEncryption).findViewById<TextView>(R.id.detailValue)
                    .setTextColor(getColor(R.color.success))
            } else {
                detail(R.id.rowEncryption, "Connection", "Not encrypted (local network)")
                findViewById<View>(R.id.rowEncryption).findViewById<TextView>(R.id.detailValue)
                    .setTextColor(getColor(R.color.warning))
            }
        } else {
            tvConnState.text = "Not linked yet"
            badge(tvConnBadge, "Not connected", Tone.WARN)
        }

        val notifOn = NotificationManagerCompat.from(this).areNotificationsEnabled()
        rowValue(rowNotif, if (notifOn) "On" else "Off", if (notifOn) Tone.GOOD else Tone.BAD)

        val unrestricted = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        rowValue(rowBattery, if (unrestricted) "Allowed" else "Restricted", if (unrestricted) Tone.GOOD else Tone.WARN)

        if (Build.VERSION.SDK_INT >= 34) {
            val fs = getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
            rowValue(rowFullScreen, if (fs) "Allowed" else "Off", if (fs) Tone.GOOD else Tone.WARN)
        }

        val bio = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        when (bio) {
            BiometricManager.BIOMETRIC_SUCCESS -> rowValue(rowBiometric, "Ready", Tone.GOOD)
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> rowValue(rowBiometric, "Not set up", Tone.BAD)
            else -> rowValue(rowBiometric, "Unavailable", Tone.BAD)
        }

        val keys = try {
            KeyStoreHelper.aliases().size
        } catch (_: Exception) {
            0
        }
        rowValue(rowKeys, keys.toString(), Tone.NEUTRAL)

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
        rowValue(rowVersion, version, Tone.NEUTRAL)
    }

    private fun setupRow(row: View, icon: Int, title: String, subtitle: String, onClick: (() -> Unit)?) {
        row.findViewById<ImageView>(R.id.rowIcon).setImageResource(icon)
        row.findViewById<TextView>(R.id.rowTitle).text = title
        row.findViewById<TextView>(R.id.rowSubtitle).text = subtitle
        row.findViewById<View>(R.id.rowChevron).isVisible = onClick != null
        if (onClick != null) {
            row.setOnClickListener { onClick() }
        } else {
            row.isClickable = false
            row.background = null
        }
    }

    private fun rowValue(row: View, text: String, tone: Tone) {
        val tv = row.findViewById<TextView>(R.id.rowValue)
        tv.isVisible = text.isNotEmpty()
        badge(tv, text, tone)
    }

    private fun badge(tv: TextView, text: String, tone: Tone) {
        val (fg, bg) = when (tone) {
            Tone.GOOD -> R.color.success to R.color.success_container
            Tone.WARN -> R.color.warning to R.color.warning_container
            Tone.BAD -> R.color.error to R.color.error_container
            Tone.NEUTRAL -> R.color.text_secondary to R.color.surface_alt
        }
        tv.text = text
        tv.setTextColor(getColor(fg))
        Ui.tint(tv, getColor(bg))
    }

    private fun detail(rowId: Int, label: String, value: String) {
        val row = findViewById<View>(rowId)
        row.findViewById<TextView>(R.id.detailLabel).text = label
        row.findViewById<TextView>(R.id.detailValue).text = value
    }

    // ---------------------------------------------------------------- connection

    private fun connect() {
        tilUrl.error = null
        tilKey.error = null
        tvConnError.isVisible = false

        var url = etUrl.text?.toString().orEmpty().trim().trimEnd('/')
        if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        val key = etKey.text?.toString().orEmpty().trim()
        val db = etDb.text?.toString().orEmpty().trim()

        var valid = true
        val problem = if (url.isEmpty()) "Enter the address you use to open Odoo" else Security.addressProblem(url)
        if (problem != null) {
            tilUrl.error = problem
            valid = false
        }
        if (key.isEmpty()) {
            tilKey.error = "Paste the API key generated in Odoo"
            valid = false
        } else if (key.length < 8 || key.length > 512 || key.any { it.isWhitespace() }) {
            tilKey.error = "This doesn't look like an Odoo API key. Copy it again from Odoo."
            valid = false
        }
        if (!valid) return

        etUrl.setText(url)
        hideKeyboard()
        if (!Security.isEncrypted(url)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Connection won't be encrypted")
                .setMessage(
                    "This address uses http://, so anyone on the same Wi-Fi could read the API key. " +
                        "Use an https:// address if your Odoo has one. Continue only on a trusted private network."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue anyway") { _, _ -> doConnect(url, key, db) }
                .show()
            return
        }
        doConnect(url, key, db)
    }

    private fun doConnect(url: String, key: String, db: String) {
        setBusy(true)

        prefs.serverUrl = url
        prefs.apiKey = key
        prefs.database = db

        Thread {
            val result: Pair<Boolean, String> = try {
                val res = Api.call(prefs, "/pos_fp/app/ping")
                if (res.optBoolean("ok", false)) true to res.optString("name")
                else false to res.optString("error", "The server refused the key.")
            } catch (e: Exception) {
                false to (e.message ?: "Unknown error")
            }

            if (result.first) {
                prefs.deviceName = result.second
                prefs.connectedAt = System.currentTimeMillis()
                prefs.bump()
                ContextCompat.startForegroundService(applicationContext, Intent(applicationContext, PollService::class.java))
            } else {
                prefs.clearConnection()
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setBusy(false)
                if (result.first) {
                    etKey.setText("")
                    render()
                    val who = result.second.ifEmpty { "this phone" }
                    Snackbar.make(root, "Connected as $who", Snackbar.LENGTH_LONG).show()
                } else {
                    tvConnError.text = "Couldn't connect: ${result.second}"
                    tvConnError.isVisible = true
                }
            }
        }.start()
    }

    private fun setBusy(busy: Boolean) {
        progress.isVisible = busy
        btnConnect.isEnabled = !busy
        btnConnect.text = if (busy) "Connecting…" else "Connect this phone"
        tilUrl.isEnabled = !busy
        tilKey.isEnabled = !busy
        tilDb.isEnabled = !busy
    }

    private fun testConnection() {
        btnTest.isEnabled = false
        btnTest.text = "Testing…"
        Thread {
            val message = try {
                val res = Api.call(prefs, "/pos_fp/app/ping")
                if (res.optBoolean("ok", false)) {
                    val name = res.optString("name")
                    if (name.isNotEmpty() && name != prefs.deviceName) {
                        prefs.deviceName = name
                        prefs.bump()
                    }
                    "Connection works. Odoo answered."
                } else {
                    "Odoo refused the connection: " + res.optString("error", "unknown reason")
                }
            } catch (e: Exception) {
                "Couldn't reach Odoo: " + (e.message ?: "network error")
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                btnTest.isEnabled = true
                btnTest.text = "Test"
                render()
                Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun confirmDisconnect() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Disconnect this phone?")
            .setMessage(
                "Login requests will stop arriving until you connect again with a new API key. " +
                    "Fingerprints stay on this phone."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Disconnect") { _, _ ->
                stopService(Intent(this, PollService::class.java))
                RequestState.online = false
                RequestState.lastError = ""
                prefs.clearConnection()
                etUrl.setText("")
                etKey.setText("")
                etDb.setText("")
                render()
                Snackbar.make(root, "Disconnected", Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun confirmErase() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Erase all data?")
            .setMessage(
                "This disconnects the phone, deletes every fingerprint key stored on it, and clears the " +
                    "POS list and history. Employees will need to configure their fingerprint again from Odoo. " +
                    "This can't be undone."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Erase") { _, _ ->
                stopService(Intent(this, PollService::class.java))
                RequestState.online = false
                RequestState.lastError = ""
                RequestState.handled.clear()
                prefs.eraseAll()
                etUrl.setText("")
                etKey.setText("")
                etDb.setText("")
                render()
                Snackbar.make(root, "All data erased from this phone", Snackbar.LENGTH_LONG).show()
            }
            .show()
    }

    private fun hideKeyboard() {
        val view = currentFocus ?: return
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    private fun showHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle("How it works")
            .setMessage(
                "1. Connect once\nIn Odoo go to Point of Sale › Fingerprint App Keys, create a key for this phone, " +
                    "and paste it in the connection form above.\n\n" +
                    "2. Register a fingerprint\nOpen the employee in Odoo, go to the Fingerprint tab and choose " +
                    "Configure Fingerprint, then pick this phone. Scan your finger when the request arrives.\n\n" +
                    "3. Log in at the POS\nTap Scan Fingerprint on the POS login screen. This phone shows the request, " +
                    "you scan, and the POS signs you in.\n\n" +
                    "Your fingerprint never leaves the phone. Odoo only receives a signature that proves the scan happened."
            )
            .setPositiveButton("Got it", null)
            .show()
    }
}
