#!/usr/bin/env bash
# Dijalankan DI DALAM reactivecircus/android-emulator-runner (emulator sudah hidup; APK sudah dibangun oleh langkah sebelumnya).
# Mengumpulkan semua hasil ke emulator-out/ dan menulis emulator-out/status.txt; skrip ini sendiri selalu keluar 0 supaya langkah
# berikutnya bisa mengunggah hasil dan baru kemudian menggagalkan job.
#
# Berkas masukan (dibangun langkah workflow sebelumnya):
#   apk-dbg/app-debug.apk   build debug biasa (debuggable)
#   apk-nd/app-debug.apk    build debug yang TIDAK debuggable (-PciNoDebuggable): ART mengompilasinya seperti aplikasi rilis
#   app/build/outputs/apk/androidTest/debug/*.apk  APK uji instrumented
#   app/build/outputs/apk/release/*.apk            APK release (R8)
set -u
PKG=com.aistudio.kapital.xkrpzv
OUT=emulator-out
mkdir -p "$OUT/cold"
: > "$OUT/status.txt"
status() { echo "$1" >> "$OUT/status.txt"; }

{
  echo "android: $(adb shell getprop ro.build.version.release | tr -d '\r') (sdk $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"
  echo "abi: $(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "model: $(adb shell getprop ro.product.model | tr -d '\r')"
  echo "runtime: $(adb shell getprop persist.sys.dalvik.vm.lib.2 | tr -d '\r')"
  echo "cpu emulator: $(adb shell nproc | tr -d '\r') inti; $(adb shell grep -m1 'model name' /proc/cpuinfo | tr -d '\r')"
  echo "cpu host: $(nproc) inti; $(grep -m1 'model name' /proc/cpuinfo)"
  echo "memori emulator: $(adb shell grep -m1 MemTotal /proc/meminfo | tr -d '\r')"
} > "$OUT/device.txt" 2>&1

# layar menyala dan tidak terkunci; dialog "tidak merespons" disembunyikan (mengotori tangkapan layar dan menahan layar saat CPU sesak)
adb shell input keyevent 224 >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb shell svc power stayon true >/dev/null 2>&1 || true
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true

# Persentase CPU emulator yang menganggur selama ±2 detik (dari /proc/stat).
cpu_idle_pct() {
  local a b
  a=$(adb shell head -n 1 /proc/stat | tr -d '\r'); sleep 2; b=$(adb shell head -n 1 /proc/stat | tr -d '\r')
  python3 -c 'import sys
a=[int(v) for v in sys.argv[1].split()[1:]]; b=[int(v) for v in sys.argv[2].split()[1:]]
d=[y-x for x,y in zip(a,b)]; t=sum(d) or 1; print(int(100*d[3]/t))' "$a" "$b" 2>/dev/null || echo 0
}
# Tunggu sistem emulator tenang (selesai kompilasi awal pascaboot) agar pengukuran tidak ikut disaingi pekerjaan sistem.
settle() {
  local ok=0 idle n=0
  : > "$OUT/settle-$1.txt"
  for _ in $(seq 1 45); do
    idle=$(cpu_idle_pct); n=$((n + 1)); echo "idle ${idle}%" >> "$OUT/settle-$1.txt"
    if [ "${idle:-0}" -ge 90 ]; then ok=$((ok + 1)); else ok=0; fi
    [ "$ok" -ge 2 ] && break
  done
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
}
settle awal
adb logcat -c || true

