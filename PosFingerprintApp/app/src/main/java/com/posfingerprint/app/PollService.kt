package com.posfingerprint.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.json.JSONObject

/** Keeps the phone "online" in Odoo and raises a notification when a fingerprint request arrives. */
class PollService : Service() {

    @Volatile private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Connection", NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REQUESTS, "Fingerprint requests", NotificationManager.IMPORTANCE_HIGH)
                .apply { enableVibration(true) }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val host = Prefs(this).host
        val notification: Notification = NotificationCompat.Builder(this, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_fingerprint)
            .setColor(getColor(R.color.ink))
            .setContentTitle("Ready for POS logins")
            .setContentText(if (host.isEmpty()) "Waiting for requests" else "Connected to $host")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ONGOING_ID, notification)
        }
        if (!running) {
            running = true
            Thread { loop() }.start()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        RequestState.online = false
        super.onDestroy()
    }

    private fun loop() {
        val prefs = Prefs(this)
        var lastToken = ""
        while (running) {
            try {
                val res = Api.call(prefs, "/pos_fp/app/poll")
                if (!res.optBoolean("ok", false)) {
                    RequestState.online = false
                    RequestState.lastError = res.optString("error", "Rejected by server")
                } else {
                    RequestState.online = true
                    RequestState.lastError = ""
                    val req = res.optJSONObject("request")
                    if (req == null) {
                        if (lastToken.isNotEmpty()) {
                            getSystemService(NotificationManager::class.java).cancel(REQUEST_ID)
                            lastToken = ""
                        }
                    } else {
                        val token = req.getString("token")
                        if (token != lastToken && token !in RequestState.handled && isForThisPhone(req)) {
                            lastToken = token
                            notifyRequest(req)
                        }
                    }
                }
            } catch (e: Exception) {
                RequestState.online = false
                RequestState.lastError = e.message ?: "Network error"
            }
            try {
                Thread.sleep(3000)
            } catch (_: InterruptedException) {
            }
        }
    }

    /** A login request lists every registered credential; only react if one of them lives on this phone. */
    private fun isForThisPhone(req: JSONObject): Boolean {
        if (req.getString("kind") == "register") return true
        val ids = req.optJSONArray("credential_ids") ?: return false
        val mine = KeyStoreHelper.aliases().toSet()
        for (i in 0 until ids.length()) {
            if (ids.getString(i) in mine) return true
        }
        return false
    }

    private fun notifyRequest(req: JSONObject) {
        val isRegister = req.getString("kind") == "register"
        val title = if (isRegister) "Register your fingerprint" else "POS login request"
        val text = if (isRegister) {
            "Odoo is linking a fingerprint for " + req.optString("employee").ifEmpty { "an employee" }
        } else {
            "Tap to approve the login to " + req.optString("pos_name").ifEmpty { "the POS" }
        }
        val intent = Intent(this, ApprovalActivity::class.java)
            .putExtra("request", req.toString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            this, req.getString("token").hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CH_REQUESTS)
            .setSmallIcon(R.drawable.ic_stat_fingerprint)
            .setColor(getColor(R.color.signal))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setTimeoutAfter(90_000)
            .build()
        getSystemService(NotificationManager::class.java).notify(REQUEST_ID, n)
    }

    companion object {
        const val CH_SERVICE = "posfp_service"
        const val CH_REQUESTS = "posfp_requests"
        const val ONGOING_ID = 1
        const val REQUEST_ID = 2
    }
}
