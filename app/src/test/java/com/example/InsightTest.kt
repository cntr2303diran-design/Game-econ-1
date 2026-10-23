package com.example

import com.example.engine.*
import org.junit.Assert.*
import org.junit.Test

/** Tahap B7: pasar prediksi, detektor bias kognitif, post-mortem. */
class InsightTest {

    private fun check(e: KapitalEngine) {
        assertEquals(0.0, e.totalAssets() - e.totalLiabilities() - e.equity, 1e-6)
        val d = e.lastDetail
        if (d.isNotEmpty()) {
            assertEquals("arus kas = perubahan kas", 0.0, d["d_resid"] ?: 0.0, 1e-6)
            assertEquals("roll-forward ekuitas", 0.0, d["e_resid"] ?: 0.0, 1e-6)
        }
    }

    /** Main disiplin; [policy] dipanggil tiap giliran untuk menaruh taruhan. */
    private fun play(seed: Long, turns: Int, policy: (KapitalEngine, Int) -> Unit): KapitalEngine {
        val e = KapitalEngine(seed)
        var t = 0
        while (e.gameOverReason == null && t < turns) {
            t++
            policy(e, t)
            TestBots.disciplinedStep(e, t)
            e.apply(Action.EndTurn)
            check(e)
        }
        return e
    }

    @Test
    fun actionEncodeDecode_roundTrip() {
        val a = Action.Predict(7, true, 12)
        assertEquals("PMK|7|1|12", a.encode())
        assertEquals(a, Action.decode(a.encode()))
        assertEquals(Action.Predict(3, false, 1), Action.decode(Action.Predict(3, false, 1).encode()))
    }

    @Test
    fun betsMoveOnlyPoints_neverCashOrEquity_sameSeedIdenticalMoneyPath() {
        for (seed in listOf(11L, 23L, 37L, 51L)) {
            val plain = play(seed * 104729L, 60) { _, _ -> }
            val bet = play(seed * 104729L, 60) { e, _ ->
                for (q in e.predMarket.open()) e.apply(Action.Predict(q.id, true, 5))
            }
            if (plain.gameOverReason != null || bet.gameOverReason != null) { assertEquals(plain.gameOverReason, bet.gameOverReason); continue }
            assertEquals("kas sama", plain.cash, bet.cash, 0.0)
            assertEquals("ekuitas sama", plain.equity, bet.equity, 0.0)
            assertEquals("pertanyaan sama", plain.predMarket.questions.map { it.text }, bet.predMarket.questions.map { it.text })
        }
    }

    @Test
    fun betRules_blockDoubleBetsAndOversizedStakes() {
        val e = play(5L * 104729L, 12) { _, _ -> }
        val q = e.predMarket.open().firstOrNull() ?: fail2("ada pertanyaan terbuka")
        assertFalse("taruhan 0 ditolak", e.apply(Action.Predict(q.id, true, 0)))
        assertFalse("taruhan melebihi 20%", e.apply(Action.Predict(q.id, true, 21)))
        assertFalse("pertanyaan tak ada", e.apply(Action.Predict(9999, true, 5)))
        assertTrue(e.apply(Action.Predict(q.id, false, 10)))
        assertEquals(90.0, e.predMarket.points, 1e-9)
        assertFalse("posisi ganda ditolak", e.apply(Action.Predict(q.id, true, 5)))
        val pos = e.predMarket.positionOf(q.id)!!
        assertEquals(1.0 - q.crowdYes, pos.price, 1e-12)
    }

    private fun fail2(msg: String): Nothing = throw AssertionError(msg)

    @Test
    fun resolution_paysAtCrowdPriceWithFee() {
        val e = play(8L * 104729L, 4) { _, _ -> }
        val q = e.predMarket.open().first()
        e.apply(Action.Predict(q.id, true, 10))
        val price = q.crowdYes
        var t = 0
        while (e.gameOverReason == null && q.outcome == null && t < 20) { t++; TestBots.disciplinedStep(e, 100 + t); e.apply(Action.EndTurn) }
        assertNotNull(q.outcome)
        val pos = e.predMarket.positionOf(q.id)!!
        if (q.outcome == true) {
            assertEquals(10.0 / price * (1.0 - PredictionMarket.FEE), pos.payout!!, 1e-9)
        } else assertEquals(0.0, pos.payout!!, 0.0)
    }

