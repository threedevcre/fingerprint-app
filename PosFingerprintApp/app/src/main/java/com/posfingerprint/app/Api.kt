package com.posfingerprint.app

import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Api {
    class ApiException(message: String) : Exception(message)

    /** POST a JSON-RPC call to the module. The API key goes in the Authorization header. */
    fun call(prefs: Prefs, path: String, params: JSONObject = JSONObject()): JSONObject {
        val conn = URL(prefs.serverUrl + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer " + prefs.apiKey)
            if (prefs.database.isNotEmpty()) {
                conn.setRequestProperty("X-Odoo-Database", prefs.database)
            }
            val body = JSONObject()
                .put("jsonrpc", "2.0")
                .put("method", "call")
                .put("params", params)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val json = try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw ApiException("Unexpected response from server (HTTP $code). Check the URL and that the module is installed.")
            }
            if (json.has("error")) {
                val err = json.getJSONObject("error")
                throw ApiException(err.optJSONObject("data")?.optString("message") ?: err.optString("message"))
            }
            return json.optJSONObject("result") ?: JSONObject()
        } finally {
            conn.disconnect()
        }
    }
}
