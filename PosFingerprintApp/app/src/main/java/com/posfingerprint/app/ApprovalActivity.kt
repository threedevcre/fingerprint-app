package com.posfingerprint.app

import android.app.NotificationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import org.json.JSONObject

/** Opened from the notification: scan the fingerprint to register a key or approve a POS login. */
class ApprovalActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var req: JSONObject
    private lateinit var tvTitle: TextView
    private lateinit var tvDetail: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnScan: Button

    private var kind = ""
    private var token = ""
    private var challenge = ""
    private var employee = ""
    private var busy = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_approval)
        prefs = Prefs(this)
        tvTitle = findViewById(R.id.tvTitle)
        tvDetail = findViewById(R.id.tvDetail)
        tvStatus = findViewById(R.id.tvStatus)
        btnScan = findViewById(R.id.btnScan)
        btnScan.setOnClickListener { begin() }
        findViewById<Button>(R.id.btnCancel).setOnClickListener { reject() }
        load()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        load()
    }

    private fun load() {
        val raw = intent.getStringExtra("request")
        if (raw == null) {
            finish()
            return
        }
        req = JSONObject(raw)
        kind = req.getString("kind")
        token = req.getString("token")
        challenge = req.getString("challenge")
        employee = req.optString("employee")
        busy = false
        tvStatus.text = ""
        if (kind == "register") {
            tvTitle.text = "Set up fingerprint"
            tvDetail.text = "Register your fingerprint for\n$employee"
        } else {
            tvTitle.text = "POS login"
            tvDetail.text = "Log in to " + req.optString("pos_name").ifEmpty { "the POS" }
        }
        getSystemService(NotificationManager::class.java).cancel(PollService.REQUEST_ID)
        begin() // go straight to the fingerprint prompt
    }

    private fun fail(msg: String) {
        busy = false
        tvStatus.text = msg
    }

    private fun begin() {
        if (busy) return
        val can = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            fail("No fingerprint is enrolled on this phone (or it is not supported). Add one in Android settings.")
            return
        }
        busy = true
        tvStatus.text = ""
        try {
            if (kind == "register") {
                sign(KeyStoreHelper.createKey(), true)
            } else {
                val ids = req.optJSONArray("credential_ids")
                val mine = KeyStoreHelper.aliases().toSet()
                val matches = (0 until (ids?.length() ?: 0)).map { ids!!.getString(it) }.filter { it in mine }
                when {
                    matches.isEmpty() -> fail("No fingerprint registered on this phone for this POS.")
                    matches.size == 1 -> sign(matches[0], false)
                    else -> {
                        val names = matches.map { prefs.employeeName(it).ifEmpty { it.takeLast(6) } }.toTypedArray()
                        AlertDialog.Builder(this)
                            .setTitle("Log in as")
                            .setItems(names) { _, i -> sign(matches[i], false) }
                            .setOnCancelListener { busy = false }
                            .show()
                    }
                }
            }
        } catch (e: Exception) {
            fail("Could not start: " + e.message)
        }
    }

    private fun sign(alias: String, isNew: Boolean) {
        val signature = try {
            KeyStoreHelper.signatureFor(alias)
        } catch (e: Exception) {
            if (isNew) KeyStoreHelper.delete(alias)
            fail("Fingerprint key unavailable (were fingerprints changed on this phone?). Register again in Odoo.")
            return
        }
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val sig = result.cryptoObject?.signature
                    if (sig == null) {
                        if (isNew) KeyStoreHelper.delete(alias)
                        fail("Fingerprint check failed.")
                        return
                    }
                    try {
                        sig.update("$kind:$token:$challenge".toByteArray())
                        send(alias, isNew, KeyStoreHelper.b64url(sig.sign()))
                    } catch (e: Exception) {
                        if (isNew) KeyStoreHelper.delete(alias)
                        fail("Signing failed: " + e.message)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (isNew) KeyStoreHelper.delete(alias)
                    fail(errString.toString())
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (isNew) "Register fingerprint" else "Approve POS login")
            .setSubtitle(tvDetail.text.toString())
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(signature))
    }

    private fun send(alias: String, isNew: Boolean, signed: String) {
        tvStatus.text = "Sending…"
        Thread {
            try {
                val payload = JSONObject().put("credential_id", alias).put("signature", signed)
                if (isNew) payload.put("public_key", KeyStoreHelper.publicKeyB64(alias))
                val res = Api.call(
                    prefs, "/pos_fp/app/complete",
                    JSONObject().put("token", token).put("action", "approve").put("payload", payload)
                )
                runOnUiThread {
                    if (res.optBoolean("ok", false)) {
                        if (isNew) prefs.setEmployeeName(alias, employee)
                        RequestState.handled.add(token)
                        tvTitle.text = "Done ✔"
                        tvDetail.text = ""
                        tvStatus.text = ""
                        handler.postDelayed({ finish() }, 1200)
                    } else {
                        if (isNew) KeyStoreHelper.delete(alias)
                        fail(res.optString("error", "Failed"))
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isNew) KeyStoreHelper.delete(alias)
                    fail(e.message ?: "Network error")
                }
            }
        }.start()
    }

    private fun reject() {
        RequestState.handled.add(token)
        if (kind == "register") {
            Thread {
                try {
                    Api.call(
                        prefs, "/pos_fp/app/complete",
                        JSONObject().put("token", token).put("action", "reject")
                    )
                } catch (_: Exception) {
                }
            }.start()
        }
        finish()
    }
}
