package com.mouthband.convert

import com.mouthband.audio.AlignedTake
import com.mouthband.model.KeyMode
import com.mouthband.model.MusicalKey
import com.mouthband.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Whatever the microphone hands over, the converters answer with notes, hits or a reason. */
class RobustnessTest {
    private val rate = 48_000
    private val processor = TakeProcessor(rate, BeatboxConverter(rate), PitchConverter(rate, primary = { null }))
    private val key = MusicalKey(0, KeyMode.MAJOR)
    private val context = TakeProcessor.Context(120, key, templates = null, clicksAudible = true)
    private val types = TrackType.entries

    private fun check(name: String, take: AlignedTake) {
        for (type in types) {
            val result = processor.process(type, take, context)
            assertEquals("$name/$type keeps one loop", take.loopFrames, result.loop.size)
            assertTrue("$name/$type loop is finite", result.loop.all { it.isFinite() })
            val events = result.events
            if (events != null) {
                val notesOk = events.notes.all { it.startFrame >= 0 && it.endFrame > it.startFrame }
                assertTrue("$name/$type notes inside the loop", notesOk)
                val hitsOk = events.hits.all { it.frameInLoop in 0 until take.loopFrames }
                assertTrue("$name/$type hits inside the loop", hitsOk)
            }
        }
    }

    private fun loud(random: Random) = (random.nextGaussian().toFloat() * 20f).coerceIn(-1f, 1f)

    private fun take(size: Int, preroll: Int, loop: Int, fill: (Int) -> Float) =
        AlignedTake(FloatArray(size) { fill(it) }, preroll, loop, rate)

    @Test
    fun randomNoiseAtAnyLevelNeverCrashes() {
        val random = Random(1)
        for (amplitude in listOf(0f, 1e-7f, 1e-4f, 0.01f, 0.3f, 1f, 4f)) {
            check("noise $amplitude", take(rate * 5, 4_800, 4 * rate) { random.nextGaussian().toFloat() * amplitude })
        }
    }

    @Test
    fun nonFiniteSamplesAreTreatedAsSilenceNotPropagated() {
        val random = Random(2)
        val samples = FloatArray(rate * 5) { random.nextGaussian().toFloat() * 0.1f }
        samples[1_000] = Float.NaN
        samples[2_000] = Float.POSITIVE_INFINITY
        samples[3_000] = Float.NEGATIVE_INFINITY
        check("non-finite", AlignedTake(samples, 4_800, 4 * rate, rate))
    }

    @Test
    fun aConstantOffsetIsNotMistakenForSound() {
        val take = take(rate * 5, 4_800, 4 * rate) { 0.4f }
        for (type in types) {
            val result = processor.process(type, take, context)
            assertTrue("$type: DC only has no events: ${result.events}", result.events == null)
            assertTrue("$type: reports why", result.problem != null)
        }
    }

    @Test
    fun aFullScaleSquareWaveAndHardClippedNoiseComeBackClean() {
        check("square", take(rate * 5, 4_800, 4 * rate) { if ((it / 100) % 2 == 0) 1f else -1f })
        val random = Random(3)
        check("clipped noise", take(rate * 5, 4_800, 4 * rate) { loud(random) })
    }

    @Test
    fun veryShortTakesAndEdgeLayoutsAreHandled() {
        check("empty loop", take(10, 0, 0) { 0.1f })
        check("one sample", take(1, 0, 1) { 0.5f })
        check("no preroll or tail", take(2_048, 0, 2_048) { if (it % 2 == 0) 0.3f else -0.3f })
        check("shorter than one frame", take(100, 10, 50) { 0.2f })
        check("preroll only", take(4_800, 4_800, 0) { 0.2f })
    }

    @Test
    fun otherSampleRatesWork() {
        for (other in listOf(16_000, 22_050, 44_100, 96_000)) {
            val random = Random(other.toLong())
            val p = TakeProcessor(other, BeatboxConverter(other), PitchConverter(other, primary = { null }))
            val samples = FloatArray(other * 5) { random.nextGaussian().toFloat() * 0.05f }
            val take = AlignedTake(samples, other / 10, 4 * other, other)
            val otherContext = TakeProcessor.Context(120, key, templates = null, clicksAudible = false)
            for (type in types) {
                val result = p.process(type, take, otherContext)
                assertEquals("$other Hz $type", take.loopFrames, result.loop.size)
            }
        }
    }
}
