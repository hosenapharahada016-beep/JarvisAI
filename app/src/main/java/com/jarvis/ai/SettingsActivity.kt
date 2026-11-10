package com.jarvis.ai

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlin.math.roundToInt

/** Poori Settings screen code se banayi gayi hai (koi extra XML nahi). */
class SettingsActivity : AppCompatActivity() {

    private lateinit var p: Prefs
    private lateinit var mem: MemoryStore
    private lateinit var chat: ChatStore
    private lateinit var box: LinearLayout

    private val exportL = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { it.write(mem.exportJson().toByteArray()) }
            toast("Memory exported")
        }
    }
    private val importL = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val s = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            toast("Imported ${mem.importJson(s)} memories")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        p = Prefs(this)
        Util.applyTheme(p)
        super.onCreate(savedInstanceState)
        title = "Settings"
        mem = MemoryStore(this)
        chat = ChatStore(this)

        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(40))
        }
        val sv = ScrollView(this).apply {
            fitsSystemWindows = true
            addView(box)
        }
        setContentView(sv)
        build()
    }

    // ---------------------------------------------------------------- sections
    private fun build() {
        // ---- AI
        header("AI")
        choice("Provider", listOf("Gemini", "OpenAI"), if (p.provider == "openai") 1 else 0) {
            p.provider = if (it == 1) "openai" else "gemini"
        }
        field("Gemini API Key", p.geminiKey, password = true) { p.geminiKey = it }
        field("Gemini model (e.g. gemini-2.0-flash)", p.geminiModel) { p.geminiModel = it }
        field("OpenAI API Key", p.openaiKey, password = true) { p.openaiKey = it }
        field("OpenAI model (e.g. gpt-4o-mini)", p.openaiModel) { p.openaiModel = it }

        // ---- Voice
        header("Voice")
        field("ElevenLabs API Key", p.elevenKey, password = true) { p.elevenKey = it }
        val voices = listOf("Rachel" to "21m00Tcm4TlvDq8ikWAM", "Adam" to "pNInz6obpgDQGcFmaJgB", "Bella" to "EXAVITQu4vr4xnSDxMgb")
        lateinit var voiceField: TextInputEditText
        voiceField = field("Voice ID (ElevenLabs)", p.voiceId) { p.voiceId = it }
        choice("Voice presets", voices.map { it.first }, -1) {
            p.voiceId = voices[it].second
            voiceField.setText(voices[it].second)
        }
        slider("Voice speed", 50f, 200f, 5f, (p.voiceSpeed * 100).roundToInt().toFloat(), { "${it.toInt()}%" }) {
            p.voiceSpeed = it / 100f
        }
        slider("Voice pitch", 50f, 200f, 5f, (p.voicePitch * 100).roundToInt().toFloat(), { "${it.toInt()}%" }) {
            p.voicePitch = it / 100f
        }
        slider("Voice volume", 0f, 100f, 1f, p.voiceVolume.toFloat(), { "${it.toInt()}%" }) { p.voiceVolume = it.toInt() }
        slider("Microphone sensitivity", 0f, 100f, 1f, p.micSensitivity.toFloat(), { "${it.toInt()}" }) {
            p.micSensitivity = it.toInt()
        }
        toggle("Wake word (\"Hey Jarvis\")", p.wakeWord) { p.wakeWord = it }

        // ---- Memory
        header("Memory")
        toggle("Enable long-term memory", p.memoryEnabled) { p.memoryEnabled = it }
        button("Saved memories") { showMemories() }
        button("Export memory") { exportL.launch("jarvis_memory.json") }
        button("Import memory") { importL.launch(arrayOf("application/json", "text/plain", "*/*")) }
        button("Clear memory") {
            confirm("Saari memories delete karein?") { mem.clear(); toast("Memory cleared") }
        }

        // ---- Device
        header("Device")
        button("App permissions") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        button("Accessibility service") { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        button("Notification access") { open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
        button("Default assistant") { open(Settings.ACTION_VOICE_INPUT_SETTINGS) }
        button("Battery optimization") { open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }

        // ---- Audio controls
        header("Audio controls")
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        slider("Media volume", 0f, 100f, 1f, (cur * 100f / max).roundToInt().toFloat(), { "${it.toInt()}%" }) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (it / 100f * max).roundToInt(), 0)
        }
        button("Increase volume") {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        }
        button("Decrease volume") {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        }
        button("Mute") {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
        }
        button("Unmute") {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
        }

        // ---- Appearance
        header("Appearance")
        val themes = listOf("system" to "System theme", "light" to "Light mode", "dark" to "Dark mode")
        choice("Theme", themes.map { it.second }, themes.indexOfFirst { it.first == p.theme }.coerceAtLeast(0)) {
            p.theme = themes[it].first
            Util.applyTheme(p)
        }
        choice("Accent color", Prefs.ACCENT_NAMES, p.accentIdx) { p.accentIdx = it }
        slider("Font size", 12f, 24f, 1f, p.fontSize.toFloat(), { "${it.toInt()} sp" }) { p.fontSize = it.toInt() }

        // ---- Language
        header("Language")
        toggle("Auto detect language", p.autoLang) { p.autoLang = it }
        choice("Preferred language", Prefs.LANG_NAMES, Prefs.LANG_TAGS.indexOf(p.prefLang).coerceAtLeast(0)) {
            p.prefLang = Prefs.LANG_TAGS[it]
        }
        field("Translation target (e.g. Bengali) — khali = off", p.translateTo) { p.translateTo = it }

        // ---- Privacy
        header("Privacy")
        button("Manage permissions") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        button("Data storage") {
            val bytes = filesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            AlertDialog.Builder(this).setTitle("Data storage")
                .setMessage("Sab data sirf is phone me hai (${bytes / 1024} KB).\nChat + memory app ke private folder me; API keys phone ki app preferences me. Koi server nahi, sirf AI/TTS provider ko request jati hai.")
                .setPositiveButton("OK", null).show()
        }
        button("Clear chat history") {
            confirm("Chat history delete karein?") { chat.clear(); toast("Chat cleared") }
        }
        button("Delete all data") {
            confirm("Chat, memory, API keys aur settings — sab delete ho jayega. Pakka?") {
                p.sp.edit().clear().apply()
                mem.clear()
                chat.clear()
                cacheDir.listFiles()?.forEach { it.delete() }
                toast("All data deleted")
                finish()
            }
        }

        // ---- About
        header("About")
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }
        note("JARVIS AI — version $ver")
        button("Check for updates") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Prefs.UPDATE_URL)))
        }
        button("Feedback") {
            startActivity(
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Prefs.FEEDBACK_EMAIL}"))
                    .putExtra(Intent.EXTRA_SUBJECT, "JARVIS feedback")
            )
        }
        button("Privacy policy") {
            info(
                "Privacy policy",
                "JARVIS aapka data sirf aapke phone par rakhta hai. Aapke messages aur attachments aapke chune hue AI provider (Gemini/OpenAI) aur, voice ke liye, ElevenLabs ko bheje jate hain. Passwords, OTP aur banking details memory me save nahi hote. Aap kabhi bhi memory ya poora data delete kar sakte hain."
            )
        }
        button("Terms of service") {
            info(
                "Terms of service",
                "Ye app \"jaisa hai waisa\" diya gaya hai. AI ke jawab galat ho sakte hain; zaroori faisle khud verify karein. API keys aur unka kharcha aapki zimmedari hai. Call/SMS jaise actions aapki confirmation ke baad hi chalte hain."
            )
        }
    }

    // ---------------------------------------------------------------- dialogs
    private fun showMemories() {
        val list = mem.all()
        if (list.isEmpty()) {
            toast("Abhi koi memory saved nahi hai")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Saved memories (tap = edit/delete)")
            .setItems(list.map { it.text }.toTypedArray()) { _, i -> editMemory(list[i]) }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun editMemory(m: Memory) {
        val et = TextInputEditText(this).apply { setText(m.text) }
        AlertDialog.Builder(this)
            .setTitle("Edit memory")
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                if (!mem.update(m.id, et.text.toString())) toast("Save nahi hui (khali ya sensitive)")
                showMemories()
            }
            .setNegativeButton("Delete") { _, _ ->
                mem.delete(m.id)
                showMemories()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun confirm(msg: String, yes: () -> Unit) {
        AlertDialog.Builder(this).setMessage(msg)
            .setPositiveButton("Yes") { _, _ -> yes() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun info(t: String, m: String) {
        AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("OK", null).show()
    }

    private fun open(action: String) {
        try { startActivity(Intent(action)) } catch (e: Exception) { toast("Is phone par ye screen nahi mili") }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------------------------------------------------------------- tiny UI toolkit
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(top: Int = 8) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun header(t: String) {
        box.addView(TextView(this).apply {
            text = t
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(p.accent)
            setPadding(0, dp(24), 0, dp(4))
        })
    }

    private fun note(t: String) {
        box.addView(TextView(this).apply { text = t; textSize = 14f }, lp())
    }

    private fun field(hint: String, value: String, password: Boolean = false, onChange: (String) -> Unit): TextInputEditText {
        val til = TextInputLayout(this).apply { this.hint = hint }
        val et = TextInputEditText(til.context).apply {
            setText(value)
            setSingleLine(true)
            if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            doAfterTextChanged { onChange(it?.toString() ?: "") }
        }
        til.addView(et)
        box.addView(til, lp())
        return et
    }

    private fun toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
        box.addView(MaterialSwitch(this).apply {
            text = label
            isChecked = value
            setOnCheckedChangeListener { _, c -> onChange(c) }
        }, lp())
    }

    private fun slider(
        label: String, from: Float, to: Float, step: Float, value: Float,
        fmt: (Float) -> String, onChange: (Float) -> Unit
    ) {
        val v = value.coerceIn(from, to)
        val tv = TextView(this).apply { text = "$label: ${fmt(v)}" }
        val s = Slider(this).apply {
            valueFrom = from
            valueTo = to
            stepSize = step
            this.value = v
            addOnChangeListener { _, nv, fromUser ->
                tv.text = "$label: ${fmt(nv)}"
                if (fromUser) onChange(nv)
            }
        }
        box.addView(tv, lp(12))
        box.addView(s, lp(0))
    }

    private fun choice(label: String, names: List<String>, current: Int, onPick: (Int) -> Unit) {
        val b = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        fun refresh(i: Int) { b.text = if (i >= 0) "$label: ${names[i]}" else label }
        refresh(current)
        b.setOnClickListener {
            AlertDialog.Builder(this).setTitle(label)
                .setItems(names.toTypedArray()) { _, i -> refresh(i); onPick(i) }
                .show()
        }
        box.addView(b, lp())
    }

    private fun button(label: String, onClick: () -> Unit) {
        val b = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        b.text = label
        b.setOnClickListener { onClick() }
        box.addView(b, lp())
    }
}
