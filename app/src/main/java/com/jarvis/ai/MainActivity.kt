package com.jarvis.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var memory: MemoryStore
    private lateinit var chat: ChatStore
    private lateinit var speaker: Speaker
    private lateinit var device: DeviceController
    private lateinit var adapter: MessageAdapter

    private lateinit var rv: RecyclerView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var attachInfo: TextView
    private lateinit var voiceSwitch: MaterialSwitch
    private lateinit var btnSend: ImageButton
    private lateinit var btnMic: ImageButton

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var voiceMode = false
    private var oneShot = false
    private var listening = false
    private var busy = false
    private var awakeUntil = 0L
    private var attach: AiClient.Attachment? = null

    private val actionRe = Regex("\\[\\[ACTION\\s*(\\{.*?\\})\\s*\\]\\]", RegexOption.DOT_MATCHES_ALL)
    private val memRe = Regex("\\[\\[MEMORY\\s*(\\{.*?\\})\\s*\\]\\]", RegexOption.DOT_MATCHES_ALL)
    private val wakeRe = Regex("(hey|hay|hi|ok|okay)?\\s*(jarvis|জার্ভিস|जार्विस)[,.!]?", RegexOption.IGNORE_CASE)

    // ---------------------------------------------------------------- launchers
    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadAttachment(uri)
    }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            val b = bitmapToB64(bmp)
            setAttachment(AiClient.Attachment("camera.jpg", "image/jpeg", b, null))
        }
    }
    private val micPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startListening() else {
            toast("Microphone permission chahiye")
            setVoiceMode(false)
        }
    }

    // ---------------------------------------------------------------- lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        Util.applyTheme(prefs)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        memory = MemoryStore(this)
        chat = ChatStore(this)
        speaker = Speaker(this, prefs)
        device = DeviceController(this)

        rv = findViewById(R.id.rv)
        input = findViewById(R.id.input)
        status = findViewById(R.id.status)
        attachInfo = findViewById(R.id.attachInfo)
        voiceSwitch = findViewById(R.id.voiceSwitch)
        btnSend = findViewById(R.id.btnSend)
        btnMic = findViewById(R.id.btnMic)

        adapter = MessageAdapter(chat.list, prefs)
        rv.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rv.adapter = adapter
        scrollDown()

        btnSend.setOnClickListener { sendFromInput() }
        btnMic.setOnClickListener {
            // mic dabao = bolna band + ek baar suno (interrupt)
            speaker.stop()
            oneShot = true
            startListening()
        }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnAttach).setOnClickListener { chooseAttachment() }
        attachInfo.setOnClickListener { attach = null; attachInfo.visibility = View.GONE }
        voiceSwitch.setOnCheckedChangeListener { _, checked -> setVoiceMode(checked, fromSwitch = true) }

        if (chat.list.isEmpty()) {
            val hint = if (prefs.geminiKey.isBlank() && prefs.openaiKey.isBlank())
                "Namaste! Settings (top-right) me jaake Gemini ya OpenAI API key daalo, phir baat shuru karte hain."
            else "Hello! Main JARVIS hu. Kaise madad karu?"
            addMsg(Message("ai", hint))
        }
    }

    override fun onResume() {
        super.onResume()
        // Settings se wapas aane par accent / font refresh
        btnSend.imageTintList = ColorStateList.valueOf(prefs.accent)
        btnMic.imageTintList = ColorStateList.valueOf(prefs.accent)
        adapter.notifyDataSetChanged()
        if (voiceMode) startListening()
    }

    override fun onPause() {
        super.onPause()
        stopListening()
        speaker.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer?.destroy()
        recognizer = null
        speaker.shutdown()
    }

    // ---------------------------------------------------------------- UI helpers
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun setStatus(s: String) { status.text = s }

    private fun scrollDown() {
        if (adapter.itemCount > 0) rv.scrollToPosition(adapter.itemCount - 1)
    }

    private fun addMsg(m: Message) {
        chat.add(m)
        adapter.notifyDataSetChanged()
        scrollDown()
    }

    private fun setVoiceMode(on: Boolean, fromSwitch: Boolean = false) {
        voiceMode = on
        if (!fromSwitch) {
            voiceSwitch.setOnCheckedChangeListener(null)
            voiceSwitch.isChecked = on
            voiceSwitch.setOnCheckedChangeListener { _, c -> setVoiceMode(c, fromSwitch = true) }
        }
        if (on) {
            awakeUntil = 0L
            if (prefs.wakeWord) setStatus("Say \"Hey Jarvis\"…")
            startListening()
        } else {
            stopListening()
            setStatus("Ready")
        }
    }

    // ---------------------------------------------------------------- sending
    private fun sendFromInput() {
        val t = input.text.toString().trim()
        if (t.isEmpty() && attach == null) return
        input.setText("")
        send(t, spoken = false)
    }

    private fun send(text: String, spoken: Boolean) {
        if (busy) return
        speaker.stop()
        val att = attach
        attach = null
        attachInfo.visibility = View.GONE
        val shown = if (att != null) "📎 ${att.name}\n$text".trim() else text
        addMsg(Message("user", shown))
        // AI ko attachment wala message text-only history me bhejna hai: last user msg = text
        val hist = chat.recent().toMutableList()
        if (hist.isNotEmpty() && hist.last().role == "user") {
            hist[hist.lastIndex] = Message("user", text)
        }
        busy = true
        setStatus("Thinking…")
        val system = buildSystem()
        Thread {
            try {
                val reply = AiClient.chat(prefs, system, hist, att)
                main.post { handleReply(reply, spoken) }
            } catch (e: Exception) {
                main.post {
                    busy = false
                    addMsg(Message("sys", "⚠ ${e.message ?: e.javaClass.simpleName}"))
                    setStatus("Ready")
                    if (voiceMode) startListening()
                }
            }
        }.start()
    }

    private fun buildSystem(): String {
        val sb = StringBuilder()
        sb.append(assets.open("system_prompt.txt").bufferedReader().use { it.readText() })
        sb.append("\n\n--- RUNTIME ---\n")
        sb.append(assets.open("runtime_protocol.txt").bufferedReader().use { it.readText() })
        sb.append("\nCurrent date/time: ")
            .append(SimpleDateFormat("EEEE, d MMMM yyyy HH:mm z", Locale.US).format(Date()))
            .append('\n')
        if (!prefs.autoLang) {
            val i = Prefs.LANG_TAGS.indexOf(prefs.prefLang).coerceAtLeast(0)
            sb.append("Always reply in ${Prefs.LANG_NAMES[i]}.\n")
        }
        if (prefs.translateTo.isNotBlank()) {
            sb.append("If the user pastes foreign text without instructions, also translate it to ${prefs.translateTo}.\n")
        }
        if (prefs.memoryEnabled) {
            val mems = memory.all()
            if (mems.isNotEmpty()) {
                sb.append("\nWhat you remember about the user:\n")
                mems.forEach { sb.append("- ").append(it.text).append('\n') }
            }
        } else {
            sb.append("\nLong-term memory is OFF: do not emit MEMORY tags.\n")
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------- reply handling
    private fun handleReply(raw: String, spoken: Boolean) {
        busy = false
        val actions = actionRe.findAll(raw).mapNotNull {
            try { JSONObject(it.groupValues[1]) } catch (e: Exception) { null }
        }.toList()
        val memOps = memRe.findAll(raw).mapNotNull {
            try { JSONObject(it.groupValues[1]) } catch (e: Exception) { null }
        }.toList()

        var display = raw.replace(actionRe, "").replace(memRe, "").trim()
        if (display.isEmpty() && (actions.isNotEmpty() || memOps.isNotEmpty())) display = "Okay."
        addMsg(Message("ai", display))

        if (prefs.memoryEnabled) {
            for (op in memOps) {
                val t = op.optString("text")
                if (op.optString("op") == "forget") {
                    val n = memory.forget(t)
                    if (n > 0) addMsg(Message("sys", "🧠 Forgot $n memory item(s)"))
                } else {
                    if (memory.add(t)) addMsg(Message("sys", "🧠 Remembered: $t"))
                    else addMsg(Message("sys", "🧠 Not saved (sensitive jaisa laga)"))
                }
            }
        }

        val afterSpeech = {
            setStatus("Ready")
            if (voiceMode) startListening()
        }
        if (spoken || voiceMode) {
            setStatus("Speaking…")
            speaker.speak(display, afterSpeech)
        } else {
            afterSpeech()
        }
        runActions(actions, 0)
    }

    private fun runActions(list: List<JSONObject>, i: Int) {
        if (i >= list.size) return
        val a = list[i]
        if (device.isSensitive(a)) {
            AlertDialog.Builder(this)
                .setTitle("Confirm")
                .setMessage(device.describe(a))
                .setPositiveButton("Yes") { _, _ ->
                    addMsg(Message("sys", device.execute(a)))
                    runActions(list, i + 1)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    addMsg(Message("sys", "Cancelled"))
                    runActions(list, i + 1)
                }
                .setCancelable(false)
                .show()
        } else {
            addMsg(Message("sys", device.execute(a)))
            runActions(list, i + 1)
        }
    }

    // ---------------------------------------------------------------- speech recognition
    private fun startListening() {
        if (busy || speaker.speaking || listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Is phone me speech recognition available nahi hai (Google app / Speech services install karo)")
            setVoiceMode(false)
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPerm.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(listener)
        }
        val tag = if (!prefs.autoLang) prefs.prefLang else Locale.getDefault().toLanguageTag()
        // mic sensitivity: zyada = jaldi "bol chuka" maan leta hai
        val silence = (3000 - prefs.micSensitivity * 20).coerceIn(800, 3000).toLong()
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            .putExtra(
                "android.speech.extra.ADDITIONAL_LANGUAGES",
                arrayOf("en-US", "bn-BD", "hi-IN", "ur-PK", "ar-SA")
            )
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silence)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silence)
        listening = true
        setStatus("Listening…")
        recognizer?.startListening(i)
    }

    private fun stopListening() {
        listening = false
        try { recognizer?.cancel() } catch (e: Exception) { /* ignore */ }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            listening = false
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                toast("Microphone permission chahiye")
                setVoiceMode(false)
                return
            }
            if (voiceMode) {
                main.postDelayed({ startListening() }, 700)
            } else {
                oneShot = false
                setStatus("Ready")
            }
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim() ?: ""
            onHeard(text)
        }
    }

    private fun onHeard(heard: String) {
        var t = heard
        if (t.isEmpty()) {
            if (voiceMode) main.postDelayed({ startListening() }, 400) else setStatus("Ready")
            oneShot = false
            return
        }
        val now = System.currentTimeMillis()
        if (voiceMode && prefs.wakeWord && !oneShot) {
            if (now > awakeUntil) {
                if (!wakeRe.containsMatchIn(t)) { // wake word nahi bola: ignore
                    main.postDelayed({ startListening() }, 300)
                    return
                }
                t = t.replace(wakeRe, "").trim()
                awakeUntil = now + 30_000
                if (t.isEmpty()) {
                    addMsg(Message("ai", "Yes?"))
                    setStatus("Speaking…")
                    speaker.speak("Yes?") { startListening() }
                    return
                }
            } else {
                awakeUntil = now + 30_000
            }
        }
        oneShot = false
        send(t, spoken = true)
    }

    // ---------------------------------------------------------------- attachments
    private fun chooseAttachment() {
        AlertDialog.Builder(this)
            .setTitle("Attach")
            .setItems(arrayOf("Camera (vision)", "Image / PDF / text file")) { _, which ->
                if (which == 0) takePhoto.launch(null) else pickFile.launch("*/*")
            }
            .show()
    }

    private fun setAttachment(a: AiClient.Attachment) {
        attach = a
        attachInfo.text = "📎 ${a.name}  (tap to remove)"
        attachInfo.visibility = View.VISIBLE
    }

    private fun bitmapToB64(bmp: Bitmap): String {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    private fun queryName(uri: Uri): String {
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) return c.getString(i)
            }
        } catch (e: Exception) { /* ignore */ }
        return uri.lastPathSegment ?: "file"
    }

    private fun loadAttachment(uri: Uri) {
        setStatus("Loading file…")
        Thread {
            try {
                val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                val name = queryName(uri)
                val att: AiClient.Attachment = when {
                    mime.startsWith("image/") -> {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
                        var s = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / s > 1600) s *= 2
                        val o = BitmapFactory.Options().apply { inSampleSize = s }
                        val bmp = contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, o) }
                            ?: throw RuntimeException("Image read nahi hui")
                        AiClient.Attachment(name, "image/jpeg", bitmapToB64(bmp), null)
                    }
                    mime == "application/pdf" -> {
                        val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                        if (bytes.size > 15_000_000) throw RuntimeException("PDF 15MB se badi hai")
                        AiClient.Attachment(name, mime, Base64.encodeToString(bytes, Base64.NO_WRAP), null)
                    }
                    mime.startsWith("text/") || mime == "application/json" || mime == "application/xml" -> {
                        val txt = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                        AiClient.Attachment(name, mime, null, txt.take(60_000))
                    }
                    else -> throw RuntimeException("Ye file type supported nahi: $mime")
                }
                main.post { setAttachment(att); setStatus("Ready") }
            } catch (e: Exception) {
                main.post { toast(e.message ?: "File load failed"); setStatus("Ready") }
            }
        }.start()
    }
}
