package com.mouthband.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class EngineMathTest {
    @Test
    fun loopFramesUseTheSpecFormula() {
        // 2 bars × 4 × 60 / 110 × 48000 = 209454.54… → 209455
        assertEquals(209_455, LoopMath.loopFrames(2, 110, 48_000))
        assertEquals(192_000, LoopMath.loopFrames(1, 60, 48_000))
        assertEquals(LoopMath.loopFrames(4, 85, 44_100), (4 * 4 * 60.0 / 85 * 44_100).let { Math.round(it).toInt() })
    }

    @Test
    fun ringBufferPreservesOrderAcrossWrap() {
        val ring = FloatRingBuffer(8)
        val out = FloatArray(8)
        var next = 0f
        var expected = 0f
        repeat(50) {
            val chunk = FloatArray(5) { next++ }
            assertEquals(5, ring.write(chunk, 0, 5))
            val n = ring.read(out, 0, 5)
            for (i in 0 until n) assertEquals(expected++, out[i])
        }
        assertEquals(0, ring.availableToRead())
    }

    @Test
    fun ringBufferDropsWhenFull() {
        val ring = FloatRingBuffer(4)
        assertEquals(4, ring.write(FloatArray(6) { it.toFloat() }, 0, 6))
        assertEquals(0, ring.availableToWrite())
        val out = FloatArray(4)
        ring.read(out, 0, 4)
        assertArrayEquals(floatArrayOf(0f, 1f, 2f, 3f), out, 0f)
    }

    @Test
    fun metronomeClicksLandOnBeats() {
        val rate = 48_000
        val metronome = Metronome(rate)
        val beat = LoopMath.framesPerBeat(120, rate) // 24000
        metronome.plan = Metronome.Plan(
            startFrame = 1000,
            framesPerBeat = beat,
            countInEndFrame = 1000 + 4 * 24_000L,
            endFrame = 1000 + 8 * 24_000L,
            clickAfterCountIn = true,
        )
        val block = 256
        val total = 9 * 24_000 + 2000
        val out = FloatArray(total * 2)
        val buf = FloatArray(block * 2)
        var frame = 0L
        while (frame + block <= total) {
            java.util.Arrays.fill(buf, 0f)
            metronome.render(buf, block, frame)
            System.arraycopy(buf, 0, out, (frame * 2).toInt(), block * 2)
            frame += block
        }
        // A click starts with sin(0) = 0, so its first non-zero sample is one frame after the beat.
        val onsets = (2 until total)
            .filter { abs(out[2 * it]) > 0f && out[2 * (it - 1)] == 0f && out[2 * (it - 2)] == 0f }
            .map { it - 1 }
        val expected = (0 until 8).map { 1000 + it * 24_000 }
        assertEquals(expected, onsets)
        assertTrue("no click after endFrame", onsets.none { it >= 1000 + 8 * 24_000 })
    }

    @Test
    fun metronomeSilentAfterCountInWhenClickOff() {
        val rate = 48_000
        val metronome = Metronome(rate)
        metronome.plan = Metronome.Plan(
            startFrame = 0,
            framesPerBeat = 24_000.0,
            countInEndFrame = 48_000,
            endFrame = 96_000,
            clickAfterCountIn = false,
        )
        val buf = FloatArray(512)
        metronome.render(buf, 256, 48_000)
        assertTrue(buf.all { it == 0f })
        val first = FloatArray(512)
        metronome.render(first, 256, 0)
        assertTrue(first.any { it != 0f })
    }

    @Test
    fun loopPlayerWrapsAndStaysSilentBeforeStart() {
        val player = LoopPlayer(1)
        player.loopFrames = 4
        player.loopStartFrame = 2
        player.lanes[0].buffer = floatArrayOf(1f, 1f, 2f, 2f, 3f, 3f, 4f, 4f)
        val out = FloatArray(16)
        player.render(out, 8, 0)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 0f, 1f, 1f, 2f, 2f, 3f, 3f, 4f, 4f, 1f, 1f, 2f, 2f), out, 1e-6f)
    }

    @Test
    fun equalPowerPanKeepsCenterAtUnity() {
        val lane = LoopPlayer.Lane()
        lane.setGainPan(0f, 0f)
        assertEquals(1f, lane.gainLeft, 1e-5f)
        assertEquals(1f, lane.gainRight, 1e-5f)
        lane.setGainPan(-6f, 1f)
        assertEquals(0f, lane.gainLeft, 1e-5f)
        assertTrue(lane.gainRight > 0.6f)
    }

    @Test
    fun takeFileRoundTrips() {
        val file = File.createTempFile("take", ".f32")
        val samples = FloatArray(10_000) { (it % 100) / 100f - 0.5f }
        TakeFile.write(file, 48_000, samples)
        val back = TakeFile.read(file)
        assertEquals(48_000, back.sampleRate)
        assertArrayEquals(samples, back.samples, 0f)
        assertTrue(!File(file.parentFile, "${file.name}.tmp").exists())
        file.delete()
    }

    @Test
    fun levelVerdicts() {
        assertEquals(MicVerdict.TOO_QUIET, LevelMath.verdict(holdDb = -50f, peakDb = -40f))
        assertEquals(MicVerdict.GOOD, LevelMath.verdict(holdDb = -20f, peakDb = -10f))
        assertEquals(MicVerdict.TOO_LOUD, LevelMath.verdict(holdDb = -20f, peakDb = 0f))
        assertEquals(0f, LevelMath.meterFraction(-70f))
        assertEquals(1f, LevelMath.meterFraction(0f))
        assertEquals(-6.02f, LevelMath.amplitudeToDb(0.5f), 0.01f)
    }
}
