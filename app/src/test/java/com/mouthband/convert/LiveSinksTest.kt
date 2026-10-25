package com.mouthband.convert

import com.mouthband.audio.LiveTake
import com.mouthband.audio.LoopClock
import com.mouthband.dsp.Rms
import com.mouthband.model.DrumClass
import com.mouthband.model.TrackType
import com.mouthband.ui.coach.liveSinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * What the Recording screen draws while a take is performed: the real live sinks fed with
 * realistic hum and beatbox audio at every recording level, block by block as the capture
 * consumer delivers it. Every sound must appear where it was made, and silence or noise must not.
 */
class LiveSinksTest {
    private val rate = 48_000
    private val loop = 4 * rate
    private val block = 480

    private fun midiToHz(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    private fun feed(type: TrackType, samples: FloatArray): LiveTake {
        val take = LiveTake(loop)
        val sinks = liveSinks(type, LoopClock { it }, take, rate) { DrumClassifier.byRules(it) }
        var at = 0
        while (at < samples.size) {
            val n = minOf(block, samples.size - at)
            val chunk = samples.copyOfRange(at, at + n)
            sinks.forEach { it.accept(chunk, n) }
            at += n
        }
        return take
    }

    /** Voice-like hum: harmonics and vibrato, each note `(midi, start s, end s)`. */
    private fun hum(notes: List<Triple<Int, Double, Double>>, peakDb: Float, noiseDb: Float): FloatArray {
        val samples = FloatArray(loop)
        for ((midi, start, end) in notes) {
            var phase = 0.0
            val from = (start * rate).toInt()
            val n = ((end - start) * rate).toInt()
            for (i in 0 until n) {
                val t = i.toDouble() / rate
                phase += 2 * PI * midiToHz(midi) * (1 + 0.004 * sin(2 * PI * 5.5 * t)) / rate
                val env = minOf(1.0, i / (0.015 * rate), (n - i) / (0.015 * rate))
                samples[from + i] += (env * (1..8).sumOf { sin(it * phase) / it }).toFloat()
            }
        }
        val gain = Rms.fromDb(peakDb) / Rms.peak(samples)
        val random = Random(4)
        val floor = Rms.fromDb(noiseDb)
        return FloatArray(loop) { (samples[it] * gain + random.nextGaussian() * floor).toFloat() }
    }

    private val notes = listOf(
        Triple(60, 0.2, 1.0),
        Triple(64, 1.3, 2.1),
        Triple(67, 2.4, 3.2),
    )

    private fun columnOf(seconds: Double, take: LiveTake) = take.column((seconds * rate).toLong())

    @Test
    fun theHummedLineAppearsOnItsNotesAtEveryLevelAndPausesStayEmpty() {
        for (peakDb in listOf(-66f, -50f, -30f, -10f, 0f)) {
            val take = feed(TrackType.MELODY, hum(notes, peakDb, noiseDb = peakDb - 45f))
            for ((midi, start, end) in notes) {
                val inside = (columnOf(start + 0.2, take)..columnOf(end - 0.1, take)).map { take.pitch[it] }
                val voiced = inside.filter { !it.isNaN() }
                val drawn = "${voiced.size}/${inside.size}"
                assertTrue("$peakDb dBFS, MIDI $midi: line drawn ($drawn)", voiced.size > inside.size * 0.8)
                voiced.forEach { assertEquals("$peakDb dBFS, MIDI $midi", midi.toFloat(), it, 0.7f) }
            }
            // A pause between notes: no line (a short margin for the window length).
            val pause = (columnOf(1.12, take)..columnOf(1.22, take)).map { take.pitch[it] }
            assertTrue("$peakDb dBFS: the pause stays empty", pause.count { it.isNaN() } >= pause.size / 2)
            val tail = (columnOf(3.5, take)..columnOf(3.9, take)).map { take.pitch[it] }
            assertTrue("$peakDb dBFS: nothing after the last note", tail.all { it.isNaN() })
        }
    }

    @Test
    fun noiseAloneDrawsNoPitchLineAtAnyLevel() {
        for (noiseDb in listOf(-76f, -60f, -45f, -30f, -15f)) {
            val random = Random(8)
            val amp = Rms.fromDb(noiseDb)
            val take = feed(TrackType.BASS, FloatArray(loop) { (random.nextGaussian() * amp).toFloat() })
            val drawn = take.pitch.count { !it.isNaN() }
            assertTrue("noise at $noiseDb dB drew $drawn of ${take.columns} columns", drawn <= take.columns / 50)
        }
    }

    @Test
    fun theWaveformFillsTheLaneForAWhisperAndAShout() {
        for (peakDb in listOf(-60f, -40f, -10f, 0f)) {
            val take = feed(TrackType.MELODY, hum(notes, peakDb, noiseDb = peakDb - 45f))
            val loudest = take.peaks[columnOf(0.6, take)] * take.displayScale()
            assertTrue("$peakDb dBFS: loudest note column fills the lane ($loudest)", loudest > 0.5f)
            val quiet = take.peaks[columnOf(3.7, take)] * take.displayScale()
            assertTrue("$peakDb dBFS: silence stays low ($quiet)", quiet < 0.25f)
        }
    }

    @Test
    fun aWhisperBelowTheMicrophoneNoiseFloorStillShowsASmallWaveform() {
        val take = feed(TrackType.MELODY, hum(notes, -66f, noiseDb = -111f))
        val shown = take.peaks[columnOf(0.6, take)] * take.displayScale()
        assertTrue("whisper at -66 dBFS shows at ${(shown * 100).toInt()} % of the lane", shown in 0.3f..1f)
    }

    // -- beatbox ---------------------------------------------------------------------------

    private val step = (60.0 / 110 / 2 * rate).toInt()

    private val pattern = listOf(
        0 to DrumClass.KICK, 2 to DrumClass.SNARE, 4 to DrumClass.KICK, 6 to DrumClass.SNARE,
        1 to DrumClass.HIHAT, 3 to DrumClass.HIHAT, 5 to DrumClass.HIHAT, 7 to DrumClass.HIHAT,
    ).sortedBy { it.first }

    private fun beatbox(peakDb: Float, noiseDb: Float): FloatArray {
        val samples = FloatArray(loop)
        val random = Random(5)
        val first = (0.3 * rate).toInt()
        for ((slot, cls) in pattern) {
            val at = first + slot * step
            var lowPassed = 0f
            var previous = 0f
            var highPassed = 0f
            for (i in 0 until (0.08 * rate).toInt()) {
                val t = i.toDouble() / rate
                val v = when (cls) {
                    DrumClass.KICK -> sin(2 * PI * (60 + 90 * exp(-t * 40)) * t) * exp(-t * 25)
                    DrumClass.SNARE -> {
                        // "pff": band-limited breath noise plus a little body.
                        lowPassed += 0.25f * (random.nextGaussian().toFloat() - lowPassed)
                        (lowPassed * 1.5 + sin(2 * PI * 200 * t) * 0.3) * exp(-t * 35)
                    }
                    DrumClass.HIHAT -> {
                        val noise = random.nextGaussian().toFloat()
                        highPassed = 0.9f * (highPassed + noise - previous)
                        previous = noise
                        highPassed * exp(-t * 60)
                    }
                }
                samples[at + i] += v.toFloat()
            }
        }
        val gain = Rms.fromDb(peakDb) / Rms.peak(samples)
        val floor = Rms.fromDb(noiseDb)
        val noise = Random(6)
        return FloatArray(loop) { (samples[it] * gain + noise.nextGaussian() * floor).toFloat() }
    }

    @Test
    fun everyBeatboxHitAppearsWhereItWasMadeAtEveryLevel() {
        val first = (0.3 * rate).toInt()
        // -60 dBFS is the quietest level a phone microphone delivers above its own noise; the softest
        // hi-hat of a pattern sits 10 dB under the loudest hit.
        for (peakDb in listOf(-60f, -50f, -30f, -10f, 0f)) {
            val take = feed(TrackType.BEAT, beatbox(peakDb, noiseDb = peakDb - 45f))
            val frames = (0 until take.hitCount).map { take.hitFrame(it) }
            assertEquals("$peakDb dBFS: hit count ${take.hitCount}", pattern.size, take.hitCount)
            pattern.forEachIndexed { i, (slot, cls) ->
                val expected = first + slot * step
                val found = frames.indexOfFirst { abs(it - expected) < rate / 25 }
                assertTrue("$peakDb dBFS: hit $i near $expected, got $frames", found >= 0)
                if (cls != DrumClass.HIHAT) {
                    assertEquals("$peakDb dBFS: class of hit $i", cls.ordinal, take.hitClass(found))
                }
            }
        }
    }

    @Test
    fun noiseAloneDrawsNoDrumHitsAtAnyLevel() {
        for (noiseDb in listOf(-76f, -60f, -45f, -30f)) {
            val random = Random(12)
            val amp = Rms.fromDb(noiseDb)
            val take = feed(TrackType.BEAT, FloatArray(loop) { (random.nextGaussian() * amp).toFloat() })
            assertEquals("noise at $noiseDb dB: no hits", 0, take.hitCount)
        }
    }
}
