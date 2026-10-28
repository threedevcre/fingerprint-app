package com.posfingerprint.app

import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object Api {
    class ApiException(message: String) : Exception(message)

    private const val MAX_RESPONSE_BYTES = 512 * 1024

    /** POST a JSON-RPC call to the module. The API key goes in the Authorization header. */
    fun call(prefs: Prefs, path: String, params: JSONObject = JSONObject()): JSONObject {
        val base = prefs.serverUrl
        Security.addressProblem(base)?.let { throw ApiException(it) }
        val key = prefs.apiKey
        if (key.isEmpty()) throw ApiException("This phone isn't connected. Connect it in Settings.")

        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.doOutput = true
            conn.useCaches = false
            // Never follow redirects: they could send the API key to another server.
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Cache-Control", "no-store")
            conn.setRequestProperty("Authorization", "Bearer $key")
            if (prefs.database.isNotEmpty()) {
                conn.setRequestProperty("X-Odoo-Database", prefs.database)
            }
            val body = JSONObject()
                .put("jsonrpc", "2.0")
                .put("method", "call")
                .put("params", params)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code in 300..399) {
                throw ApiException("The server tried to redirect the app. Enter the final https:// address of your Odoo.")
            }
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { readLimited(it) } ?: ""
            val json = try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw ApiException("Unexpected response from server (HTTP $code). Check the address and that the module is installed.")
            }
            if (json.has("error")) {
                val err = json.optJSONObject("error") ?: JSONObject()
                val msg = err.optJSONObject("data")?.optString("message").orEmpty().ifEmpty { err.optString("message") }
                throw ApiException(msg.take(300).ifEmpty { "The server returned an error." })
            }
            return json.optJSONObject("result") ?: JSONObject()
        } finally {
            conn.disconnect()
        }
    }

    private fun readLimited(input: InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_RESPONSE_BYTES) throw ApiException("The server response was too large.")
            out.write(buf, 0, n)
        }
        return out.toString("UTF-8")
    }
}