    @Test
    fun crowdIsInformativeButNotPerfect_andRandomBettorsGainNothing_200seeds() {
        var n = 0; var brierSum = 0.0; var pointsSum = 0.0; var maxPoints = 0.0; var staked = 0.0; var bonusMax = 0
        for (seed in 1L..200L) {
            val r = java.util.Random(seed)
            val e = play(seed * 7919L, 90) { en, _ ->
                for (q in en.predMarket.open()) if (r.nextInt(3) == 0 && en.predMarket.positionOf(q.id) == null) {
                    val st = 1 + r.nextInt(5)
                    if (en.apply(Action.Predict(q.id, r.nextBoolean(), st))) staked += st
                }
            }
            val res = e.predMarket.resolved()
            if (res.isNotEmpty()) { brierSum += e.predMarket.crowdBrier() * res.size; n += res.size }
            pointsSum += e.predMarket.points; maxPoints = maxOf(maxPoints, e.predMarket.points); bonusMax = maxOf(bonusMax, e.predMarket.scoreBonus())
        }
        val meanPts = pointsSum / 200
        val crowd = brierSum / n
        println("INSIGHT pasar: pertanyaan selesai=$n Brier kerumunan=${"%.3f".format(crowd)} | penebak acak 200 seed: rata-rata poin=${"%.2f".format(meanPts)} maks=${"%.1f".format(maxPoints)} dipertaruhkan=${staked.toInt()} bonus skor maks=$bonusMax")
        assertTrue("kerumunan lebih baik dari menebak 50/50: $crowd", crowd < 0.245)
        assertTrue("kerumunan tidak sempurna: $crowd", crowd > 0.08)
        assertTrue("penebak acak tidak untung wajar: $meanPts", meanPts < PredictionMarket.START_POINTS + 2.0)
        assertTrue("tidak merugi tak wajar: $meanPts", meanPts > PredictionMarket.START_POINTS - 30.0)
        assertTrue("bonus skor dibatasi", bonusMax <= PredictionMarket.MAX_SCORE_BONUS)
    }

    @Test
    fun biasDetector_flagsOverconfidence_planning_underconfidence_statusQuo() {
        fun jr(conf: Int, pred: Double, actual: Double) = JournalRecord("j", "x", 1, 7, "r", "p", conf, "f", pred, actual >= pred, actual)
        val over = BiasDetector.detect(listOf(jr(90, 1000.0, 700.0), jr(90, 1000.0, 800.0), jr(90, 1000.0, 1200.0), jr(90, 1000.0, 600.0)),
            PredictionMarket(), emptyList(), 5, 50, false).map { it.code }
        assertTrue("OVERCONFIDENCE: $over", "OVERCONFIDENCE" in over)
        assertTrue("PLANNING: $over", "PLANNING" in over)
        val under = BiasDetector.detect(listOf(jr(40, 1000.0, 1100.0), jr(40, 1000.0, 1300.0), jr(40, 1000.0, 1500.0)),
            PredictionMarket(), emptyList(), 5, 50, false).map { it.code }
        assertTrue("UNDERCONFIDENCE: $under", "UNDERCONFIDENCE" in under)
        assertFalse("tanpa perencanaan buruk: $under", "PLANNING" in under)
        val hist = (1..40).map { MonthSnap(it, 100.0, 50.0, 200.0, 100.0, 5.0, 10.0, 0.0, 0, 1.0 + 0.01 * it) }
        val sq = BiasDetector.detect(emptyList(), PredictionMarket(), hist, 5, 50, false).map { it.code }
        assertTrue("STATUS_QUO: $sq", "STATUS_QUO" in sq)
        val ok = BiasDetector.detect(emptyList(), PredictionMarket(), hist.map { MonthSnap(it.turn, 100.0, 50.0, 200.0, 100.0, 5.0, 10.0, 0.0, it.turn / 4, it.costIndex) }, 5, 50, false)
        assertTrue("harga dinaikkan → bukan status quo", ok.none { it.code == "STATUS_QUO" })
        assertTrue("konfirmasi tanpa cek silang", BiasDetector.detect(emptyList(), PredictionMarket(), emptyList(), 0, 60, false).any { it.code == "CONFIRMATION" })
        assertTrue("sampel kecil tak dituduh", BiasDetector.detect(listOf(jr(95, 1000.0, 10.0)), PredictionMarket(), emptyList(), 5, 10, false).isEmpty())
    }

