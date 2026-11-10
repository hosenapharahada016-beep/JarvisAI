package com.jarvis.ai

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Text-to-Speech: ElevenLabs key ho to wahi (natural voice), warna Android ka built-in TTS.
 * ElevenLabs fail ho to automatically system TTS pe fallback.
 */
class Speaker(private val ctx: Context, private val prefs: Prefs) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var mp: MediaPlayer? = null
    private var gen = 0

    var speaking = false
        private set

    init {
        tts = TextToSpeech(ctx.applicationContext) { st -> ttsReady = (st == TextToSpeech.SUCCESS) }
    }

    fun speak(raw: String, done: () -> Unit) {
        stop()
        val text = Util.speechText(raw).take(3800)
        if (text.isEmpty()) {
            done()
            return
        }
        val my = ++gen
        speaking = true
        val finish: () -> Unit = {
            if (my == gen) {
                speaking = false
                done()
            }
        }
        if (prefs.elevenKey.isNotBlank()) {
            Thread {
                try {
                    val f = eleven(text)
                    main.post {
                        if (my == gen) {
                            playFile(f, finish) { systemSpeak(text, my, finish) }
                        }
                    }
                } catch (e: Exception) {
                    main.post { if (my == gen) systemSpeak(text, my, finish) }
                }
            }.start()
        } else {
            systemSpeak(text, my, finish)
        }
    }

    fun stop() {
        gen++
        speaking = false
        try { tts?.stop() } catch (e: Exception) { /* ignore */ }
        try { mp?.release() } catch (e: Exception) { /* ignore */ }
        mp = null
    }

    fun shutdown() {
        stop()
        try { tts?.shutdown() } catch (e: Exception) { /* ignore */ }
        tts = null
    }

    // ------------------------------------------------------------ ElevenLabs
    private fun eleven(text: String): File {
        val body = JSONObject().put("text", text).put("model_id", "eleven_multilingual_v2")
        val r = Util.http(
            "https://api.elevenlabs.io/v1/text-to-speech/${prefs.voiceId}",
            mapOf(
                "xi-api-key" to prefs.elevenKey,
                "Content-Type" to "application/json",
                "Accept" to "audio/mpeg"
            ),
            body.toString().toByteArray(Charsets.UTF_8)
        )
        if (r.first != 200) throw RuntimeException("ElevenLabs HTTP ${r.first}")
        val f = File(ctx.cacheDir, "tts_${System.nanoTime()}.mp3")
        f.writeBytes(r.second)
        return f
    }

    private fun playFile(f: File, onDone: () -> Unit, onFail: () -> Unit) {
        try {
            val v = prefs.voiceVolume / 100f
            val player = MediaPlayer()
            mp = player
            player.setDataSource(f.path)
            player.setVolume(v, v)
            player.setOnCompletionListener {
                f.delete()
                onDone()
            }
            player.setOnErrorListener { _, _, _ ->
                f.delete()
                onFail()
                true
            }
            player.setOnPreparedListener { m ->
                try {
                    m.playbackParams = PlaybackParams()
                        .setSpeed(prefs.voiceSpeed.coerceIn(0.5f, 2.0f))
                        .setPitch(prefs.voicePitch.coerceIn(0.5f, 2.0f))
                } catch (e: Exception) { /* ignore */ }
                m.start()
            }
            player.prepareAsync()
        } catch (e: Exception) {
            onFail()
        }
    }

    // ------------------------------------------------------------ System TTS
    private fun systemSpeak(text: String, my: Int, finish: () -> Unit) {
        val t = tts
        if (t == null || !ttsReady) {
            main.postDelayed({ if (my == gen) finish() }, 400)
            return
        }
        var lang = Util.langOf(text) ?: (if (!prefs.autoLang) prefs.prefLang else null)
        if (lang == "ar" && prefs.prefLang.startsWith("ur")) lang = "ur"
        val loc = if (lang != null) Locale.forLanguageTag(lang) else Locale.US
        val r = t.setLanguage(loc)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            t.setLanguage(Locale.US)
        }
        t.setSpeechRate(prefs.voiceSpeed)
        t.setPitch(prefs.voicePitch)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { main.post { if (my == gen) finish() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { if (my == gen) finish() } }
        })
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, prefs.voiceVolume / 100f)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, params, "u$my")
    }
}
