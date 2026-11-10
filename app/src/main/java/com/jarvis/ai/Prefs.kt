package com.jarvis.ai

import android.content.Context
import android.content.SharedPreferences

class Prefs(ctx: Context) {
    val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)

    private fun s(k: String, d: String) = sp.getString(k, d) ?: d
    private fun putS(k: String, v: String) = sp.edit().putString(k, v).apply()
    private fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun putB(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    private fun f(k: String, d: Float) = sp.getFloat(k, d)
    private fun putF(k: String, v: Float) = sp.edit().putFloat(k, v).apply()
    private fun i(k: String, d: Int) = sp.getInt(k, d)
    private fun putI(k: String, v: Int) = sp.edit().putInt(k, v).apply()

    // ---- AI ----
    var provider: String
        get() = s("provider", "gemini")
        set(v) = putS("provider", v)
    var geminiKey: String
        get() = s("gemini_key", "")
        set(v) = putS("gemini_key", v.trim())
    var openaiKey: String
        get() = s("openai_key", "")
        set(v) = putS("openai_key", v.trim())
    var geminiModel: String
        get() = s("gemini_model", "gemini-2.0-flash")
        set(v) = putS("gemini_model", v.trim())
    var openaiModel: String
        get() = s("openai_model", "gpt-4o-mini")
        set(v) = putS("openai_model", v.trim())

    // ---- Voice ----
    var elevenKey: String
        get() = s("eleven_key", "")
        set(v) = putS("eleven_key", v.trim())
    var voiceId: String
        get() = s("voice_id", "21m00Tcm4TlvDq8ikWAM")
        set(v) = putS("voice_id", v.trim())
    var voiceSpeed: Float
        get() = f("voice_speed", 1.0f)
        set(v) = putF("voice_speed", v)
    var voicePitch: Float
        get() = f("voice_pitch", 1.0f)
        set(v) = putF("voice_pitch", v)
    var voiceVolume: Int
        get() = i("voice_volume", 100)
        set(v) = putI("voice_volume", v)
    var micSensitivity: Int
        get() = i("mic_sens", 50)
        set(v) = putI("mic_sens", v)
    var wakeWord: Boolean
        get() = b("wake_word", false)
        set(v) = putB("wake_word", v)

    // ---- Memory ----
    var memoryEnabled: Boolean
        get() = b("memory_enabled", true)
        set(v) = putB("memory_enabled", v)

    // ---- Appearance ----
    var theme: String
        get() = s("theme", "system")
        set(v) = putS("theme", v)
    var accentIdx: Int
        get() = i("accent", 0)
        set(v) = putI("accent", v)
    var fontSize: Int
        get() = i("font_size", 16)
        set(v) = putI("font_size", v)
    val accent: Int
        get() = ACCENTS[accentIdx.coerceIn(0, ACCENTS.size - 1)]

    // ---- Language ----
    var autoLang: Boolean
        get() = b("auto_lang", true)
        set(v) = putB("auto_lang", v)
    var prefLang: String
        get() = s("pref_lang", "en-US")
        set(v) = putS("pref_lang", v)
    var translateTo: String
        get() = s("translate_to", "")
        set(v) = putS("translate_to", v)

    companion object {
        val ACCENTS = intArrayOf(
            0xFF6750A4.toInt(), // Purple
            0xFF0B57D0.toInt(), // Blue
            0xFF006D3B.toInt(), // Green
            0xFFB3261E.toInt(), // Red
            0xFFE8710A.toInt()  // Orange
        )
        val ACCENT_NAMES = listOf("Purple", "Blue", "Green", "Red", "Orange")

        val LANG_NAMES = listOf("English", "বাংলা (Bengali)", "हिन्दी (Hindi)", "اردو (Urdu)", "العربية (Arabic)")
        val LANG_TAGS = listOf("en-US", "bn-BD", "hi-IN", "ur-PK", "ar-SA")

        // Apna GitHub releases link yahan daal do
        const val UPDATE_URL = "https://github.com/YOUR_NAME/JarvisAI/releases"
        const val FEEDBACK_EMAIL = "you@example.com"
    }
}
