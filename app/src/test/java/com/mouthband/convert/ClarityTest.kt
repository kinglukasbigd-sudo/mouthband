package com.mouthband.convert

import com.mouthband.audio.AlignedTake
import com.mouthband.dsp.Rms
import com.mouthband.model.DrumClass
import com.mouthband.model.KeyMode
import com.mouthband.model.MusicalKey
import com.mouthband.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Every take gets its notes however loudly or quietly it was recorded: the same performance is
 * scaled across the whole range a phone microphone produces and the result must not change.
 */
class ClarityTest {
    private val rate = 48_000
    private val key = MusicalKey(0, KeyMode.MAJOR)
    private val processor = TakeProcessor(rate, BeatboxConverter(rate), PitchConverter(rate, primary = { null }))
    private val context = TakeProcessor.Context(bpm = 120, key = key, templates = null, clicksAudible = false)

    private fun midiToHz(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** A hummed take with a slight room/mic noise floor: [notes] as (midi, start s, length s). */
    private fun hummed(notes: List<Triple<Int, Double, Double>>, noiseDb: Float): AlignedTake {
        val preroll = (0.1 * rate).toInt()
        val loop = 4 * rate
        val samples = FloatArray(preroll + loop + (0.25 * rate).toInt())
        notes.forEach { (midi, start, length) ->
            val hz = midiToHz(midi)
            val from = preroll + (start * rate).toInt()
            val n = (length * rate).toInt()
            for (i in 0 until n) {
                val t = i.toDouble() / rate
                val env = minOf(1.0, i / (0.01 * rate), (n - i) / (0.01 * rate))
                val v = sin(2 * PI * hz * t) + 0.4 * sin(4 * PI * hz * t) + 0.2 * sin(6 * PI * hz * t)
                samples[from + i] += (0.3 * env * v).toFloat()
            }
        }
        val random = Random(4)
        val noise = Rms.fromDb(noiseDb)
        for (i in samples.indices) samples[i] += (random.nextGaussian() * noise).toFloat()
        return AlignedTake(samples, preroll, loop, rate)
    }

    /** The take with its loudest sample moved to [peakDb] dBFS; anything above full scale clips. */
    private fun AlignedTake.atPeak(peakDb: Float): AlignedTake {
        val gain = Rms.fromDb(peakDb) / Rms.peak(samples)
        val scaled = FloatArray(samples.size) { (samples[it] * gain).coerceIn(-1f, 1f) }
        return AlignedTake(scaled, prerollFrames, loopFrames, sampleRate)
    }

    private val bassLine = listOf(Triple(48, 0.0, 0.45), Triple(55, 1.0, 0.45), Triple(57, 2.0, 0.45))

    @Test
    fun aHummedBassLineKeepsItsThreeNotesFromWhisperToClipping() {
        for (peakDb in listOf(-66f, -60f, -50f, -40f, -30f, -20f, -10f, -3f, 0f, 6f)) {
            // The noise floor stays 40 dB under the hum, as a quiet room does at any gain.
            val take = hummed(bassLine, noiseDb = -46f).atPeak(peakDb)
            val result = processor.process(TrackType.BASS, take, context)
            assertNull("$peakDb dBFS: ${result.problem}", result.problem)
            val midis = result.events?.notes?.map { it.midi }.orEmpty()
            // A note sung a hair early wraps to the end of the cyclic loop, so compare the set of notes.
            assertEquals("$peakDb dBFS", listOf(33, 36, 43), midis.sorted())
        }
    }

    @Test
    fun hummedChordRootsKeepTheirChordsAtEveryLevel() {
        val roots = listOf(Triple(60, 0.0, 0.5), Triple(65, 2.0, 0.6))
        for (peakDb in listOf(-66f, -50f, -30f, -10f, 0f, 6f)) {
            val result = processor.process(TrackType.CHORDS, hummed(roots, -46f).atPeak(peakDb), context)
            assertNull("$peakDb dBFS: ${result.problem}", result.problem)
            val chords = result.events?.notes.orEmpty().groupBy { it.startFrame }
            assertEquals("$peakDb dBFS", 2, chords.size)
            assertTrue(chords.values.all { it.size == 3 })
        }
    }

    // -- beatbox ----------------------------------------------------------------------------

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
            out[at + n] += v.toFloat() * gain
        }
    }

    private val pattern = listOf(
        0 to DrumClass.KICK,
        2 to DrumClass.HIHAT,
        4 to DrumClass.SNARE,
        6 to DrumClass.HIHAT,
        8 to DrumClass.KICK,
        10 to DrumClass.HIHAT,
        12 to DrumClass.SNARE,
        14 to DrumClass.HIHAT,
    )

    private fun beat(bpm: Int): Pair<AlignedTake, Double> {
        val step = 60.0 / bpm * rate / 4
        val loop = (16 * step).toInt()
        val preroll = 4800
        val samples = FloatArray(preroll + loop + 12_000)
        val random = Random(8)
        pattern.forEach { (s, cls) -> addHit(samples, preroll + (s * step).toInt(), cls, 0.4f, random) }
        val noise = Random(3)
        val floor = Rms.fromDb(-50f)
        for (i in samples.indices) samples[i] += (noise.nextGaussian() * floor).toFloat()
        return AlignedTake(samples, preroll, loop, rate) to step
    }

    @Test
    fun aBeatKeepsEveryHitFromWhisperToClipping() {
        val bpm = 100
        val (base, step) = beat(bpm)
        val beatContext = TakeProcessor.Context(bpm, key, templates = null, clicksAudible = false)
        for (peakDb in listOf(-62f, -55f, -45f, -35f, -20f, -6f, 0f, 6f)) {
            val result = processor.process(TrackType.BEAT, base.atPeak(peakDb), beatContext)
            assertNull("$peakDb dBFS: ${result.problem}", result.problem)
            val hits = result.events?.hits.orEmpty()
            val cells = Quantizer.hits(hits, step, 1f, base.loopFrames)
                .map { (it.frameInLoop / step).toInt() to it.cls }
            assertEquals("$peakDb dBFS", pattern, cells)
        }
    }

    // -- nothing there ----------------------------------------------------------------------

    @Test
    fun roomNoiseAloneIsNotTurnedIntoNotesOrHits() {
        val random = Random(6)
        for (noiseDb in listOf(-90f, -80f, -70f, -60f, -50f)) {
            val amp = Rms.fromDb(noiseDb)
            val samples = FloatArray(rate * 5) { (random.nextGaussian() * amp).toFloat() }
            val take = AlignedTake(samples, 4800, 4 * rate, rate)
            for (type in listOf(TrackType.BASS, TrackType.MELODY, TrackType.BEAT)) {
                val result = processor.process(type, take, context)
                assertTrue("$type at $noiseDb dB noise: ${result.events}", result.events == null)
                assertTrue("$type at $noiseDb dB reports why", result.problem != null)
            }
        }
    }

    @Test
    fun digitalSilenceIsReportedAsSilence() {
        val take = AlignedTake(FloatArray(rate * 5), 4800, 4 * rate, rate)
        assertEquals(TakeProblem.SILENCE, processor.process(TrackType.BASS, take, context).problem)
        assertEquals(TakeProblem.SILENCE, processor.process(TrackType.BEAT, take, context).problem)
        assertEquals(TakeProblem.SILENCE, processor.process(TrackType.VOCALS_VERSE, take, context).problem)
    }

    // -- the conditioned audio itself -------------------------------------------------------

    @Test
    fun conditionedTakesLandOnOneLevelWhateverTheInput() {
        val levels = listOf(-60f, -40f, -20f, 0f).map { peakDb ->
            val conditioned = TakeConditioner.condition(hummed(bassLine, -46f).atPeak(peakDb))
            Rms.toDb(Rms.of(conditioned.take.samples, conditioned.take.prerollFrames, conditioned.take.loopFrames))
        }
        assertTrue("levels $levels", levels.max() - levels.min() < 3f)
    }

    @Test
    fun theKeptLoopIsTheConditionedOne() {
        val quiet = hummed(bassLine, -46f).atPeak(-60f)
        val converted = processor.process(TrackType.BASS, quiet, context)
        val before = Rms.toDb(Rms.of(quiet.loopOnly()))
        val after = Rms.toDb(Rms.of(converted.loop))
        assertTrue("kept loop is louder: $before → $after", after > before + 20f)
        assertEquals(quiet.loopFrames, converted.loop.size)
    }

    @Test
    fun conditioningNeverClipsAndNeverBoostsAboveTheCeiling() {
        for (peakDb in listOf(-60f, -20f, 0f)) {
            val conditioned = TakeConditioner.condition(hummed(bassLine, -46f).atPeak(peakDb))
            assertTrue(Rms.peak(conditioned.take.samples) <= 0.95f + 1e-3f)
        }
    }

    @Test
    fun aWellRecordedTakeIsLeftAlone() {
        val take = hummed(bassLine, -46f).atPeak(-12f)
        val conditioned = TakeConditioner.condition(take)
        assertTrue("gain ${conditioned.gainDb} dB", abs(conditioned.gainDb) < 6f)
        assertEquals(take.loopFrames, conditioned.take.loopFrames)
        assertEquals(take.samples.size, conditioned.take.samples.size)
    }

    @Test
    fun clippedPeaksAreRebuiltCloserToTheOriginalWave() {
        val hz = 220.0
        val original = FloatArray(rate) { (1.6 * sin(2 * PI * hz * it / rate)).toFloat() }
        val clipped = FloatArray(original.size) { original[it].coerceIn(-1f, 1f) }
        val repaired = Declip.repair(clipped, rate)
        assertTrue("clips found", repaired.runs > 0)
        fun error(a: FloatArray) = (rate / 4 until rate - rate / 4).sumOf {
            val d = (a[it] - original[it]).toDouble()
            d * d
        }
        val before = error(clipped)
        val after = error(repaired.samples)
        assertTrue("error $before → $after", after < before * 0.5)
    }

    @Test
    fun aLongSquishedRunIsLeftAlone() {
        val flat = FloatArray(rate) { 1f }
        val repaired = Declip.repair(flat, rate)
        assertEquals(0, repaired.runs)
        assertTrue(repaired.samples.all { it == 1f })
    }
}

