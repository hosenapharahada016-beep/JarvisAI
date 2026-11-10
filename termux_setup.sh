#!/data/data/com.termux/files/usr/bin/bash
# JARVIS ko Termux me build karne ka helper.
# Pehle: Termux ko F-Droid se install karo (Play Store wala purana hai).
set -e

pkg update -y
pkg install -y openjdk-17 gradle wget unzip git
# aapt2 Termux ka (aarch64) chahiye, Google ka x86 binary yahan nahi chalta
pkg install -y aapt2 || echo "!! 'pkg install aapt2' fail hua: 'pkg search aapt' se naam dekho"

SDK="$HOME/android-sdk"
if [ ! -d "$SDK/platforms/android-34" ] || [ ! -d "$SDK/build-tools" ]; then
  cat <<EOF

==============================================================
Android SDK abhi $SDK me nahi mila.
Zaroorat: platforms/android-34 (android.jar) + build-tools.
README.md ka "Termux me Android SDK" section dekho, SDK rakh ke
ye script dobara chalao.
==============================================================
EOF
  exit 1
fi

# Project ke liye SDK path
echo "sdk.dir=$SDK" > local.properties

# Termux ka aapna aapt2 use karo (project gradle.properties ko chhue bina)
mkdir -p "$HOME/.gradle"
GP="$HOME/.gradle/gradle.properties"
touch "$GP"
grep -q "aapt2FromMavenOverride" "$GP" || \
  echo "android.aapt2FromMavenOverride=$PREFIX/bin/aapt2" >> "$GP"

gradle --no-daemon assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
  mkdir -p "$HOME/storage/downloads" 2>/dev/null || true
  cp "$APK" "$HOME/storage/downloads/JARVIS.apk" 2>/dev/null && \
    echo "APK copy hua: Downloads/JARVIS.apk" || \
    echo "APK yaha hai: $APK  (termux-setup-storage chala ke Downloads me copy kar sakte ho)"
fi
