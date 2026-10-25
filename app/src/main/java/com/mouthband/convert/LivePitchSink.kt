package com.mouthband.convert

import com.mouthband.audio.CaptureSink
import com.mouthband.dsp.Resampler
import kotlin.math.abs

/**
 * Live pitch for the Recording screen's note lane: every ~32 ms the most recent window is
 * resampled to 16 kHz and run through YIN. [onPitch] receives the record index of the window's
 * centre and fractional MIDI, or NaN when nothing voiced was heard. Runs on the capture consumer
 * coroutine, never the record thread.
 *
 * A single window can lock on a harmonic or on music playing nearby (the phone's own backing
 * track), which draws a spike in the line. Estimates are therefore passed through [PitchSmoother],
 * a centred median over [PitchSmoother.SPAN] estimates: a step to a new note stays exact, a spike of
 * up to two estimates disappears. The line runs two hops (64 ms) behind the sound it draws.
 */
class LivePitchSink(
    private val sampleRate: Int,
    private val onPitch: PitchListener,
) : CaptureSink {
    private val yin = YinPitchTracker()
    private val smoother = PitchSmoother(onPitch)
    private val windowFrames = YinPitchTracker.WINDOW * sampleRate / PitchTracker.SAMPLE_RATE
    private val ring = FloatArray(windowFrames)
    private val linear = FloatArray(windowFrames)
    private val hop = (HOP_SECONDS * sampleRate).toInt()
    private var write = 0
    private var filled = 0
    private var sinceLast = 0
    private var total = 0L

    override fun accept(samples: FloatArray, count: Int) {
        for (i in 0 until count) {
            ring[write] = samples[i]
            write = (write + 1) % ring.size
            total++
            if (filled < ring.size) filled++
            sinceLast++
            if (sinceLast >= hop && filled == ring.size) {
                sinceLast = 0
                analyse()
            }
        }
    }

    private fun analyse() {
        // Oldest sample first.
        System.arraycopy(ring, write, linear, 0, ring.size - write)
        System.arraycopy(ring, 0, linear, ring.size - write, write)
        val window = Resampler.resample(linear, sampleRate, PitchTracker.SAMPLE_RATE)
        val padded = if (window.size >= YinPitchTracker.WINDOW) window else window.copyOf(YinPitchTracker.WINDOW)
        val peak = padded.maxOf { abs(it) }
        val estimate = if (peak < LIVE_SILENCE_PEAK) null else yin.estimate(padded)
        val voiced = estimate?.takeIf { it.second >= yin.voicedThreshold }
        val midi = if (voiced != null) MusicTheory.hzToMidi(voiced.first) else Float.NaN
        smoother.push(total - ring.size / 2, midi)
    }

    private companion object {
        const val HOP_SECONDS = 0.032
        const val LIVE_SILENCE_PEAK = 0.0003f
    }
}

/** Receives each live pitch estimate; a fun interface so primitives are passed without boxing. */
fun interface PitchListener {
    fun onPitch(centreIndex: Long, midi: Float)
}

/**
 * Centred median over the last [SPAN] pitch estimates, emitted for the middle one. Unvoiced
 * (NaN) estimates stay unvoiced, so real pauses are not filled; a voiced estimate takes the median
 * of the voiced estimates around it when at least [MIN_VOICED] of the [SPAN] are voiced, and is left
 * alone otherwise (an isolated blip is shown as heard).
 */
class PitchSmoother(private val onPitch: PitchListener) {
    private val centres = LongArray(SPAN)
    private val values = FloatArray(SPAN)
    private val scratch = FloatArray(SPAN)
    private var count = 0

    fun push(centreIndex: Long, midi: Float) {
        if (count == SPAN) {
            System.arraycopy(centres, 1, centres, 0, SPAN - 1)
            System.arraycopy(values, 1, values, 0, SPAN - 1)
            count--
        }
        centres[count] = centreIndex
        values[count] = midi
        count++
        if (count < SPAN) return
        val middle = SPAN / 2
        onPitch.onPitch(centres[middle], median(values[middle]))
    }

    private fun median(centre: Float): Float {
        if (centre.isNaN()) return centre
        var voiced = 0
        for (v in values) if (!v.isNaN()) scratch[voiced++] = v
        if (voiced < MIN_VOICED) return centre
        java.util.Arrays.sort(scratch, 0, voiced)
        return if (voiced % 2 == 1) {
            scratch[voiced / 2]
        } else {
            // An even number of voiced estimates: the one of the two middle values nearer the centre.
            val low = scratch[voiced / 2 - 1]
            val high = scratch[voiced / 2]
            if (abs(centre - low) <= abs(centre - high)) low else high
        }
    }

    companion object {
        const val SPAN = 5
        const val MIN_VOICED = 3
    }
}
