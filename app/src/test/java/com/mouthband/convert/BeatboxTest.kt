package com.mouthband.convert

import com.mouthband.audio.AlignedTake
import com.mouthband.dsp.Rms
import com.mouthband.model.DrumClass
import com.mouthband.model.DrumHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

class BeatboxTest {
    private val rate = 48_000

    /** Synthetic beatbox sounds: a pitched-down sine kick, a mid noise snare, a bright noise hat. */
    private fun addHit(out: FloatArray, at: Int, cls: DrumClass, gain: Float, random: Random) {
        val length = (0.08 * rate).toInt()
        var hp = 0f
        var prev = 0f
        var lp = 0f
        for (n in 0 until length) {
            if (at + n >= out.size) return
            val t = n.toDouble() / rate
            val v = when (cls) {
                DrumClass.KICK -> sin(2 * PI * (60 + 90 * exp(-t * 40)) * t) * exp(-t * 25)
                DrumClass.SNARE -> {
                    // "pff": band-limited breath noise plus a little body.
                    lp += 0.25f * (random.nextGaussian().toFloat() - lp)
                    (lp * 1.5 + sin(2 * PI * 200 * t) * 0.3) * exp(-t * 35)
                }
                DrumClass.HIHAT -> {
                    val noise = random.nextGaussian().toFloat()
                    hp = 0.9f * (hp + noise - prev)
                    prev = noise
                    hp * exp(-t * 60)
                }
            }
            out[at + n] += (v.toFloat() * gain)
        }
    }

    private fun noise(size: Int, db: Float, seed: Long): FloatArray {
        val random = Random(seed)
        val amp = Rms.fromDb(db)
        return FloatArray(size) { (random.nextGaussian() * amp).toFloat() }
    }

    @Test
    fun detectsEverySyntheticClickWithoutFalsePositives() {
        val random = Random(11)
        val signal = noise(rate * 4, -40f, 5)
        val truth = (0 until 20).map { 3000 + it * 9000 + (it % 3) * 700 }
        truth.forEachIndexed { i, at ->
            // Short decaying noise clicks at mixed levels (-6 … -24 dBFS peaks).
            val gain = Rms.fromDb(-6f - (i % 4) * 6f)
            for (n in 0 until 1200) signal[at + n] += (random.nextGaussian() * gain * exp(-n / 240.0)).toFloat()
        }
        val found = OnsetDetector(rate).detect(signal)
        assertEquals("recall + no false positives", truth.size, found.size)
        truth.zip(found).forEach { (t, f) -> assertTrue("onset $f vs $t", abs(f - t) <= rate / 200) }
    }

    @Test
    fun silenceHasNoOnsets() {
        assertTrue(OnsetDetector(rate).detect(noise(rate * 2, -40f, 9)).isEmpty())
    }

    @Test
    fun quantizerMath() {
        val step = 1000.0
        assertEquals(1000, Quantizer.frame(1100, step, 1f, 16_000))
        assertEquals(1100, Quantizer.frame(1100, step, 0f, 16_000))
        assertEquals(1020, Quantizer.frame(1100, step, 0.8f, 16_000))
        // An early downbeat wraps to the end of the loop at partial strength, to 0 at full strength.
        assertEquals(15_960, Quantizer.frame(-200, step, 0.8f, 16_000))
        assertEquals(0, Quantizer.frame(-200, step, 1f, 16_000))
        assertEquals(0, Quantizer.cell(15_800, step, 16))
    }

    @Test
    fun dedupeKeepsLouderOfSameClassInSameStep() {
        val hits = listOf(
            DrumHit(DrumClass.KICK, 990, 0.6f),
            DrumHit(DrumClass.KICK, 1040, 0.9f),
            DrumHit(DrumClass.SNARE, 1010, 0.7f),
        )
        val out = Quantizer.dedupe(hits, 1000.0, 16)
        assertEquals(2, out.size)
        assertEquals(0.9f, out.first { it.cls == DrumClass.KICK }.velocity)
    }

    @Test
    fun knnSeparatesSyntheticClusters() {
        val random = Random(4)
        fun vector(center: Float) = List(DrumFeatures.SIZE) { center + random.nextGaussian().toFloat() * 0.3f }
        val templates = BeatboxTemplates(
            DrumClass.entries.flatMapIndexed { i, cls -> List(4) { TemplateExample(cls, vector(i * 5f)) } },
        )
        val classifier = DrumClassifier(templates)
        assertTrue(classifier.usesTemplates)
        var correct = 0
        repeat(60) { n ->
            val cls = DrumClass.entries[n % 3]
            val query = vector(DrumClass.entries.indexOf(cls) * 5f).toFloatArray()
            if (classifier.classify(DrumFeatures.Analysis(query, 0f)) == cls) correct++
        }
        assertEquals(60, correct)
    }

    @Test
    fun rulesClassifySyntheticSounds() {
        val features = DrumFeatures(rate)
        val random = Random(2)
        DrumClass.entries.forEach { cls ->
            val buf = FloatArray(rate / 2)
            addHit(buf, 100, cls, 0.5f, random)
            assertEquals(cls, DrumClassifier.byRules(features.extract(buf, 100)))
        }
    }

    @Test
    fun converterFindsKickSnareHatPattern() {
        val bpm = 100
        val step = 60.0 / bpm * rate / 4
        val loop = (16 * step).toInt()
        val preroll = 4800
        val samples = noise(preroll + loop + 12_000, -50f, 3)
        val random = Random(8)
        val pattern = listOf(0 to DrumClass.KICK, 2 to DrumClass.HIHAT, 4 to DrumClass.SNARE, 6 to DrumClass.HIHAT,
            8 to DrumClass.KICK, 10 to DrumClass.HIHAT, 12 to DrumClass.SNARE, 14 to DrumClass.HIHAT)
        pattern.forEach { (s, cls) -> addHit(samples, preroll + (s * step).toInt(), cls, 0.4f, random) }
        val take = AlignedTake(samples, preroll, loop, rate)
        val result = BeatboxConverter(rate).convert(take, bpm, templates = null, clicksAudible = false)
        assertNull(result.problem)
        val cells = Quantizer.hits(result.hits, step, 1f, loop).map { (it.frameInLoop / step).toInt() to it.cls }
        assertEquals(pattern, cells)
        assertTrue(result.hits.all { it.velocity in 0.5f..1f })
    }

    @Test
    fun converterReportsSilence() {
        val take = AlignedTake(FloatArray(100_000), 4800, 90_000, rate)
        assertEquals(TakeProblem.SILENCE, BeatboxConverter(rate).convert(take, 100, null, false).problem)
    }
}
