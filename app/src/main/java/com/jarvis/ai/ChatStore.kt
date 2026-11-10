package com.jarvis.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** role: "user", "ai" ya "sys" (sirf UI ke liye, AI ko nahi bheja jata). */
data class Message(val role: String, val text: String, val ts: Long = System.currentTimeMillis())

class ChatStore(ctx: Context) {
    private val file = File(ctx.filesDir, "chat.json")
    val list = mutableListOf<Message>()

    init {
        try {
            if (file.exists()) {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(Message(o.getString("role"), o.getString("text"), o.optLong("ts", 0L)))
                }
            }
        } catch (e: Exception) {
            list.clear()
        }
    }

    fun add(m: Message) {
        list.add(m)
        while (list.size > 300) list.removeAt(0)
        save()
    }

    fun clear() {
        list.clear()
        save()
    }

    /** AI ko bhejne ke liye last N turns; user se shuru, sys hata ke. */
    fun recent(n: Int = 24): List<Message> {
        val r = list.filter { it.role == "user" || it.role == "ai" }.takeLast(n).toMutableList()
        while (r.isNotEmpty() && r.first().role != "user") r.removeAt(0)
        return r
    }

    private fun save() {
        try {
            val arr = JSONArray()
            for (m in list) arr.put(JSONObject().put("role", m.role).put("text", m.text).put("ts", m.ts))
            file.writeText(arr.toString())
        } catch (e: Exception) {
            // ignore
        }
    }
}
