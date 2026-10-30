#!/usr/bin/env bash
# Membangkitkan app/src/androidTest/assets/replay_fixture.txt (kampanye penuh + ringkasan keadaan) TANPA Android SDK.
# Wajib dijalankan ulang setiap kali ENGINE_VERSION (data/GameRepository.kt) dinaikkan; ReplayFixtureTest gagal bila tidak.
#
# Pemakaian: KOTLINC_HOME=/jalur/ke/kotlinc tools/gen-replay-fixture.sh
set -euo pipefail
: "${KOTLINC_HOME:?Setel KOTLINC_HOME ke folder kotlinc (berisi bin/kotlinc dan lib/)}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
M="$ROOT/app/src/main/java/com/example"
T="$ROOT/app/src/test/java/com/example"
S="$ROOT/app/src/shared/kotlin/com/example"
OUT="${OUT_DIR:-$(mktemp -d)}"
KC="$KOTLINC_HOME/bin/kotlinc"
CO="$KOTLINC_HOME/lib/kotlinx-coroutines-core-jvm.jar"
STD="$KOTLINC_HOME/lib/kotlin-stdlib.jar"
mkdir -p "$OUT"
"$KC" -nowarn -jvm-target 11 -cp "$CO" -d "$OUT/engine.jar" "$M/engine" "$M/data" "$M/model"
"$KC" -nowarn -jvm-target 11 -cp "$OUT/engine.jar:$CO" -d "$OUT/gen.jar" \
  "$S/ReplayDigest.kt" "$S/ReplayFixture.kt" "$T/TestBots.kt" "$T/tools/GenReplayFixture.kt"
java -cp "$OUT/gen.jar:$OUT/engine.jar:$STD:$CO" com.example.tools.GenReplayFixtureKt "$ROOT/app/src/androidTest/assets/replay_fixture.txt"
