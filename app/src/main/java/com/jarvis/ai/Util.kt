package com.jarvis.ai

import androidx.appcompat.app.AppCompatDelegate
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Util {

    /** Script dekh kar language guess: bn / hi / ar ya null (Latin). */
    fun langOf(t: String): String? {
        var bn = 0
        var hi = 0
        var ar = 0
        for (c in t) {
            val code = c.code
            if (code in 0x0980..0x09FF) bn++
            else if (code in 0x0900..0x097F) hi++
            else if (code in 0x0600..0x06FF) ar++
        }
        return when {
            bn >= 3 && bn >= hi && bn >= ar -> "bn"
            hi >= 3 && hi >= ar -> "hi"
            ar >= 3 -> "ar"
            else -> null
        }
    }

    /** TTS ke liye markdown/links hatao. */
    fun speechText(t: String): String =
        t.replace(Regex("https?://\\S+"), "link")
            .replace(Regex("[*_`#>~]+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun applyTheme(p: Prefs) {
        AppCompatDelegate.setDefaultNightMode(
            when (p.theme) {
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    /** Simple blocking HTTP. body == null -> GET, warna POST. */
    fun http(url: String, headers: Map<String, String>, body: ByteArray?): Pair<Int, ByteArray> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 20000
            c.readTimeout = 90000
            c.requestMethod = if (body != null) "POST" else "GET"
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val stream = if (code < 400) c.inputStream else c.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            return Pair(code, bytes)
        } finally {
            c.disconnect()
        }
    }

    fun errorMessage(bytes: ByteArray, code: Int): String {
        try {
            val msg = JSONObject(String(bytes)).optJSONObject("error")?.optString("message") ?: ""
            if (msg.isNotBlank()) return "HTTP $code: $msg"
        } catch (e: Exception) {
            // ignore
        }
        return "HTTP $code"
    }
}
