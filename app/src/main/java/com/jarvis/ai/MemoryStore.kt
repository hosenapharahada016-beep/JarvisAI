package com.jarvis.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Memory(val id: String, var text: String, val ts: Long)

/**
 * Long-term memory. Entries tab tak rehte hain jab tak user delete na kare
 * (30 din ki minimum requirement se zyada). Sensitive cheezein kabhi save nahi hoti.
 */
class MemoryStore(ctx: Context) {
    private val file = File(ctx.filesDir, "memory.json")
    private val items = mutableListOf<Memory>()

    init { load() }

    private fun load() {
        items.clear()
        try {
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(Memory(o.getString("id"), o.getString("text"), o.optLong("ts", 0L)))
            }
        } catch (e: Exception) {
            // corrupt file: ignore
        }
    }

    private fun save() {
        val arr = JSONArray()
        for (m in items) arr.put(JSONObject().put("id", m.id).put("text", m.text).put("ts", m.ts))
        file.writeText(arr.toString())
    }

    fun all(): List<Memory> = items.toList()

    /** false return = sensitive laga, save nahi kiya. */
    fun add(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || isSensitive(t)) return false
        if (items.any { it.text.equals(t, ignoreCase = true) }) return true
        items.add(Memory(UUID.randomUUID().toString(), t, System.currentTimeMillis()))
        save()
        return true
    }

    fun update(id: String, text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || isSensitive(t)) return false
        items.find { it.id == id }?.text = t
        save()
        return true
    }

    fun delete(id: String) {
        items.removeAll { it.id == id }
        save()
    }

    fun forget(keyword: String): Int {
        val k = keyword.trim().lowercase()
        if (k.isEmpty()) return 0
        val n = items.size
        items.removeAll { it.text.lowercase().contains(k) }
        save()
        return n - items.size
    }

    fun clear() {
        items.clear()
        save()
    }

    fun exportJson(): String {
        val arr = JSONArray()
        for (m in items) arr.put(JSONObject().put("text", m.text).put("ts", m.ts))
        return arr.toString(2)
    }

    fun importJson(s: String): Int {
        var n = 0
        try {
            val arr = JSONArray(s)
            for (i in 0 until arr.length()) {
                if (add(arr.getJSONObject(i).optString("text"))) n++
            }
        } catch (e: Exception) {
            // bad file
        }
        return n
    }

    companion object {
        private val SENSITIVE = Regex(
            "(password|passcode|passwd|\\botp\\b|\\bpin\\b|cvv|cvc|card number|credit card|debit card|" +
                "bank account|account number|iban|secret key|api key|token|পাসওয়ার্ড|पासवर्ड)|\\d{6,}",
            RegexOption.IGNORE_CASE
        )

        fun isSensitive(t: String) = SENSITIVE.containsMatchIn(t)
    }
}