DBG_APK=$(ls apk-dbg/*.apk | head -1)
ND_APK=$(ls apk-nd/*.apk | head -1)
TEST_APK=$(ls app/build/outputs/apk/androidTest/debug/*.apk | head -1)
RELEASE_APK=$(ls app/build/outputs/apk/release/*.apk | head -1)
RUNNER="$PKG.test/androidx.test.runner.AndroidJUnitRunner"

# ---------- 1) uji instrumented pada build debug (debuggable) ----------
# ColdLoadTest dan ArtProfileTest sengaja dikeluarkan: keduanya hanya bermakna bila dijalankan sendiri di proses baru (fase 3).
adb install -r -t "$DBG_APK" > "$OUT/install-debug.txt" 2>&1
adb install -r -t "$TEST_APK" > "$OUT/install-test.txt" 2>&1
adb shell am instrument -w -r -e notClass com.example.ColdLoadTest,com.example.ArtProfileTest "$RUNNER" > "$OUT/instrument.txt" 2>&1
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
ls -l "$RELEASE_APK" "$DBG_APK" "$ND_APK" | awk '{print $5, $9}' > "$OUT/apk-sizes.txt"
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
adb uninstall "$PKG" > /dev/null 2>&1 || true

# ---------- 3) muat simpanan akhir permainan dari PROSES BARU ----------
# Mengukur apa yang dialami pemain: berapa lama simpanan ±380 bulan diputar ulang. Dua build (debuggable "dbg" dan tidak debuggable
# "nd") dan dua keadaan kompilasi: bawaan pemasangan (hanya diverifikasi; interpreter + JIT, seperti APK yang baru dipasang manual)
# dan AOT penuh (batas atas kecepatan, seperti aplikasi yang sudah dioptimalkan sistem). Hasil informatif, tidak menggagalkan job,
# kecuali pemulihan tidak pernah selesai (status cold_load_finished=0).
: > "$OUT/cold-load.txt"
cold_finished=1
cold_tests_bad=0

# instr <label> <argumen -e ...>: jalankan ColdLoadTest/ArtProfileTest di proses baru; keluaran ke emulator-out/cold/instr-<label>.txt
instr() {
  local label="$1"; shift
  adb shell am instrument -w -r "$@" "$RUNNER" > "$OUT/cold/instr-$label.txt" 2>&1
  python3 tools/parse_instrument.py "$OUT/cold/instr-$label.txt" > /dev/null 2>&1
}

# cold_test <label>: pemulihan diukur di dalam uji (GameRepository persis seperti KapitalViewModel), proses instrumentasi baru
cold_test() {
  adb shell am force-stop "$PKG" > /dev/null 2>&1
  adb logcat -c
  if instr "$1" -e class com.example.ColdLoadTest -e label "$1" -e turns 380; then
    echo "uji   $1: $(adb logcat -d -s KAPITAL-LOAD:I | grep "COLD label=$1" | head -1 | sed 's/^.*KAPITAL-LOAD: //')" >> "$OUT/cold-load.txt"
  else
    cold_tests_bad=$((cold_tests_bad + 1))
    echo "uji   $1: GAGAL (lihat cold/instr-$1.txt)" >> "$OUT/cold-load.txt"
  fi
}

# cold_app <label>: simpanan ditulis oleh uji, lalu aplikasi SUNGGUHAN dibuka dari proses mati; KapitalViewModel mencatat waktunya
cold_app() {
  adb shell am force-stop "$PKG" > /dev/null 2>&1
  if ! instr "$1-siapkan" -e class com.example.ColdLoadTest -e label "$1" -e turns 380 -e mode siapkan; then
    cold_tests_bad=$((cold_tests_bad + 1))
    echo "app   $1: GAGAL menyiapkan simpanan (lihat cold/instr-$1-siapkan.txt)" >> "$OUT/cold-load.txt"
    return
  fi
  adb shell am force-stop "$PKG" > /dev/null 2>&1
  adb logcat -c
  local t0 t1 line=""
  t0=$(date +%s)
  adb shell am start -W -n "$PKG/com.example.MainActivity" > "$OUT/cold/start-$1.txt" 2>&1
  adb exec-out screencap -p > "$OUT/cold/layar-$1-memuat.png" 2>/dev/null || true
  for _ in $(seq 1 240); do
    line=$(adb logcat -d -s KAPITAL-LOAD:I 2>/dev/null | grep "restorePending selesai" | head -1)
    [ -n "$line" ] && break
    sleep 1
  done
  t1=$(date +%s)
  if [ -z "$line" ]; then cold_finished=0; line="TIDAK SELESAI dalam 240 s"; else line="${line##*KAPITAL-LOAD: }"; fi
  echo "app   $1: ${line} | diamati skrip $((t1 - t0)) s | $(grep -E 'TotalTime|WaitTime' "$OUT/cold/start-$1.txt" | tr '\r\n' '  ')" >> "$OUT/cold-load.txt"
  adb exec-out screencap -p > "$OUT/cold/layar-$1-selesai.png" 2>/dev/null || true
  adb shell am force-stop "$PKG" > /dev/null 2>&1
}

# profile <label>: profil pengambilan sampel + biaya fungsi matematika di proses baru; hasil disalin sebelum aplikasi dicopot
profile() {
  adb shell am force-stop "$PKG" > /dev/null 2>&1
  adb logcat -c
  if instr "prof-$1" -e class com.example.ArtProfileTest; then :; else cold_tests_bad=$((cold_tests_bad + 1)); fi
  adb pull "/sdcard/Android/data/$PKG/files/art_profile.txt" "$OUT/cold/art_profile-$1.txt" > /dev/null 2>&1 || true
  adb pull "/sdcard/Android/data/$PKG/files/math_cost.txt" "$OUT/cold/math_cost-$1.txt" > /dev/null 2>&1 || true
}

dexopt_state() {  # $1 = label
  { echo "== $1"; adb shell dumpsys package "$PKG" | grep -E "pkgFlags=|^\s+flags=" | head -2
    adb shell dumpsys package dexopt 2>/dev/null | grep -A2 "\[$PKG\]" | head -8; } >> "$OUT/dexopt-state.txt" 2>&1
}

install_variant() {  # $1 = nd|dbg, $2 = apk
  adb uninstall "$PKG" > /dev/null 2>&1 || true
  adb uninstall "$PKG.test" > /dev/null 2>&1 || true
  adb install -r -t "$2" > "$OUT/cold/install-$1.txt" 2>&1
  adb install -r -t "$TEST_APK" > "$OUT/cold/install-test-$1.txt" 2>&1
  dexopt_state "$1 setelah pasang"
}

compile_aot() {  # $1 = label
  adb shell cmd package compile -m speed -f "$PKG" > "$OUT/cold/aot-compile-$1.txt" 2>&1
  dexopt_state "$1 setelah compile -m speed"
}

: > "$OUT/dexopt-state.txt"

# --- build TIDAK debuggable (yang mendekati pengalaman pemain) ---
install_variant nd "$ND_APK"
# 0 = benar tidak debuggable (yang diharapkan); 1 = APK "nd" ternyata masih debuggable, jadi perbandingan di bawah tak berarti
if adb shell dumpsys package "$PKG" | grep -E "pkgFlags=|flags=" | grep -q DEBUGGABLE; then status "nd_debuggable=1"; else status "nd_debuggable=0"; fi
settle nd
cold_test nd-jit1
cold_test nd-jit2
cold_app nd-jit1
profile nd-jit
compile_aot nd
cold_test nd-aot1
cold_test nd-aot2
cold_app nd-aot1
profile nd-aot
adb pull "/sdcard/Android/data/$PKG/files/." "$OUT/cold/files-nd/" > /dev/null 2>&1 || true

# --- build debuggable (pembanding dengan run sebelumnya) ---
install_variant dbg "$DBG_APK"
settle dbg
cold_test dbg-jit1
cold_app dbg-jit1
compile_aot dbg
cold_test dbg-aot1
cold_app dbg-aot1
adb pull "/sdcard/Android/data/$PKG/files/." "$OUT/cold/files-dbg/" > /dev/null 2>&1 || true

adb logcat -d -s KAPITAL-LOAD:I KAPITAL-ART:I > "$OUT/cold/logcat-terakhir.txt" 2>&1 || true
adb shell am force-stop "$PKG" > /dev/null 2>&1 || true
adb uninstall "$PKG.test" > /dev/null 2>&1 || true
adb uninstall "$PKG" > /dev/null 2>&1 || true
status "cold_load_finished=$cold_finished"
status "cold_tests_bad=$cold_tests_bad"
exit 0
