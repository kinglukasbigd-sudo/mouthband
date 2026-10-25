package com.mouthband.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTakeTest {
    private val loop = 96_000
    private val columns = 480
    private val frameOfColumn = loop / columns

    @Test
    fun hitsKeepTheirExactFrame() {
        val take = LiveTake(loop, columns)
        take.addHit(12_345, 1)
        assertEquals(1, take.hitCount)
        assertEquals(12_345, take.hitFrame(0))
        assertEquals(1, take.hitClass(0))
    }

    @Test
    fun hitsOutsideTheLoopAreIgnored() {
        val take = LiveTake(loop, columns)
        take.addHit(-1, 0)
        take.addHit(loop.toLong(), 0)
        take.addHit(Long.MIN_VALUE, 0)
        assertEquals(0, take.hitCount)
    }

    @Test
    fun hitsStopAtTheLimitInsteadOfGrowing() {
        val take = LiveTake(loop, columns)
        repeat(LiveTake.MAX_HITS + 10) { take.addHit(it.toLong(), 0) }
        assertEquals(LiveTake.MAX_HITS, take.hitCount)
    }

    @Test
    fun pitchFillsEveryColumnBetweenTwoEstimates() {
        val take = LiveTake(loop, columns)
        take.addPitch(10L * frameOfColumn, 20L * frameOfColumn, 60.5f)
        for (c in 10..20) assertEquals(60.5f, take.pitch[c], 0f)
        assertTrue(take.pitch[9].isNaN())
        assertTrue(take.pitch[21].isNaN())
    }

    @Test
    fun silenceBreaksThePitchLine() {
        val take = LiveTake(loop, columns)
        take.addPitch(0, 30L * frameOfColumn, 62f)
        take.addPitch(10L * frameOfColumn, 20L * frameOfColumn, Float.NaN)
        assertEquals(62f, take.pitch[9], 0f)
        for (c in 10..20) assertTrue(take.pitch[c].isNaN())
        assertEquals(62f, take.pitch[21], 0f)
    }

    @Test
    fun pitchBeforeTheLoopStartsNothingAndAfterItEndsNothing() {
        val take = LiveTake(loop, columns)
        take.addPitch(-5_000, -1, 60f)
        take.addPitch(loop.toLong(), loop + 5_000L, 60f)
        assertTrue(take.pitch.all { it.isNaN() })
    }

    @Test
    fun waveformPeaksLandWhereTheyWereHeard() {
        val take = LiveTake(loop, columns)
        // The take is heard 1 000 frames after it was recorded (latency and loop start).
        val sink = LiveWaveformSink(take, { index -> index - 1_000 }, blockFrames = 100)
        val samples = FloatArray(10_000)
        // One loud block: record frames 5 000..5 099, centre 5 050 → loop frame 4 050.
        for (i in 5_000 until 5_100) samples[i] = if (i % 2 == 0) 0.2f else -0.2f
        sink.accept(samples, samples.size)
        val loud = take.column(4_050)
        assertEquals(0.2f, take.peaks[loud], 1e-6f)
        assertEquals(1, take.peaks.count { it > 0f })
    }

    @Test
    fun theWaveformScalesToTheLoudestColumnSoAWhisperFillsTheLane() {
        val take = LiveTake(loop, columns)
        take.addPeak(1_000, 0.01f)
        take.addPeak(50_000, 0.05f)
        // Loudest column 0.05 → scale 20: the whisper's peak fills the lane, the quieter one a fifth.
        assertEquals(20f, take.displayScale(), 1e-3f)
        assertEquals(1f, take.peaks[take.column(50_000)] * take.displayScale(), 1e-3f)
        assertEquals(0.2f, take.peaks[take.column(1_000)] * take.displayScale(), 1e-3f)
    }

    @Test
    fun aSilentRoomIsNotStretchedToFullHeight() {
        val take = LiveTake(loop, columns)
        take.addPeak(10_000, 0.0001f)
        assertEquals(1f / LiveTake.MIN_REFERENCE, take.displayScale(), 1e-3f)
        assertTrue(take.peaks[take.column(10_000)] * take.displayScale() < 0.2f)
    }

    @Test
    fun aLoudTakeKeepsItsRawPeaks() {
        val take = LiveTake(loop, columns)
        val sink = LiveWaveformSink(take, { index -> index }, blockFrames = 100)
        sink.accept(FloatArray(100) { 0.9f }, 100)
        assertEquals(0.9f, take.peaks[take.column(50)], 0f)
        assertEquals(1f / 0.9f, take.displayScale(), 1e-4f)
    }
}
