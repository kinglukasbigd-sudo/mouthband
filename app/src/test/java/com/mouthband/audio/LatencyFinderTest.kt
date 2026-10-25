package com.mouthband.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Random
import kotlin.math.abs

class LatencyFinderTest {
    private val rate = 48_000

    /** Simulates the phone: bursts come back [delayMs] later, quieter, filtered and noisy. */
    private fun simulate(
        delayMs: Double,
        gain: Float,
        noise: Float,
        startFrame: Long,
    ): Triple<Array<FloatArray>, LongArray, FloatArray> {
        val bursts = Array(NoiseBurst.COUNT) { NoiseBurst.create(it, rate) }
        val interval = LoopMath.msToFrames(NoiseBurst.INTERVAL_MS, rate)
        val emits = LongArray(NoiseBurst.COUNT) { startFrame + rate / 2 + it.toLong() * interval }
        val delay = LoopMath.msToFrames(delayMs, rate)
        val recording = FloatArray(rate * 5)
        val random = Random(7)
        for (i in recording.indices) recording[i] = (random.nextGaussian() * noise).toFloat()
        bursts.forEachIndexed { k, burst ->
            val at = (emits[k] - startFrame + delay).toInt()
            var lowPassed = 0f
            for (n in burst.indices) {
                // A speaker/mic path is band-limited: a one-pole low-pass plus a small echo.
                lowPassed += 0.5f * (burst[n] - lowPassed)
                recording[at + n] += gain * lowPassed
                if (at + n + 300 < recording.size) recording[at + n + 300] += 0.2f * gain * lowPassed
            }
        }
        return Triple(bursts, emits, recording)
    }

    @Test
    fun findsKnownDelayOf123ms() {
        val (bursts, emits, rec) = simulate(delayMs = 123.0, gain = 0.3f, noise = 0.01f, startFrame = 1_000)
        val result = LatencyFinder.find(
            BurstSource.Schedule(bursts, emits),
            Recording(rec),
            recordingStartFrame = 1_000,
            sampleRate = rate,
        )
        val latency = requireNotNull(result.latencyFrames)
        val ms = LoopMath.framesToMs(latency.toLong(), rate)
        assertEquals(123.0, ms, 1.0)
        assertEquals(8, result.accepted)
    }

    @Test
    fun findsNegativeOffsetWithinWindow() {
        val (bursts, emits, rec) = simulate(delayMs = 40.0, gain = 0.5f, noise = 0.005f, startFrame = 0)
        // Pretend the recording started 90 ms earlier than it did: the lag comes out at 40 − 90 = −50 ms.
        val shifted = LoopMath.msToFrames(90.0, rate).toLong()
        val result = LatencyFinder.find(
            BurstSource.Schedule(bursts, emits),
            Recording(rec),
            recordingStartFrame = -shifted,
            sampleRate = rate,
        )
        val latency = requireNotNull(result.latencyFrames)
        assertEquals(-50.0, LoopMath.framesToMs(latency.toLong(), rate), 1.0)
    }

    @Test
    fun rejectsWhenTooQuiet() {
        val (bursts, emits, rec) = simulate(delayMs = 123.0, gain = 0.0005f, noise = 0.05f, startFrame = 0)
        val result = LatencyFinder.find(
            BurstSource.Schedule(bursts, emits),
            Recording(rec),
            recordingStartFrame = 0,
            sampleRate = rate,
        )
        assertNull(result.latencyFrames)
    }

    @Test
    fun timestampMappingRecoversStartFrame() {
        val playNanos = 2_000_000_000L
        val playFrame = 96_000L // presented at 2.0 s
        val recFrame = 48_000L // record frame 48000 captured at 2.1 s → frame 0 at 1.1 s
        val recNanos = 2_100_000_000L
        val start = Alignment.startFrameFromTimestamps(playFrame, playNanos, recFrame, recNanos, rate)
        // At 1.1 s the transport presented 96000 - 0.9 s × 48000 = 52800.
        assertEquals(52_800L, start)
    }

    @Test
    fun extractMapsRecordedFramesOntoTransport() {
        val rec = FloatArray(100) { it.toFloat() }
        // Record frame i ↔ transport 1000 + i − 10.
        val out = Alignment.extract(Recording(rec), RecordingMap(startFrame = 1000, latencyFrames = 10), 1000, 5)
        assertEquals(listOf(10f, 11f, 12f, 13f, 14f), out.toList())
        val before = Alignment.extract(Recording(rec), RecordingMap(startFrame = 1000, latencyFrames = 0), 998, 3)
        assertEquals(listOf(0f, 0f, 0f), before.toList().map { abs(it) })
    }
}
