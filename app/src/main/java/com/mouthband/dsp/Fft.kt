package com.mouthband.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Radix-2 real FFT of size [size] (power of two). The real input is packed into a half-size
 * complex FFT and split afterwards. All buffers are preallocated; [forward] does not allocate.
 * Not thread-safe: use one instance per thread.
 */
class Fft(val size: Int) {
    init {
        require(size >= MIN_SIZE && size and (size - 1) == 0) { "FFT size must be a power of two ≥ $MIN_SIZE" }
    }

    private val half = size / 2
    private val bins = half + 1
    private val bitReverse = IntArray(half)
    private val cosTable = FloatArray(half / 2)
    private val sinTable = FloatArray(half / 2)
    private val splitCos = FloatArray(half)
    private val splitSin = FloatArray(half)
    private val workRe = FloatArray(half)
    private val workIm = FloatArray(half)

    init {
        var bits = 0
        while (1 shl bits < half) bits++
        for (i in 0 until half) {
            var r = 0
            for (b in 0 until bits) if (i and (1 shl b) != 0) r = r or (1 shl (bits - 1 - b))
            bitReverse[i] = r
        }
        for (i in 0 until half / 2) {
            cosTable[i] = cos(2.0 * PI * i / half).toFloat()
            sinTable[i] = -sin(2.0 * PI * i / half).toFloat()
        }
        for (k in 0 until half) {
            splitCos[k] = cos(2.0 * PI * k / size).toFloat()
            splitSin[k] = -sin(2.0 * PI * k / size).toFloat()
        }
    }

    /** Number of output bins: size / 2 + 1 (DC … Nyquist). */
    val binCount: Int get() = bins

    /** Forward transform of [input] (first [size] samples) into [re]/[im] (each ≥ [binCount]). */
    fun forward(input: FloatArray, re: FloatArray, im: FloatArray) {
        for (i in 0 until half) {
            val j = bitReverse[i]
            workRe[j] = input[2 * i]
            workIm[j] = input[2 * i + 1]
        }
        complexInPlace()
        // Split the half-size spectrum Z into the real-input spectrum X.
        re[0] = workRe[0] + workIm[0]
        im[0] = 0f
        re[half] = workRe[0] - workIm[0]
        im[half] = 0f
        for (k in 1 until half) {
            val zr = workRe[k]
            val zi = workIm[k]
            val cr = workRe[half - k]
            val ci = -workIm[half - k]
            val er = 0.5f * (zr + cr)
            val ei = 0.5f * (zi + ci)
            val or = 0.5f * (zi - ci)
            val oi = -(0.5f * (zr - cr))
            val wr = splitCos[k]
            val wi = splitSin[k]
            re[k] = er + (or * wr - oi * wi)
            im[k] = ei + (or * wi + oi * wr)
        }
    }

    /** Magnitude spectrum of [input] into [magnitudes] (size ≥ [binCount]). */
    fun magnitudes(input: FloatArray, re: FloatArray, im: FloatArray, magnitudes: FloatArray) {
        forward(input, re, im)
        for (k in 0 until bins) magnitudes[k] = sqrt(re[k] * re[k] + im[k] * im[k])
    }

    private fun complexInPlace() {
        var length = 2
        while (length <= half) {
            val step = half / length
            val halfLength = length / 2
            var start = 0
            while (start < half) {
                for (j in 0 until halfLength) {
                    val wr = cosTable[j * step]
                    val wi = sinTable[j * step]
                    val a = start + j
                    val b = a + halfLength
                    val tr = workRe[b] * wr - workIm[b] * wi
                    val ti = workRe[b] * wi + workIm[b] * wr
                    workRe[b] = workRe[a] - tr
                    workIm[b] = workIm[a] - ti
                    workRe[a] += tr
                    workIm[a] += ti
                }
                start += length
            }
            length *= 2
        }
    }

    private companion object {
        const val MIN_SIZE = 4
    }
}
