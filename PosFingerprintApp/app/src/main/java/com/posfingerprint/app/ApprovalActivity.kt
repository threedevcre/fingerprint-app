package com.posfingerprint.app

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.NotificationManager
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import java.util.Date

/** Opened from the notification: scan the fingerprint to register a key or approve a POS login. */
class ApprovalActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var req: JSONObject

    private lateinit var ringOuter: View
    private lateinit var ringInner: View
    private lateinit var fpCircle: View
    private lateinit var ivFp: ImageView
    private lateinit var tvKind: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvDetail: TextView
    private lateinit var tvStatus: TextView
    private lateinit var cardDetails: View
    private lateinit var btnScan: MaterialButton
    private lateinit var btnCancel: MaterialButton

    private var kind = ""
    private var token = ""
    private var challenge = ""
    private var employee = ""
    private var posName = ""
    private var promptSubtitle = ""
    private var busy = false
    private var expired = false
    private var receivedAt = 0L
    private var pulse: AnimatorSet? = null
    private val handler = Handler(Looper.getMainLooper())

    private val isRegister get() = kind == "register"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Security.protect(this)
        setContentView(R.layout.activity_approval)
        prefs = Prefs(this)

        ringOuter = findViewById(R.id.ringOuter)
        ringInner = findViewById(R.id.ringInner)
        fpCircle = findViewById(R.id.fpCircle)
        ivFp = findViewById(R.id.ivFp)
        tvKind = findViewById(R.id.tvKind)
        tvTitle = findViewById(R.id.tvTitle)
        tvDetail = findViewById(R.id.tvDetail)
        tvStatus = findViewById(R.id.tvStatus)
        cardDetails = findViewById(R.id.cardDetails)
        btnScan = findViewById(R.id.btnScan)
        btnCancel = findViewById(R.id.btnCancel)

        btnScan.setOnClickListener { begin() }
        btnCancel.setOnClickListener { reject() }
        load()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handler.removeCallbacksAndMessages(null)
        load()
    }

    override fun onDestroy() {
        pulse?.cancel()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- setup

    private fun load() {
        val raw = intent.getStringExtra("request")
        if (raw == null) {
            finish()
            return
        }
        val parsed = try {
            JSONObject(raw)
        } catch (_: Exception) {
            null
        }
        if (parsed == null || !Security.isValidRequest(parsed)) {
            finish()
            return
        }
        req = parsed
        receivedAt = intent.getLongExtra("received_at", System.currentTimeMillis())
        expired = false
        kind = req.getString("kind")
        token = req.getString("token")
        challenge = req.getString("challenge")
        employee = req.optString("employee")
        posName = req.optString("pos_name").ifEmpty { Prefs.DEFAULT_POS }
        busy = false

        val who: String
        if (isRegister) {
            tvKind.text = "Fingerprint setup"
            tvTitle.text = "Register your fingerprint"
            tvDetail.text = "Odoo wants to link a fingerprint on this phone to ${employee.ifEmpty { "an employee" }}."
            promptSubtitle = "For ${employee.ifEmpty { "this employee" }}"
            who = employee.ifEmpty { "Not specified" }
            btnCancel.text = "Decline"
        } else {
            tvKind.text = "POS login request"
            tvTitle.text = "Approve login"
            tvDetail.text = "Someone is signing in to $posName. Scan to confirm it's you."
            promptSubtitle = "Log in to $posName"
            val matches = myMatches()
            who = when (matches.size) {
                0 -> "No fingerprint on this phone"
                1 -> prefs.employeeName(matches[0]).ifEmpty { "Registered cashier" }
                else -> "${matches.size} cashiers, choose after scan"
            }
            btnCancel.text = "Reject"
        }

        detail(R.id.rowEmployee, "Employee", who)
        detail(R.id.rowTarget, if (isRegister) "Action" else "Point of Sale", if (isRegister) "Link fingerprint" else posName)
        detail(R.id.rowServer, "Server", prefs.host.ifEmpty { "Unknown" })
        detail(R.id.rowTime, "Requested at", DateFormat.getTimeFormat(this).format(Date()))

        cardDetails.isVisible = true
        btnScan.isVisible = true
        btnCancel.isVisible = true
        showReady("Touch the fingerprint sensor when asked")

        getSystemService(NotificationManager::class.java).cancel(PollService.REQUEST_ID)
        begin() // go straight to the fingerprint prompt
    }

    private fun myMatches(): List<String> {
        val ids = req.optJSONArray("credential_ids") ?: return emptyList()
        val mine = try {
            KeyStoreHelper.aliases().toSet()
        } catch (_: Exception) {
            emptySet()
        }
        return (0 until ids.length()).map { ids.getString(it) }.filter { it in mine }
    }

    private fun detail(rowId: Int, label: String, value: String) {
        val row = findViewById<View>(rowId)
        row.findViewById<TextView>(R.id.detailLabel).text = label
        row.findViewById<TextView>(R.id.detailValue).text = value
    }

    // ---------------------------------------------------------------- visual states

    private fun showReady(message: String) {
        setCircle(null, R.drawable.ic_fingerprint)
        startPulse()
        tvStatus.setTextColor(getColor(R.color.text_secondary))
        tvStatus.text = message
        btnScan.isEnabled = true
        btnScan.text = "Scan fingerprint"
    }

    private fun showWorking(message: String) {
        tvStatus.setTextColor(getColor(R.color.text_secondary))
        tvStatus.text = message
        btnScan.isEnabled = false
    }

    private fun fail(msg: String) {
        busy = false
        stopPulse()
        setCircle(getColor(R.color.error), R.drawable.ic_fingerprint)
        tvStatus.setTextColor(getColor(R.color.error))
        tvStatus.text = msg
        btnScan.isEnabled = true
        btnScan.text = "Try again"
    }

    private fun showSuccess(title: String, detail: String) {
        stopPulse()
        setCircle(getColor(R.color.success), R.drawable.ic_check)
        tvKind.text = "Done"
        tvTitle.text = title
        tvDetail.text = detail
        tvStatus.text = ""
        cardDetails.isVisible = false
        btnScan.isVisible = false
        btnCancel.isVisible = false
        fpCircle.scaleX = 0.85f
        fpCircle.scaleY = 0.85f
        fpCircle.animate().scaleX(1f).scaleY(1f).setDuration(280).setInterpolator(DecelerateInterpolator()).start()
        handler.postDelayed({ finish() }, 1600)
    }

    private fun setCircle(color: Int?, icon: Int) {
        fpCircle.backgroundTintList = color?.let { ColorStateList.valueOf(it) }
        ivFp.setImageResource(icon)
    }

    private fun startPulse() {
        if (pulse?.isRunning == true) return
        fun ring(v: View, delay: Long): Animator {
            val sx = ObjectAnimator.ofFloat(v, View.SCALE_X, 0.62f, 1.1f)
            val sy = ObjectAnimator.ofFloat(v, View.SCALE_Y, 0.62f, 1.1f)
            val a = ObjectAnimator.ofFloat(v, View.ALPHA, 0.7f, 0f)
            listOf(sx, sy, a).forEach {
                it.duration = 2000
                it.startDelay = delay
                it.repeatCount = ValueAnimator.INFINITE
                it.interpolator = DecelerateInterpolator()
            }
            return AnimatorSet().apply { playTogether(sx, sy, a) }
        }
        pulse = AnimatorSet().apply {
            playTogether(ring(ringOuter, 0), ring(ringInner, 1000))
            start()
        }
    }

    private fun stopPulse() {
        pulse?.cancel()
        pulse = null
        ringOuter.alpha = 0f
        ringInner.alpha = 0f
    }

    // ---------------------------------------------------------------- fingerprint flow

    private fun isTooOld(): Boolean =
        System.currentTimeMillis() - receivedAt > Security.REQUEST_MAX_AGE_MS

    private fun showExpired() {
        expired = true
        busy = false
        stopPulse()
        setCircle(getColor(R.color.text_tertiary), R.drawable.ic_history)
        tvStatus.setTextColor(getColor(R.color.text_secondary))
        tvStatus.text = "This request expired. Start a new one from the POS or Odoo."
        btnScan.isVisible = false
        btnCancel.text = "Close"
    }

    private fun begin() {
        if (busy || expired) return
        if (isTooOld()) {
            showExpired()
            return
        }
        val can = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            fail("No fingerprint is enrolled on this phone, or the sensor isn't supported. Add one in Android settings.")
            return
        }
        busy = true
        showReady("Touch the fingerprint sensor")
        try {
            if (isRegister) {
                sign(KeyStoreHelper.createKey(), true)
            } else {
                val matches = myMatches()
                when {
                    matches.isEmpty() -> fail("This phone has no fingerprint registered for this POS.")
                    matches.size == 1 -> sign(matches[0], false)
                    else -> {
                        val names = matches.map { prefs.employeeName(it).ifEmpty { "Cashier …" + it.takeLast(4) } }
                            .toTypedArray()
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Log in as")
                            .setItems(names) { _, i -> sign(matches[i], false) }
                            .setOnCancelListener { busy = false }
                            .show()
                    }
                }
            }
        } catch (e: Exception) {
            fail("Couldn't start the scan: " + e.message)
        }
    }

    private fun sign(alias: String, isNew: Boolean) {
        val signature = try {
            KeyStoreHelper.signatureFor(alias)
        } catch (e: Exception) {
            if (isNew) KeyStoreHelper.delete(alias)
            fail("This fingerprint key no longer works, usually because fingerprints changed on the phone. Register again from Odoo.")
            return
        }
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val sig = result.cryptoObject?.signature
                    if (sig == null) {
                        if (isNew) KeyStoreHelper.delete(alias)
                        fail("The fingerprint check didn't complete. Try again.")
                        return
                    }
                    if (isTooOld()) {
                        if (isNew) KeyStoreHelper.delete(alias)
                        showExpired()
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
                    val cancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    if (cancelled) {
                        busy = false
                        showReady("Scan cancelled. Tap Scan fingerprint to try again.")
                    } else {
                        fail(errString.toString())
                    }
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (isNew) "Register fingerprint" else "Approve POS login")
            .setSubtitle(promptSubtitle)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(signature))
    }

    private fun send(alias: String, isNew: Boolean, signed: String) {
        stopPulse()
        showWorking("Confirming with Odoo…")
        Thread {
            try {
                val payload = JSONObject().put("credential_id", alias).put("signature", signed)
                if (isNew) payload.put("public_key", KeyStoreHelper.publicKeyB64(alias))
                val res = Api.call(
                    prefs, "/pos_fp/app/complete",
                    JSONObject().put("token", token).put("action", "approve").put("payload", payload)
                )
                val ok = res.optBoolean("ok", false)
                if (ok) {
                    RequestState.handled.add(token)
                    if (isNew) prefs.recordRegistration(alias, employee) else prefs.recordLogin(alias, posName)
                } else {
                    if (isNew) KeyStoreHelper.delete(alias)
                    prefs.recordFailed(res.optString("error", "Rejected by Odoo"))
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (ok) {
                        if (isNew) {
                            showSuccess(
                                "Fingerprint registered",
                                "${employee.ifEmpty { "This employee" }} can now sign in to the POS with this phone."
                            )
                        } else {
                            val name = prefs.employeeName(alias).ifEmpty { "You" }
                            showSuccess("Login approved", "$name is being signed in to $posName.")
                        }
                    } else {
                        fail(res.optString("error", "Odoo didn't accept the approval."))
                    }
                }
            } catch (e: Exception) {
                if (isNew) KeyStoreHelper.delete(alias)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    fail("Couldn't reach Odoo: " + (e.message ?: "network error"))
                }
            }
        }.start()
    }

    private fun reject() {
        if (expired) {
            RequestState.handled.add(token)
            finish()
            return
        }
        RequestState.handled.add(token)
        prefs.recordRejected(isRegister, if (isRegister) employee.ifEmpty { "Employee" } else posName)
        if (isRegister) {
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