    @Test
    fun biasDetector_marketPatterns_directionAndLossChasing() {
        var dir = 0; var chase = 0; var tried = 0
        for (seed in 1L..40L) {
            val e = play(seed * 104729L, 130) { en, _ ->
                for (q in en.predMarket.open()) if (en.predMarket.positionOf(q.id) == null) en.apply(Action.Predict(q.id, true, 2))
            }
            if (e.predMarket.positions.count { it.payout != null } >= 6) { tried++; if (e.biasFindings().any { it.code == "DIRECTION" }) dir++ }
            val e2 = play(seed * 104729L, 130) { en, _ ->
                val last = en.predMarket.positions.lastOrNull { it.payout != null }
                for (q in en.predMarket.open()) if (en.predMarket.positionOf(q.id) == null)
                    en.apply(Action.Predict(q.id, (q.id % 2) == 0, if (last?.won == false) en.predMarket.maxStake() else 1))
            }
            if (e2.biasFindings().any { it.code == "LOSS_CHASING" }) chase++
        }
        println("INSIGHT bias pasar: selalu-YA terdeteksi=$dir/$tried; pengejar kerugian terdeteksi=$chase/40")
        assertTrue("pola satu arah terdeteksi: $dir/$tried", tried >= 10 && dir >= tried * 0.9)
        assertTrue("pengejar kerugian terdeteksi pada sebagian seed: $chase", chase >= 5)
    }

    @Test
    fun postMortem_explainsFailure_deterministically() {
        var checked = 0; var withCause = 0
        for (seed in 1L..120L) {
            val e = KapitalEngine(seed * 7919L)
            var t = 0
            while (e.gameOverReason == null && t < 400) { t++; TestBots.disciplinedStep(e, t); e.apply(Action.EndTurn) }
            if (e.gameOverReason == null || e.isVictory) continue
            val pm = e.postMortem(); val pm2 = e.postMortem()
            assertEquals("deterministik", pm.lines(), pm2.lines())
            assertTrue(pm.headline.startsWith("Akhir permainan"))
            assertTrue("titik balik maks. 3", pm.turningPoints.size <= 3)
            assertTrue("ada pelajaran", pm.lessons.isNotEmpty())
            assertTrue("ada kualitas keputusan", pm.decisionQuality.isNotEmpty())
            checked++; if (pm.causes.isNotEmpty()) withCause++
            if (checked >= 12) break
        }
        println("INSIGHT post-mortem: $checked permainan gagal diperiksa, $withCause dengan penyebab teridentifikasi")
        assertTrue("ada permainan gagal untuk diperiksa: $checked", checked >= 5)
        assertTrue("sebagian besar kegagalan punya penyebab teridentifikasi: $withCause/$checked", withCause >= checked * 0.6)
    }

    @Test
    fun postMortem_ongoingGame_hasHeadlineAndHistory() {
        val e = play(3L * 7919L, 50) { _, _ -> }
        val pm = e.postMortem()
        assertTrue(pm.headline.contains("sementara") || e.gameOverReason != null)
        assertTrue("riwayat bulanan tercatat: ${e.history.size}", e.history.size >= 40 || e.gameOverReason != null)
    }
}
