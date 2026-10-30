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

# ---------- 3) muat simpanan akhir permainan dari PROSES DINGIN lewat aplikasi (debug, bisa run-as) ----------
# Mengukur apa yang dialami pemain: berapa lama aplikasi yang baru dibuka memutar ulang kampanye ±380 bulan di latar belakang.
# Dua keadaan kompilasi: bawaan pemasangan (hanya diverifikasi; jalan lewat interpreter + JIT, seperti APK yang baru dipasang
# manual) dan AOT penuh (batas atas kecepatan, seperti aplikasi yang sudah dioptimalkan sistem). Hasil informatif, tidak menggagalkan job.
adb uninstall "$PKG" > /dev/null 2>&1 || true
python3 tools/make_late_save.py app/src/androidTest/assets/replay_fixture.txt 380 "$OUT/late_save.txt" > "$OUT/late_save-info.txt" 2>&1
adb install -r -t "$DEBUG_APK" > "$OUT/install-debug2.txt" 2>&1
adb push "$OUT/late_save.txt" /data/local/tmp/kapital_late_save.txt > "$OUT/push-late.txt" 2>&1
LATE_SIZE=$(stat -c %s "$OUT/late_save.txt" 2>/dev/null || echo 0)
: > "$OUT/cold-load.txt"
cold_finished=1
cold_start() {  # $1 = label pengukuran
  adb shell am force-stop "$PKG" > /dev/null 2>&1
  adb shell run-as "$PKG" mkdir -p files > /dev/null 2>&1
  adb shell run-as "$PKG" cp /data/local/tmp/kapital_late_save.txt files/kapital_save.txt > /dev/null 2>&1
  local have
  have=$(adb shell run-as "$PKG" stat -c %s files/kapital_save.txt 2>/dev/null | tr -d '\r')
  if [ "$have" != "$LATE_SIZE" ]; then
    # cadangan: kirim lewat stdin
    adb shell "run-as $PKG sh -c 'cat > files/kapital_save.txt'" < "$OUT/late_save.txt" > /dev/null 2>&1
    have=$(adb shell run-as "$PKG" stat -c %s files/kapital_save.txt 2>/dev/null | tr -d '\r')
  fi
  adb logcat -c
  local t0 t1 line=""
  t0=$(date +%s)
  adb shell am start -W -n "$PKG/com.example.MainActivity" > "$OUT/cold-$1-start.txt" 2>&1
  for _ in $(seq 1 180); do
    line=$(adb logcat -d -s KAPITAL-LOAD:I 2>/dev/null | grep "restorePending selesai" | head -1)
    [ -n "$line" ] && break
    sleep 1
  done
  t1=$(date +%s)
  if [ -z "$line" ]; then cold_finished=0; line="TIDAK SELESAI dalam 180 s"; fi
  echo "$1 (berkas $have dari $LATE_SIZE byte): ${line} | diamati skrip $((t1 - t0)) s | $(grep -E 'TotalTime|WaitTime' "$OUT/cold-$1-start.txt" | tr '\r\n' '  ')" >> "$OUT/cold-load.txt"
}
cold_start jit1
cold_start jit2
adb shell cmd package compile -m speed -f "$PKG" > "$OUT/aot-compile.txt" 2>&1
cold_start aot1
cold_start aot2
adb shell am force-stop "$PKG" > /dev/null 2>&1 || true
adb uninstall "$PKG" > /dev/null 2>&1 || true
status "cold_load_finished=$cold_finished"
exit 0
