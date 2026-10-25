package com.mouthband.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class DspTest {
    @Test
    fun fftMatchesNaiveDft() {
        val n = 64
        val random = Random(3)
        val x = FloatArray(n) { random.nextFloat() * 2 - 1 }
        val fft = Fft(n)
        val re = FloatArray(fft.binCount)
        val im = FloatArray(fft.binCount)
        fft.forward(x, re, im)
        for (k in 0..n / 2) {
            var sr = 0.0
            var si = 0.0
            for (t in 0 until n) {
                sr += x[t] * cos(2 * PI * k * t / n)
                si -= x[t] * sin(2 * PI * k * t / n)
            }
            assertEquals("re[$k]", sr, re[k].toDouble(), 1e-3)
            assertEquals("im[$k]", si, im[k].toDouble(), 1e-3)
        }
    }

    @Test
    fun fftFindsSinePeak() {
        val fft = Fft(1024)
        val x = FloatArray(1024) { sin(2 * PI * 64 * it / 1024).toFloat() }
        val mags = FloatArray(fft.binCount)
        fft.magnitudes(x, FloatArray(fft.binCount), FloatArray(fft.binCount), mags)
        assertEquals(64, mags.indices.maxBy { mags[it] })
    }

    @Test
    fun lowPassPassesLowAndCutsHigh() {
        val rate = 48_000
        fun gainAt(hz: Double): Double {
            val f = Biquad().apply { coefficients = Biquad.lowPass(rate, 2000f) }
            var peak = 0.0
            for (i in 0 until rate / 10) {
                val y = f.process(sin(2 * PI * hz * i / rate).toFloat())
                if (i > rate / 20) peak = maxOf(peak, abs(y).toDouble())
            }
            return peak
        }
        assertEquals(1.0, gainAt(100.0), 0.02)
        assertTrue(gainAt(16_000.0) < 0.05)
    }

    @Test
    fun highPassCutsDc() {
        val f = Biquad().apply { coefficients = Biquad.highPass(48_000, 90f) }
        var y = 1f
        repeat(48_000) { y = f.process(1f) }
        assertTrue(abs(y) < 1e-3)
    }

    @Test
    fun peakBoostsCenter() {
        val rate = 48_000
        val f = Biquad().apply { coefficients = Biquad.peak(rate, 4000f, 3f) }
        var peak = 0f
        for (i in 0 until rate / 5) {
            val y = f.process(sin(2 * PI * 4000 * i / rate).toFloat())
            if (i > rate / 10) peak = maxOf(peak, abs(y))
        }
        assertEquals(Rms.fromDb(3f), peak, 0.02f)
    }

    @Test
    fun limiterNeverExceedsCeiling() {
        val rate = 48_000
        val limiter = Limiter(rate)
        val random = Random(1)
        val ceiling = Rms.fromDb(-1f)
        val block = FloatArray(512)
        repeat(200) {
            for (i in 0 until 256) {
                val v = (random.nextGaussian() * 0.8).toFloat()
                block[2 * i] = v
                block[2 * i + 1] = -v * 1.3f
            }
            limiter.processStereo(block, 256)
            block.forEach { assertTrue("sample $it over ceiling", abs(it) <= ceiling + 1e-4f) }
        }
    }

    @Test
    fun limiterLeavesQuietSignalUntouchedButDelayed() {
        val limiter = Limiter(48_000)
        val n = 1000
        val block = FloatArray(n * 2) { if (it / 2 == 10) 0.3f else 0f }
        limiter.processStereo(block, n)
        val outIndex = (0 until n).first { block[2 * it] != 0f }
        assertEquals(10 + limiter.latencyFrames + 1, outIndex)
        assertEquals(0.3f, block[2 * outIndex], 1e-4f)
    }

    @Test
    fun compressorAppliesRatioAboveThreshold() {
        val rate = 48_000
        val c = Compressor(rate, thresholdDb = -18f, ratio = 3f, attackMs = 10f, releaseMs = 120f)
        var out = 0f
        val amplitude = Rms.fromDb(-6f) * sqrt(2f) // sine with -6 dBFS RMS
        for (i in 0 until rate) {
            val x = (amplitude * sin(2 * PI * 440 * i / rate)).toFloat()
            out = c.process(x)
        }
        // 12 dB over threshold at 3:1 → 8 dB of gain reduction.
        assertEquals(8f, c.reductionDb, 0.5f)
        assertTrue(abs(out) <= amplitude)
    }

    @Test
    fun reverbDecaysAndStaysFinite() {
        val reverb = Reverb(48_000)
        val input = FloatArray(48_000)
        input[0] = 1f
        val out = FloatArray(96_000)
        reverb.processAdd(input, out, 48_000, wet = 1f)
        assertTrue(out.all { it.isFinite() })
        val early = Rms.of(out, 2000, 20_000)
        val late = Rms.of(out, 70_000, 20_000)
        assertTrue(early > 0f && late < early)
        val tail = FloatArray(96_000)
        reverb.processAdd(FloatArray(48_000), tail, 48_000, wet = 1f)
        assertTrue(Rms.of(tail, 80_000, 16_000) < early)
    }

    @Test
    fun resamplerKeepsFrequency() {
        val from = 48_000
        val to = 16_000
        val x = FloatArray(from) { sin(2 * PI * 440 * it / from).toFloat() }
        val y = Resampler.resample(x, from, to)
        assertEquals(to, y.size)
        val crossings = ZeroCrossing.rate(y, 1000, 8000) * 7999
        // 440 Hz → 880 crossings per second → 440 per 8000 samples at 16 kHz.
        assertEquals(440f, crossings, 6f)
        assertEquals(1f / sqrt(2f), Rms.of(y, 1000, 8000), 0.02f)
    }

    @Test
    fun mfccDistinguishesLowFromHigh() {
        val rate = 48_000
        val mfcc = Mfcc(rate)
        val low = FloatArray(1024) { sin(2 * PI * 100 * it / rate).toFloat() }
        val high = FloatArray(1024) { sin(2 * PI * 8000 * it / rate).toFloat() }
        val a = FloatArray(13)
        val b = FloatArray(13)
        mfcc.compute(low, 0, a)
        val lowCentroid = mfcc.centroidHz()
        val lowRatio = mfcc.energyBelow(150f)
        mfcc.compute(high, 0, b)
        assertTrue(lowCentroid < 500f)
        assertTrue(mfcc.centroidHz() > 6000f)
        assertTrue(lowRatio > 0.5f)
        assertTrue(a[1] > b[1])
    }

    @Test
    fun hannIsPeriodic() {
        val w = Window.hann(8)
        assertEquals(0f, w[0], 1e-6f)
        assertEquals(1f, w[4], 1e-6f)
        assertEquals(w[1], w[7], 1e-6f)
    }
}
