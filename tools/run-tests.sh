#!/usr/bin/env bash
# Menjalankan tes mesin TANPA Android SDK/Gradle: kompilasi engine/, data/, model/ dengan
# kotlinc JVM, lalu jalankan kelas tes dengan stub JUnit + runner refleksi minimal.
#
# Pemakaian:
#   KOTLINC_HOME=/jalur/ke/kotlinc tools/run-tests.sh            # semua kelas tes
#   KOTLINC_HOME=/jalur/ke/kotlinc tools/run-tests.sh NciTest    # kelas tertentu
#
# KOTLINC_HOME = folder hasil unzip kotlin-compiler-2.0.21.zip (berisi bin/ dan lib/).
# Ini jalur cepat untuk pengembangan; sumber kebenaran resmi tetap `gradle :app:testDebugUnitTest` di CI.
set -euo pipefail

: "${KOTLINC_HOME:?Setel KOTLINC_HOME ke folder kotlinc (berisi bin/kotlinc dan lib/)}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
M="$ROOT/app/src/main/java/com/example"
T="$ROOT/app/src/test/java/com/example"
OUT="${OUT_DIR:-$(mktemp -d)}"
LIB="$KOTLINC_HOME/lib"
KC="$KOTLINC_HOME/bin/kotlinc"
CO="$LIB/kotlinx-coroutines-core-jvm.jar"
STD="$LIB/kotlin-stdlib.jar"
mkdir -p "$OUT/jsrc/org/junit" "$OUT/jcls"

# --- Stub JUnit (hanya API yang dipakai tes proyek ini) ---
cat > "$OUT/jsrc/org/junit/Assert.java" <<'EOF'
package org.junit;
public class Assert {
  public static void assertTrue(boolean c) { if (!c) throw new AssertionError("assertTrue gagal"); }
  public static void assertTrue(String m, boolean c) { if (!c) throw new AssertionError(m); }
  public static void assertFalse(boolean c) { if (c) throw new AssertionError("assertFalse gagal"); }
  public static void assertFalse(String m, boolean c) { if (c) throw new AssertionError(m); }
  public static void assertNotNull(Object o) { if (o == null) throw new AssertionError("assertNotNull gagal"); }
  public static void assertNotNull(String m, Object o) { if (o == null) throw new AssertionError(m); }
  public static void assertEquals(Object a, Object b) { if (!java.util.Objects.equals(a, b)) throw new AssertionError("expected <" + a + "> but was <" + b + ">"); }
  public static void assertEquals(String m, Object a, Object b) { if (!java.util.Objects.equals(a, b)) throw new AssertionError(m + ": expected <" + a + "> but was <" + b + ">"); }
  public static void assertEquals(long a, long b) { if (a != b) throw new AssertionError("expected <" + a + "> but was <" + b + ">"); }
  public static void assertEquals(String m, long a, long b) { if (a != b) throw new AssertionError(m + ": expected <" + a + "> but was <" + b + ">"); }
  public static void assertEquals(double a, double b, double d) { if (Math.abs(a - b) > d) throw new AssertionError("expected <" + a + "> but was <" + b + "> (tol " + d + ")"); }
  public static void assertEquals(String m, double a, double b, double d) { if (Math.abs(a - b) > d) throw new AssertionError(m + ": expected <" + a + "> but was <" + b + "> (tol " + d + ")"); }
}
EOF
cat > "$OUT/Junit.kt" <<'EOF'
package org.junit
annotation class Test
EOF
cat > "$OUT/Runner.kt" <<'EOF'
import java.lang.reflect.InvocationTargetException
fun main(args: Array<String>) {
  var pass = 0; var fail = 0
  for (cn in args) {
    val k = Class.forName(cn); val inst = k.getDeclaredConstructor().newInstance()
    for (m in k.declaredMethods.sortedBy { it.name }) {
      if (m.getAnnotation(org.junit.Test::class.java) == null) continue
      val t0 = System.nanoTime()
      try { m.invoke(inst); pass++; println("PASS ${k.simpleName}.${m.name} (${(System.nanoTime()-t0)/1_000_000} ms)") }
      catch (e: InvocationTargetException) { fail++; println("FAIL ${k.simpleName}.${m.name}: ${e.targetException}") }
    }
  }
  println("== LULUS $pass, GAGAL $fail"); if (fail > 0) System.exit(1)
}
EOF
javac -d "$OUT/jcls" "$OUT/jsrc/org/junit/Assert.java"
"$KC" -nowarn -jvm-target 11 -d "$OUT/stub.jar" "$OUT/Junit.kt"
(cd "$OUT/jcls" && jar uf "$OUT/stub.jar" org/junit/Assert.class)
"$KC" -nowarn -jvm-target 11 -cp "$OUT/stub.jar" -d "$OUT/runner.jar" "$OUT/Runner.kt"

# --- Mesin + tes ---
"$KC" -nowarn -jvm-target 11 -cp "$CO" -d "$OUT/engine.jar" "$M/engine" "$M/data" "$M/model"
TESTS=("$T/ArgonCorporateTest.kt" "$T/ExampleUnitTest.kt" "$T/StressCalibrationTest.kt" "$T/TestBots.kt")
for extra in "$T"/*Test.kt; do
  case "$(basename "$extra")" in
    ArgonCorporateTest.kt|ExampleUnitTest.kt|StressCalibrationTest.kt|ExampleRobolectricTest.kt) ;;
    *) TESTS+=("$extra") ;;
  esac
done
"$KC" -nowarn -jvm-target 11 -cp "$OUT/stub.jar:$OUT/engine.jar:$CO" -d "$OUT/tests.jar" "${TESTS[@]}"

DEFAULT_CLASSES=(ArgonCorporateTest ExampleUnitTest StressCalibrationTest ChainCardsTest EraCrisisTest PublicCompanyTest
  EvaluationTest GroupStatementsUiTest TransferPricingAuditTest RivalLearningTest DifficultyTest EraReviewTest
  StockTest NciTest SuccessionTest BalanceTest PerformanceTest AgentScaleTest WorldLayerTest MacroPlusTest MarketTest CreditSystemTest LongCyclesTest StageAStressTest WorldViewTest IntelBoardTest AntiExploitTest ProductMarketTest OperationsTest FinanceTest DerivativesTest MinorityTest ForensicsTest LawTest ActorsTest InsightTest SaveSlotsTest ActiveBotTest CardDeckTest RolesTest ChallengeTest EraUnlockTest AuditGuardsTest)
if [ "$#" -gt 0 ]; then CLASSES=("$@"); else CLASSES=("${DEFAULT_CLASSES[@]}"); fi
FQ=(); for c in "${CLASSES[@]}"; do FQ+=("com.example.$c"); done
exec java -cp "$OUT/stub.jar:$OUT/engine.jar:$OUT/tests.jar:$OUT/runner.jar:$STD:$CO" RunnerKt "${FQ[@]}"
