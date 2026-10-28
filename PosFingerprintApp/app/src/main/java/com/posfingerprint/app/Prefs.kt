package com.posfingerprint.app

import android.content.Context
import org.json.JSONObject

/** Connection settings, stored privately in the app sandbox. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("posfp", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString("url", "") ?: ""
        set(v) = sp.edit().putString("url", v.trim().trimEnd('/')).apply()

    var apiKey: String
        get() = sp.getString("key", "") ?: ""
        set(v) = sp.edit().putString("key", v.trim()).apply()

    var database: String
        get() = sp.getString("db", "") ?: ""
        set(v) = sp.edit().putString("db", v.trim()).apply()

    val connected: Boolean get() = serverUrl.isNotEmpty() && apiKey.isNotEmpty()

    fun clearConnection() {
        sp.edit().remove("url").remove("key").remove("db").apply()
    }

    /** Employee name remembered for each fingerprint key (used when one phone holds several cashiers). */
    fun employeeName(alias: String): String =
        JSONObject(sp.getString("names", "{}") ?: "{}").optString(alias, "")

    fun setEmployeeName(alias: String, name: String) {
        val o = JSONObject(sp.getString("names", "{}") ?: "{}")
        o.put(alias, name)
        sp.edit().putString("names", o.toString()).apply()
    }
}
