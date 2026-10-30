#!/usr/bin/env bash
# Dijalankan DI DALAM reactivecircus/android-emulator-runner (emulator sudah hidup; APK sudah dibangun oleh langkah sebelumnya).
# Mengumpulkan semua hasil ke emulator-out/ dan menulis emulator-out/status.txt; skrip ini sendiri selalu keluar 0 supaya langkah
# berikutnya bisa mengunggah hasil dan baru kemudian menggagalkan job.
set -u
PKG=com.aistudio.kapital.xkrpzv
OUT=emulator-out
mkdir -p "$OUT"
: > "$OUT/status.txt"
status() { echo "$1" >> "$OUT/status.txt"; }

{
  echo "android: $(adb shell getprop ro.build.version.release | tr -d '\r') (sdk $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"
  echo "abi: $(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "model: $(adb shell getprop ro.product.model | tr -d '\r')"
  echo "runtime: $(adb shell getprop persist.sys.dalvik.vm.lib.2 | tr -d '\r')"
} > "$OUT/device.txt" 2>&1

# layar menyala dan tidak terkunci
adb shell input keyevent 224 >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb shell svc power stayon true >/dev/null 2>&1 || true
adb logcat -c || true

DEBUG_APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
TEST_APK=$(ls app/build/outputs/apk/androidTest/debug/*.apk | head -1)
RELEASE_APK=$(ls app/build/outputs/apk/release/*.apk | head -1)

# ---------- 1) uji instrumented pada build debug ----------
adb install -r -t "$DEBUG_APK" > "$OUT/install-debug.txt" 2>&1
adb install -r -t "$TEST_APK" > "$OUT/install-test.txt" 2>&1
adb shell am instrument -w -r "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/instrument.txt" 2>&1
python3 tools/parse_instrument.py "$OUT/instrument.txt" "$OUT/instrument-ringkasan.md" > /dev/null 2>&1
status "instrumented=$?"
mkdir -p "$OUT/appfiles"
adb pull "/sdcard/Android/data/$PKG/files/." "$OUT/appfiles/" > "$OUT/pull.txt" 2>&1 || true
adb logcat -d -s KAPITAL-ART:I > "$OUT/art-logcat.txt" 2>&1 || true
adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
adb uninstall "$PKG.test" > /dev/null 2>&1 || true
adb uninstall "$PKG" > /dev/null 2>&1 || true

# ---------- 2) build release (R8, kunci sementara): pasang, jalankan, monkey ----------
adb install -r "$RELEASE_APK" > "$OUT/install-release.txt" 2>&1
grep -q "Success" "$OUT/install-release.txt"; status "release_install=$?"
ls -l "$RELEASE_APK" "$DEBUG_APK" | awk '{print $5, $9}' > "$OUT/apk-sizes.txt"
adb logcat -c || true
adb shell am start -W -n "$PKG/com.example.MainActivity" > "$OUT/release-start.txt" 2>&1
sleep 8
adb exec-out screencap -p > "$OUT/release_01_menu.png" 2>/dev/null || true
adb shell pidof "$PKG" > /dev/null 2>&1; status "release_alive_after_start=$?"
adb shell monkey -p "$PKG" -s 20261030 --throttle 70 --pct-syskeys 0 --pct-appswitch 0 --pct-anyevent 0 -v 2500 > "$OUT/monkey.txt" 2>&1
adb exec-out screencap -p > "$OUT/release_02_setelah_monkey.png" 2>/dev/null || true
adb shell pidof "$PKG" > /dev/null 2>&1; status "release_alive_after_monkey=$?"
adb logcat -d > "$OUT/release-logcat.txt" 2>&1 || true
if grep -Eq "^// CRASH|Monkey aborted|// NOT RESPONDING" "$OUT/monkey.txt"; then status "monkey_crash=1"; else status "monkey_crash=0"; fi
if grep -E "FATAL EXCEPTION" "$OUT/release-logcat.txt" | head -1 | grep -q .; then status "release_fatal=1"; else status "release_fatal=0"; fi
grep -E "Events injected|Network stats|CRASH|aborted" "$OUT/monkey.txt" | head -5 > "$OUT/monkey-ringkasan.txt" 2>&1 || true
exit 0
