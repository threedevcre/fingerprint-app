package com.posfingerprint.app

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import org.json.JSONObject
import java.net.URI

/** Central security rules: which server addresses are allowed and which requests are acceptable. */
object Security {

    /** Requests older than this can no longer be approved from the phone. */
    const val REQUEST_MAX_AGE_MS = 120_000L

    // ------------------------------------------------------------ server address policy

    /**
     * True only for literal private-network addresses. We never resolve DNS here, so a
     * public name can't pretend to be local.
     */
    fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        if (h == "localhost" || h.endsWith(".local")) return true
        val p = h.split(".")
        if (p.size != 4) return false
        val o = p.map { it.toIntOrNull() ?: return false }
        if (o.any { it !in 0..255 }) return false
        return o[0] == 10 ||
            o[0] == 127 ||
            (o[0] == 192 && o[1] == 168) ||
            (o[0] == 172 && o[1] in 16..31)
    }

    /** Null when the address is acceptable, otherwise a message telling the user what to fix. */
    fun addressProblem(url: String): String? {
        val u = try {
            URI(url)
        } catch (_: Exception) {
            return "This isn't a valid web address."
        }
        val scheme = u.scheme?.lowercase()
        val host = u.host
        if (host.isNullOrEmpty()) return "Enter the address you use to open Odoo."
        if (u.rawUserInfo != null) return "Remove the user name or password from the address."
        if (u.rawQuery != null || u.rawFragment != null) return "Remove anything after ? or # from the address."
        return when {
            scheme == "https" -> null
            scheme == "http" && isPrivateHost(host) -> null
            scheme == "http" ->
                "Use https://. Unencrypted http:// is only allowed for local network addresses such as 192.168.x.x."
            else -> "Only https:// addresses are supported."
        }
    }

    fun isEncrypted(url: String): Boolean = url.lowercase().startsWith("https://")

    // ------------------------------------------------------------ request validation

    private val SAFE_TOKEN = Regex("^[A-Za-z0-9._~+/=-]{8,512}$")

    /** Rejects malformed or oversized requests before they reach a notification or a signature. */
    fun isValidRequest(req: JSONObject): Boolean {
        val kind = req.optString("kind")
        if (kind != "register" && kind != "login") return false
        if (!SAFE_TOKEN.matches(req.optString("token"))) return false
        if (!SAFE_TOKEN.matches(req.optString("challenge"))) return false
        if (req.optString("employee").length > 200) return false
        if (req.optString("pos_name").length > 200) return false
        if (kind == "login") {
            val ids = req.optJSONArray("credential_ids") ?: return false
            if (ids.length() > 200) return false
        }
        return true
    }

    // ------------------------------------------------------------ screen protection

    /** Blocks screenshots, screen recording and the recent-apps preview; hides overlay apps. */
    fun protect(activity: Activity) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        if (Build.VERSION.SDK_INT >= 31) activity.window.setHideOverlayWindows(true)
    }
}