/** A sung take is kept only when a voice is in it: noise is not turned into a loud hiss. */
class VocalNoiseTest {
    private val rate = 48_000
    private val processor = TakeProcessor(rate, BeatboxConverter(rate), PitchConverter(rate, primary = { null }))
    private val key = MusicalKey(0, KeyMode.MAJOR)
    private val context = TakeProcessor.Context(120, key, templates = null, clicksAudible = false)

    private fun noiseTake(db: Float, seed: Long): AlignedTake {
        val random = Random(seed)
        val amp = Rms.fromDb(db)
        return AlignedTake(FloatArray(rate * 5) { (random.nextGaussian() * amp).toFloat() }, 4_800, 4 * rate, rate)
    }

    private fun sungTake(peakDb: Float, pauses: Boolean): AlignedTake {
        val samples = FloatArray(rate * 5)
        val random = Random(9)
        for (i in samples.indices) {
            val t = i.toDouble() / rate
            val voiced = !pauses || (t % 1.0) < 0.7
            if (voiced) {
                val hz = 196.0 * (1 + 0.004 * sin(2 * PI * 5.5 * t))
                samples[i] = (sin(2 * PI * hz * t) + 0.5 * sin(4 * PI * hz * t) + 0.25 * sin(6 * PI * hz * t)).toFloat()
            }
        }
        val gain = Rms.fromDb(peakDb) / Rms.peak(samples)
        val floor = Rms.fromDb(peakDb - 40f)
        for (i in samples.indices) samples[i] = samples[i] * gain + (random.nextGaussian() * floor).toFloat()
        return AlignedTake(samples, 4_800, 4 * rate, rate)
    }

    @Test
    fun noiseAloneIsSilenceForVocalsAtEveryLevel() {
        for (db in listOf(-76f, -70f, -62f, -50f, -40f, -30f, -20f)) {
            for (type in listOf(TrackType.VOCALS_VERSE, TrackType.VOCALS_CHORUS)) {
                val result = processor.process(type, noiseTake(db, 11), context)
                assertEquals("$type noise at $db dB", TakeProblem.SILENCE, result.problem)
            }
        }
    }

    @Test
    fun aSungVoiceIsKeptFromAWhisperToAShout() {
        for (peakDb in listOf(-66f, -55f, -40f, -20f, -3f, 6f)) {
            for (pauses in listOf(true, false)) {
                val result = processor.process(TrackType.VOCALS_VERSE, sungTake(peakDb, pauses), context)
                assertNull("$peakDb dBFS, pauses=$pauses: ${result.problem}", result.problem)
            }
        }
    }

    @Test
    fun theKeptVocalLoopIsAtAClearLevelAndNotHiss() {
        val converted = processor.process(TrackType.VOCALS_VERSE, sungTake(-60f, pauses = true), context)
        val db = Rms.toDb(Rms.of(converted.loop))
        assertTrue("sung loop level $db dBFS", db > -35f)
    }
}
