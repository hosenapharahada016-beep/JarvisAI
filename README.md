# JARVIS AI (Android app, Kotlin)

Aapke "JARVIS AI – Master System Prompt" ke hisaab se bana app. Master prompt `app/src/main/assets/system_prompt.txt` me hu-ba-hu hai aur har request ke saath AI ko jata hai.

> Dhyan: ye code maine likha hai lekin yahan Android SDK/internet nahi tha, isliye **abhi tak compile karke test nahi hua**. Pehli build me chhoti compile error aa sakti hai, error paste karoge to turant theek kar dunga.

## Features
- Chat (Gemini ya OpenAI), conversation history, markdown-free natural replies
- Voice mode: continuous listening, mic button se interrupt, optional wake word "Hey Jarvis"
- Voice output: ElevenLabs (natural), key na ho ya fail ho to Android TTS
- Language auto-detect (Bengali / English / Hindi / Urdu / Arabic), reply usi language me
- Device control (AI se): app kholna, call, SMS, WhatsApp, contacts, camera, gallery, files, maps/navigation, flashlight, volume/mute, brightness, alarm, timer, calendar. Call/SMS/WhatsApp se pehle confirmation dialog
- Long-term memory: user delete kare tab tak rehti hai; password/OTP/card/bank jaisi cheezein save nahi hoti; view / edit / delete / export / import / clear
- Smart features: web search (Gemini Google Search), camera vision, image analysis, OCR, PDF + text file reader (attach button), translation, weather, news, calculator, unit/currency conversion (AI + search se)
- Settings: AI, Voice, Memory, Device, Audio controls, Appearance, Language, Privacy, About

## Limits (Android ki wajah se, bug nahi)
- Wi-Fi / Bluetooth seedha on-off nahi hota (Android 10+); app unka settings panel kholta hai
- Brightness sirf app ki screen ke liye badalti hai
- "Default assistant", "Accessibility", "Notification access" buttons sirf system settings kholte hain; app khud assistant/notification-reader service nahi hai
- Bolte waqt apni awaaz sunne se bachne ke liye listening TTS ke baad shuru hoti hai; beech me rokne ke liye mic dabao
- Speech recognition phone ke Google speech service par chalti hai (internet chahiye)
- "Check for updates" `Prefs.kt` me `UPDATE_URL` kholta hai; apna link wahan daalo

## Build option A: GitHub Actions (sabse aasan, Termux ki zarurat nahi)
1. Is folder ko ek GitHub repo me push karo (Termux me `pkg install git`)
2. Repo ke Actions tab me "Build APK" chalao
3. Artifacts se `JARVIS-debug-apk` download karo aur install karo

## Build option B: Termux me
Termux **F-Droid** se lo. Phir:
```
pkg install openjdk-17 gradle aapt2 wget unzip
```
Android SDK (aarch64 build-tools + `platforms/android-34/android.jar`) `~/android-sdk` me chahiye. Termux ke liye ready SDK builds community projects (jaise Lzhiyong/termux-ndk, sdk-tools) me milti hain; platform zip Google ke repository se. Ye hissa device/version par depend karta hai, isliye pehle `pkg search aapt` / `pkg search android` se check karo.

SDK rakhne ke baad:
```
cd JarvisAI
bash termux_setup.sh
```
Ye `local.properties` banata hai, aapt2 override set karta hai aur `gradle assembleDebug` chalata hai. APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Pehli baar chalane par
1. App kholo, top-right Settings me Gemini ya OpenAI API key daalo
2. (Optional) ElevenLabs key daalo
3. Mic permission allow karo, "Voice" switch on karo

## Structure
```
app/src/main/java/com/jarvis/ai/
  MainActivity.kt      chat, voice loop, actions, attachments
  SettingsActivity.kt  poori settings screen
  AiClient.kt          Gemini + OpenAI HTTP
  Speaker.kt           ElevenLabs + Android TTS
  DeviceController.kt  phone control
  MemoryStore.kt       long-term memory
  ChatStore.kt, Prefs.kt, Util.kt, MessageAdapter.kt
app/src/main/assets/system_prompt.txt   aapka master prompt
app/src/main/assets/runtime_protocol.txt  action/memory tag rules
```
