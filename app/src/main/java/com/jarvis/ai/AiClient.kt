package com.jarvis.ai

import org.json.JSONArray
import org.json.JSONObject

/** Gemini aur OpenAI ke liye plain HTTP client (koi extra library nahi). Blocking hai: background thread se chalao. */
object AiClient {

    /** b64 = image/PDF ka base64; text = text file ka content. */
    class Attachment(val name: String, val mime: String, val b64: String?, val text: String?)

    fun chat(p: Prefs, system: String, hist: List<Message>, att: Attachment?): String =
        if (p.provider == "openai") openai(p, system, hist, att) else gemini(p, system, hist, att)

    private fun userText(m: Message, att: Attachment?, isLast: Boolean): String {
        var t = m.text
        if (isLast && att?.text != null) t = "[Attached file: ${att.name}]\n${att.text}\n\n$t"
        return if (t.isBlank()) "(attachment)" else t
    }

    // ---------------------------------------------------------------- Gemini
    private fun gemini(p: Prefs, system: String, hist: List<Message>, att: Attachment?): String {
        require(p.geminiKey.isNotBlank()) { "Gemini API key missing. Settings → AI me daalo." }

        fun body(tools: Boolean): ByteArray {
            val o = JSONObject()
            o.put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            val contents = JSONArray()
            hist.forEachIndexed { i, m ->
                val isLast = i == hist.lastIndex && m.role == "user"
                val parts = JSONArray()
                parts.put(JSONObject().put("text", userText(m, att, isLast)))
                if (isLast && att?.b64 != null) {
                    parts.put(
                        JSONObject().put(
                            "inline_data",
                            JSONObject().put("mime_type", att.mime).put("data", att.b64)
                        )
                    )
                }
                contents.put(
                    JSONObject().put("role", if (m.role == "user") "user" else "model").put("parts", parts)
                )
            }
            o.put("contents", contents)
            // Web search (weather, news, currency...) ke liye Google Search grounding
            if (tools) o.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            return o.toString().toByteArray(Charsets.UTF_8)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/${p.geminiModel}:generateContent"
        val headers = mapOf("Content-Type" to "application/json", "x-goog-api-key" to p.geminiKey)
        var r = Util.http(url, headers, body(true))
        if (r.first >= 400) r = Util.http(url, headers, body(false)) // model search tool support na kare to retry
        if (r.first >= 400) throw RuntimeException(Util.errorMessage(r.second, r.first))

        val j = JSONObject(String(r.second, Charsets.UTF_8))
        val cand = j.optJSONArray("candidates")?.optJSONObject(0)
        val parts = cand?.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        }
        if (sb.isBlank()) {
            val reason = j.optJSONObject("promptFeedback")?.optString("blockReason") ?: ""
            throw RuntimeException(if (reason.isNotBlank()) "Blocked: $reason" else "Empty reply from Gemini")
        }
        return sb.toString().trim()
    }

    // ---------------------------------------------------------------- OpenAI
    private fun openai(p: Prefs, system: String, hist: List<Message>, att: Attachment?): String {
        require(p.openaiKey.isNotBlank()) { "OpenAI API key missing. Settings → AI me daalo." }
        if (att?.mime == "application/pdf") throw RuntimeException("PDF ke liye Gemini provider chuno.")

        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", system))
        hist.forEachIndexed { i, m ->
            val isLast = i == hist.lastIndex && m.role == "user"
            val role = if (m.role == "user") "user" else "assistant"
            if (isLast && att?.b64 != null) {
                val content = JSONArray()
                content.put(JSONObject().put("type", "text").put("text", userText(m, att, true)))
                content.put(
                    JSONObject().put("type", "image_url").put(
                        "image_url",
                        JSONObject().put("url", "data:${att.mime};base64,${att.b64}")
                    )
                )
                msgs.put(JSONObject().put("role", role).put("content", content))
            } else {
                msgs.put(JSONObject().put("role", role).put("content", userText(m, att, isLast)))
            }
        }
        val body = JSONObject().put("model", p.openaiModel).put("messages", msgs)
        val r = Util.http(
            "https://api.openai.com/v1/chat/completions",
            mapOf("Content-Type" to "application/json", "Authorization" to "Bearer ${p.openaiKey}"),
            body.toString().toByteArray(Charsets.UTF_8)
        )
        if (r.first >= 400) throw RuntimeException(Util.errorMessage(r.second, r.first))
        val j = JSONObject(String(r.second, Charsets.UTF_8))
        val text = j.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""
        if (text.isBlank()) throw RuntimeException("Empty reply from OpenAI")
        return text.trim()
    }
}
