package com.posfingerprint.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.util.Calendar

/**
 * Everything the app remembers, stored privately in the app sandbox:
 * the one-time Odoo connection, the fingerprint profiles on this phone,
 * the POS terminals they were used on, and a short activity log.
 */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("posfp", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- connection

    var serverUrl: String
        get() = sp.getString("url", "") ?: ""
        set(v) = sp.edit().putString("url", v.trim().trimEnd('/')).apply()

    /**
     * The Odoo API key, encrypted at rest with a hardware-backed key (see SecureStore).
     * Keys saved in plain text by version 1.0 are encrypted the first time they are read.
     */
    var apiKey: String
        get() {
            cachedKey?.let { return it }
            val raw = sp.getString("key", "") ?: ""
            if (raw.isEmpty()) return ""
            val plain = if (SecureStore.isEncrypted(raw)) {
                SecureStore.decrypt(raw) ?: ""
            } else {
                // Migrate a 1.0 plain-text key.
                try {
                    sp.edit().putString("key", SecureStore.encrypt(raw)).commit()
                } catch (_: Exception) {
                }
                raw
            }
            if (plain.isNotEmpty()) cachedKey = plain
            return plain
        }
        set(v) {
            val t = v.trim()
            cachedKey = null
            if (t.isEmpty()) {
                sp.edit().remove("key").commit()
            } else {
                sp.edit().putString("key", SecureStore.encrypt(t)).commit()
                cachedKey = t
            }
        }

    var database: String
        get() = sp.getString("db", "") ?: ""
        set(v) = sp.edit().putString("db", v.trim()).apply()

    /** Name of this phone's key in Odoo (returned by the ping endpoint). */
    var deviceName: String
        get() = sp.getString("device", "") ?: ""
        set(v) = sp.edit().putString("device", v.trim()).apply()

    var connectedAt: Long
        get() = sp.getLong("since", 0L)
        set(v) = sp.edit().putLong("since", v).apply()

    val connected: Boolean get() = serverUrl.isNotEmpty() && apiKey.isNotEmpty()

    /** App.THEME_SYSTEM, THEME_LIGHT or THEME_DARK. */
    var themeMode: Int
        get() = sp.getInt("theme", App.THEME_SYSTEM)
        set(v) = sp.edit().putInt("theme", v).apply()

    /** "erp.company.com" instead of the full URL, for display. */
    val host: String
        get() = try {
            URI(serverUrl).host ?: serverUrl
        } catch (_: Exception) {
            serverUrl
        }

    fun clearConnection() {
        cachedKey = null
        sp.edit().remove("url").remove("key").remove("db").remove("device").remove("since").commit()
        bump()
    }

    /** Wipes everything: connection, encrypted key, fingerprint keys, history and terminals. */
    fun eraseAll() {
        cachedKey = null
        KeyStoreHelper.deleteAll()
        SecureStore.deleteKey()
        sp.edit().clear().commit()
        bump()
    }

    // ---------------------------------------------------------------- change tracking

    /** Increases whenever stored data changes, so screens know when to redraw. */
    val revision: Int get() = sp.getInt("rev", 0)

    fun bump() {
        sp.edit().putInt("rev", revision + 1).apply()
    }

    // ---------------------------------------------------------------- models

    data class Profile(
        val alias: String,
        val name: String,
        val createdAt: Long,
        val lastUsed: Long,
        val uses: Int,
        val terminals: List<String>,
        /** KIND_USER (Odoo user login), KIND_EMPLOYEE (POS cashier) or "" if unknown (older versions). */
        val kind: String,
        val webUses: Int,
    )

    /** Odoo web sign-ins approved from this phone. */
    data class WebStats(val count: Int, val lastAt: Long, val lastUser: String)

    data class Terminal(val name: String, val lastUsed: Long, val lastEmployee: String, val logins: Int)

    data class Event(val type: String, val title: String, val detail: String, val time: Long)

    private fun readObj(key: String): JSONObject = try {
        JSONObject(sp.getString(key, null) ?: "{}")
    } catch (_: JSONException) {
        JSONObject()
    }

    private fun readArr(key: String): JSONArray = try {
        JSONArray(sp.getString(key, null) ?: "[]")
    } catch (_: JSONException) {
        JSONArray()
    }

    // ---------------------------------------------------------------- profiles

    /** Employee name for a fingerprint key (also reads the storage used by version 1.0). */
    fun employeeName(alias: String): String {
        val name = readObj("profiles").optJSONObject(alias)?.optString("name").orEmpty()
        return name.ifEmpty { readObj("names").optString(alias, "") }
    }

    /** One entry per fingerprint key that really exists in this phone's keystore. */
    fun profiles(): List<Profile> {
        val stored = readObj("profiles")
        val legacy = readObj("names")
        val aliases = try {
            KeyStoreHelper.aliases()
        } catch (_: Exception) {
            emptyList()
        }
        return aliases.map { alias ->
            val p = stored.optJSONObject(alias)
            val pos = p?.optJSONArray("pos")
            Profile(
                alias = alias,
                name = p?.optString("name").orEmpty().ifEmpty { legacy.optString(alias, "") },
                createdAt = p?.optLong("created") ?: 0L,
                lastUsed = p?.optLong("last") ?: 0L,
                uses = p?.optInt("uses") ?: 0,
                terminals = if (pos == null) emptyList()
                else (0 until pos.length()).map { pos.optString(it) }.filter { it.isNotEmpty() },
                kind = p?.optString("kind").orEmpty(),
                webUses = p?.optInt("web") ?: 0,
            )
        }.sortedWith(compareByDescending<Profile> { maxOf(it.lastUsed, it.createdAt) }.thenBy { it.name })
    }

    fun recordRegistration(alias: String, employee: String, kind: String = "") {
        val all = readObj("profiles")
        all.put(
            alias, JSONObject()
                .put("name", employee)
                .put("kind", kind)
                .put("created", System.currentTimeMillis())
                .put("last", 0L)
                .put("uses", 0)
                .put("pos", JSONArray())
        )
        sp.edit().putString("profiles", all.toString()).apply()
        addEvent(
            EV_REGISTER,
            if (kind == KIND_USER) "Odoo login set up" else "Fingerprint registered",
            employee.ifEmpty { "New profile" }
        )
    }

    fun recordLogin(alias: String, posName: String) {
        val now = System.currentTimeMillis()
        val pos = posName.ifEmpty { DEFAULT_POS }
        val name = employeeName(alias).ifEmpty { "Cashier" }

        val all = readObj("profiles")
        val p = all.optJSONObject(alias) ?: JSONObject().put("name", name).put("created", 0L)
        p.put("last", now).put("uses", p.optInt("uses") + 1)
        val list = p.optJSONArray("pos") ?: JSONArray()
        if ((0 until list.length()).none { list.optString(it) == pos }) list.put(pos)
        p.put("pos", list)
        all.put(alias, p)

        val terms = readObj("terminals")
        val t = terms.optJSONObject(pos) ?: JSONObject()
        t.put("last", now).put("emp", name).put("count", t.optInt("count") + 1)
        terms.put(pos, t)

        val today = dayKey()
        val count = if (sp.getString("today", "") == today) sp.getInt("today_n", 0) else 0
        sp.edit()
            .putString("profiles", all.toString())
            .putString("terminals", terms.toString())
            .putString("today", today)
            .putInt("today_n", count + 1)
            .apply()
        addEvent(EV_LOGIN, "Login approved", "$name on $pos")
    }

    /** An approved Odoo web sign-in. Kept apart from POS terminals. */
    fun recordWebLogin(alias: String) {
        val now = System.currentTimeMillis()
        val name = employeeName(alias).ifEmpty { "User" }
        val all = readObj("profiles")
        val p = all.optJSONObject(alias) ?: JSONObject().put("name", name).put("created", 0L)
        p.put("last", now).put("uses", p.optInt("uses") + 1).put("web", p.optInt("web") + 1)
        all.put(alias, p)
        val today = dayKey()
        val count = if (sp.getString("today", "") == today) sp.getInt("today_n", 0) else 0
        sp.edit()
            .putString("profiles", all.toString())
            .putInt("web_n", sp.getInt("web_n", 0) + 1)
            .putLong("web_last", now)
            .putString("web_user", name)
            .putString("today", today)
            .putInt("today_n", count + 1)
            .apply()
        addEvent(EV_WEB_LOGIN, "Odoo sign-in approved", name)
    }

    fun webStats(): WebStats =
        WebStats(sp.getInt("web_n", 0), sp.getLong("web_last", 0L), sp.getString("web_user", "") ?: "")

    /** Deletes the key from this phone. Odoo keeps its record until removed there. */
    fun removeProfile(alias: String) {
        val name = employeeName(alias)
        KeyStoreHelper.delete(alias)
        val all = readObj("profiles").apply { remove(alias) }
        val legacy = readObj("names").apply { remove(alias) }
        sp.edit().putString("profiles", all.toString()).putString("names", legacy.toString()).apply()
        addEvent(EV_REMOVED, "Fingerprint removed", name.ifEmpty { "Profile" })
    }

    // ---------------------------------------------------------------- terminals & activity

    fun terminals(): List<Terminal> {
        val o = readObj("terminals")
        return o.keys().asSequence().mapNotNull { k ->
            val t = o.optJSONObject(k) ?: return@mapNotNull null
            Terminal(k, t.optLong("last"), t.optString("emp"), t.optInt("count"))
        }.sortedByDescending { it.lastUsed }.toList()
    }

    fun approvalsToday(): Int = if (sp.getString("today", "") == dayKey()) sp.getInt("today_n", 0) else 0

    fun events(limit: Int = 8): List<Event> {
        val a = readArr("activity")
        return (0 until minOf(a.length(), limit)).mapNotNull { i ->
            val e = a.optJSONObject(i) ?: return@mapNotNull null
            Event(e.optString("type"), e.optString("title"), e.optString("detail"), e.optLong("time"))
        }
    }

    fun recordRejected(isRegister: Boolean, detail: String) =
        addEvent(EV_REJECT, if (isRegister) "Setup declined" else "Login rejected", detail)

    fun recordFailed(detail: String) = addEvent(EV_FAILED, "Approval failed", detail)

    fun clearHistory() {
        sp.edit().remove("activity").apply()
        bump()
    }

    private fun addEvent(type: String, title: String, detail: String) {
        synchronized(LOCK) {
            val old = readArr("activity")
            val next = JSONArray().put(
                JSONObject()
                    .put("type", type)
                    .put("title", title)
                    .put("detail", detail)
                    .put("time", System.currentTimeMillis())
            )
            for (i in 0 until minOf(old.length(), MAX_EVENTS - 1)) next.put(old.opt(i))
            sp.edit().putString("activity", next.toString()).putInt("rev", revision + 1).apply()
        }
    }

    private fun dayKey(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
    }

    companion object {
        const val EV_REGISTER = "register"
        const val EV_LOGIN = "login"
        const val EV_REJECT = "reject"
        const val EV_FAILED = "failed"
        const val EV_REMOVED = "removed"
        const val EV_WEB_LOGIN = "web_login"
        const val KIND_USER = "user"
        const val KIND_EMPLOYEE = "employee"
        const val DEFAULT_POS = "Point of Sale"
        private const val MAX_EVENTS = 50
        private val LOCK = Any()

        /** Decrypted API key kept in memory only, so the keystore isn't hit on every poll. */
        @Volatile
        private var cachedKey: String? = null
    }
}
