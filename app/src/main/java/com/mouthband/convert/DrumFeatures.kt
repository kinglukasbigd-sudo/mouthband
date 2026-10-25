package com.mouthband.convert

import com.mouthband.dsp.Mfcc
import com.mouthband.dsp.Rms
import com.mouthband.dsp.ZeroCrossing

/**
 * Beatbox hit features (spec §2.4 step 2) from an 80 ms window starting at the onset: mean
 * MFCC 1–13, spectral centroid, zero-crossing rate, low-band (< 150 Hz) energy ratio and peak
 * RMS (dBFS). Also reports how tonal the hit is around the metronome click frequencies, so
 * click bleed from the speaker can be rejected.
 */
class DrumFeatures(private val sampleRate: Int) {
    private val mfcc = Mfcc(sampleRate)
    private val coefficients = FloatArray(Mfcc.COEFFICIENTS)
    private val meanPower = FloatArray(Mfcc.FRAME / 2 + 1)

    /** Feature vector of the hit at [onset]. */
    fun extract(samples: FloatArray, onset: Int): Analysis {
        val window = (WINDOW_SECONDS * sampleRate).toInt()
        val values = FloatArray(SIZE)
        meanPower.fill(0f)
        var frames = 0
        var start = onset
        while (frames == 0 || start + Mfcc.FRAME <= onset + window) {
            mfcc.compute(samples, start, coefficients)
            for (k in coefficients.indices) values[MFCC_FIRST + k] += coefficients[k]
            for (k in meanPower.indices) meanPower[k] += mfcc.power[k]
            frames++
            start += SUB_HOP
        }
        for (k in 0 until Mfcc.COEFFICIENTS) values[MFCC_FIRST + k] /= frames
        System.arraycopy(meanPower, 0, mfcc.power, 0, meanPower.size)
        values[CENTROID] = mfcc.centroidHz()
        values[LOW_RATIO] = mfcc.energyBelow(LOW_BAND_HZ)
        values[ZCR] = ZeroCrossing.rate(samples, onset, window)
        values[PEAK_RMS] = peakRmsDb(samples, onset, window)
        val clickShare = maxOf(
            mfcc.energyNear(ACCENT_CLICK_HZ, CLICK_BAND_HZ),
            mfcc.energyNear(NORMAL_CLICK_HZ, CLICK_BAND_HZ),
        )
        return Analysis(values, clickShare)
    }

    private fun peakRmsDb(samples: FloatArray, onset: Int, window: Int): Float {
        var peak = 0f
        var at = onset
        while (at < onset + window) {
            peak = maxOf(peak, Rms.of(samples, at, RMS_SUBWINDOW))
            at += RMS_SUBWINDOW / 2
        }
        return Rms.toDb(peak)
    }

    /** [values] is the 17-dimensional feature vector; [clickShare] the energy share at 800/1000 Hz. */
    class Analysis(val values: FloatArray, val clickShare: Float) {
        val peakRmsDb: Float get() = values[PEAK_RMS]
        val centroidHz: Float get() = values[CENTROID]
        val lowRatio: Float get() = values[LOW_RATIO]
        val zeroCrossingRate: Float get() = values[ZCR]
    }

    companion object {
        const val SIZE = 17
        const val MFCC_FIRST = 0
        const val CENTROID = 13
        const val ZCR = 14
        const val LOW_RATIO = 15
        const val PEAK_RMS = 16
        const val WINDOW_SECONDS = 0.080
        private const val SUB_HOP = 256
        private const val RMS_SUBWINDOW = 256
        private const val LOW_BAND_HZ = 150f
        private const val ACCENT_CLICK_HZ = 1000f
        private const val NORMAL_CLICK_HZ = 800f
        private const val CLICK_BAND_HZ = 60f
    }
}
