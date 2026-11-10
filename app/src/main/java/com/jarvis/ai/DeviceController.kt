package com.jarvis.ai

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * AI se aaye [[ACTION {...}]] tags ko asli Android actions me badalta hai.
 * Call / SMS / WhatsApp jaise sensitive actions ke liye pehle user se confirmation li jati hai (MainActivity me).
 */
class DeviceController(private val act: Activity) {

    private val sensitive = setOf("call", "sms", "whatsapp", "delete", "purchase")

    fun isSensitive(a: JSONObject) = a.optString("type") in sensitive

    fun describe(a: JSONObject): String {
        val who = a.optString("contact", a.optString("number", a.optString("name", "")))
        return when (a.optString("type")) {
            "call" -> "Call $who?"
            "sms" -> "Send SMS to $who:\n\"${a.optString("message")}\""
            "whatsapp" -> "Open WhatsApp chat with $who:\n\"${a.optString("message")}\""
            else -> "Allow action: ${a.optString("type")}?"
        }
    }

    fun execute(a: JSONObject): String = try {
        when (val type = a.optString("type")) {
            "open_app" -> openApp(a.optString("name"))
            "call" -> call(a)
            "sms" -> sms(a)
            "whatsapp" -> whatsapp(a)
            "contacts" -> go(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI), "Contacts opened")
            "camera" -> go(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "Camera opened")
            "gallery" -> go(
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY),
                "Gallery opened"
            )
            "open_file" -> go(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
                "File picker opened"
            )
            "maps" -> go(
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + enc(a.optString("query")))),
                "Maps opened"
            )
            "navigate" -> go(
                Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + enc(a.optString("query")))),
                "Navigation started"
            )
            "wifi" -> go(
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY),
                "Wi-Fi panel opened (Android apps ko Wi-Fi seedha on/off karne nahi deta)"
            )
            "bluetooth" -> go(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
                "Bluetooth settings opened (Android seedha toggle allow nahi karta)"
            )
            "flashlight" -> flashlight(a.optBoolean("on", true))
            "volume" -> volume(a)
            "mute" -> mute(true)
            "unmute" -> mute(false)
            "brightness" -> brightness(a.optInt("level", 50))
            "alarm" -> alarm(a)
            "timer" -> timer(a)
            "calendar" -> calendar(a)
            "web" -> go(Intent(Intent.ACTION_VIEW, Uri.parse(a.optString("url"))), "Opened link")
            else -> "Unknown action: $type"
        }
    } catch (e: Exception) {
        "Action failed: ${e.message}"
    }

    // ------------------------------------------------------------- helpers
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun go(i: Intent, ok: String): String {
        act.startActivity(i)
        return ok
    }

    private fun has(p: String) = ContextCompat.checkSelfPermission(act, p) == PackageManager.PERMISSION_GRANTED

    private fun ask(p: String) = ActivityCompat.requestPermissions(act, arrayOf(p), 77)

    private fun resolveNumber(a: JSONObject): String? {
        val n = a.optString("number")
        if (n.isNotBlank() && n.any { it.isDigit() }) return n
        val name = a.optString("contact", a.optString("name"))
        if (name.isBlank()) return null
        if (!has(Manifest.permission.READ_CONTACTS)) {
            ask(Manifest.permission.READ_CONTACTS)
            return null
        }
        act.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        return null
    }

    // ------------------------------------------------------------- actions
    private fun openApp(name: String): String {
        val pm = act.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(q, 0)
        val n = name.trim().lowercase()
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == n }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(n) }
            ?: return "App not found: $name"
        val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return "Cannot launch $name"
        act.startActivity(i)
        return "Opened ${hit.loadLabel(pm)}"
    }

    private fun call(a: JSONObject): String {
        val num = resolveNumber(a) ?: return "Number/contact nahi mila (contacts permission ya naam check karo)"
        val uri = Uri.parse("tel:" + Uri.encode(num))
        return if (has(Manifest.permission.CALL_PHONE)) {
            act.startActivity(Intent(Intent.ACTION_CALL, uri))
            "Calling $num"
        } else {
            ask(Manifest.permission.CALL_PHONE)
            act.startActivity(Intent(Intent.ACTION_DIAL, uri))
            "Dialer opened for $num (direct call permission maangi gayi hai)"
        }
    }

    private fun sms(a: JSONObject): String {
        val num = resolveNumber(a) ?: return "Number/contact nahi mila"
        val msg = a.optString("message")
        if (has(Manifest.permission.SEND_SMS)) {
            val sm = act.getSystemService(SmsManager::class.java)
            sm.sendMultipartTextMessage(num, null, sm.divideMessage(msg), null, null)
            return "SMS sent to $num"
        }
        ask(Manifest.permission.SEND_SMS)
        act.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num")).putExtra("sms_body", msg))
        return "SMS app opened (send permission maangi gayi hai)"
    }

    private fun whatsapp(a: JSONObject): String {
        val num = resolveNumber(a)?.filter { it.isDigit() }
        val msg = a.optString("message")
        val url = if (!num.isNullOrEmpty()) "https://wa.me/$num?text=${enc(msg)}"
        else "https://wa.me/?text=${enc(msg)}"
        act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        return "WhatsApp opened (message bhejne ke liye WhatsApp me Send dabao)"
    }

    private fun flashlight(on: Boolean): String {
        val cm = act.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "Flashlight available nahi hai"
        cm.setTorchMode(id, on)
        return if (on) "Flashlight on" else "Flashlight off"
    }

    private fun volume(a: JSONObject): String {
        val am = act.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (a.has("level")) {
            val lv = a.optInt("level").coerceIn(0, 100)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(lv / 100f * max), AudioManager.FLAG_SHOW_UI)
            return "Volume $lv%"
        }
        val up = a.optString("dir", "up") == "up"
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
        return if (up) "Volume increased" else "Volume decreased"
    }

    private fun mute(m: Boolean): String {
        val am = act.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (m) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
            AudioManager.FLAG_SHOW_UI
        )
        return if (m) "Muted" else "Unmuted"
    }

    private fun brightness(level: Int): String {
        val lv = level.coerceIn(1, 100)
        val lp = act.window.attributes
        lp.screenBrightness = lv / 100f // sirf is app ki screen; system brightness ke liye WRITE_SETTINGS chahiye
        act.window.attributes = lp
        return "Brightness $lv% (is app ki screen ke liye)"
    }

    private fun alarm(a: JSONObject): String {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, a.optInt("hour"))
            .putExtra(AlarmClock.EXTRA_MINUTES, a.optInt("minute"))
            .putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "JARVIS"))
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        act.startActivity(i)
        return "Alarm set for %02d:%02d".format(a.optInt("hour"), a.optInt("minute"))
    }

    private fun timer(a: JSONObject): String {
        val s = a.optInt("seconds", 60)
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, s)
            .putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "JARVIS"))
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        act.startActivity(i)
        return "Timer set for $s seconds"
    }

    private fun calendar(a: JSONObject): String {
        val i = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, a.optString("title", "Reminder"))
        val start = a.optString("start")
        if (start.isNotBlank()) {
            try {
                val d = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(start)
                if (d != null) {
                    i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, d.time)
                    i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, d.time + 3600_000L)
                }
            } catch (e: Exception) { /* time parse fail: user manually set karega */ }
        }
        act.startActivity(i)
        return "Calendar event screen opened"
    }
}
