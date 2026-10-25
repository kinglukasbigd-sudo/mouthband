package com.mouthband.dsp

/**
 * Freeverb-style reverb (spec §2.3): 8 parallel damped comb filters and 4 series all-pass
 * filters per channel, tunings scaled from 44.1 kHz, right channel offset by a stereo spread.
 * Mono in, stereo wet out. Allocation-free after construction.
 */
class Reverb(sampleRate: Int, roomSize: Float = DEFAULT_ROOM, damping: Float = DEFAULT_DAMPING) {
    private val scale = sampleRate / REFERENCE_RATE
    private val combsLeft = COMB_TUNINGS.map { Comb(scaled(it)) }.toTypedArray()
    private val combsRight = COMB_TUNINGS.map { Comb(scaled(it + STEREO_SPREAD)) }.toTypedArray()
    private val allPassLeft = ALLPASS_TUNINGS.map { AllPass(scaled(it)) }.toTypedArray()
    private val allPassRight = ALLPASS_TUNINGS.map { AllPass(scaled(it + STEREO_SPREAD)) }.toTypedArray()

    init {
        setRoom(roomSize, damping)
    }

    private fun scaled(samples: Int): Int = maxOf(1, (samples * scale).toInt())

    /** [roomSize] and [damping] are 0..1 as in Freeverb. */
    fun setRoom(roomSize: Float, damping: Float) {
        val feedback = roomSize * ROOM_SCALE + ROOM_OFFSET
        val damp = damping * DAMP_SCALE
        (combsLeft + combsRight).forEach {
            it.feedback = feedback
            it.damp = damp
        }
    }

    /**
     * Adds the wet signal of the mono [input] (first [frames] samples) to the interleaved stereo
     * [out], scaled by [wet].
     */
    fun processAdd(input: FloatArray, out: FloatArray, frames: Int, wet: Float) {
        for (i in 0 until frames) {
            val x = input[i] * FIXED_GAIN
            var l = 0f
            var r = 0f
            for (c in combsLeft) l += c.process(x)
            for (c in combsRight) r += c.process(x)
            for (a in allPassLeft) l = a.process(l)
            for (a in allPassRight) r = a.process(r)
            out[2 * i] += l * wet
            out[2 * i + 1] += r * wet
        }
    }

    fun reset() {
        (combsLeft + combsRight).forEach { it.reset() }
        (allPassLeft + allPassRight).forEach { it.reset() }
    }

    private class Comb(size: Int) {
        private val buffer = FloatArray(size)
        private var index = 0
        private var store = 0f
        var feedback = 0f
        var damp = 0f

        fun process(input: Float): Float {
            val output = buffer[index]
            store = output * (1f - damp) + store * damp
            buffer[index] = input + store * feedback
            index++
            if (index >= buffer.size) index = 0
            return output
        }

        fun reset() {
            buffer.fill(0f)
            store = 0f
            index = 0
        }
    }

    private class AllPass(size: Int) {
        private val buffer = FloatArray(size)
        private var index = 0

        fun process(input: Float): Float {
            val buffered = buffer[index]
            val output = -input + buffered
            buffer[index] = input + buffered * ALLPASS_FEEDBACK
            index++
            if (index >= buffer.size) index = 0
            return output
        }

        fun reset() {
            buffer.fill(0f)
            index = 0
        }
    }

    companion object {
        const val DEFAULT_ROOM = 0.6f
        const val DEFAULT_DAMPING = 0.5f
        private const val REFERENCE_RATE = 44_100f
        private const val STEREO_SPREAD = 23
        private const val FIXED_GAIN = 0.015f
        private const val ROOM_SCALE = 0.28f
        private const val ROOM_OFFSET = 0.7f
        private const val DAMP_SCALE = 0.4f
        private const val ALLPASS_FEEDBACK = 0.5f
        private val COMB_TUNINGS = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        private val ALLPASS_TUNINGS = intArrayOf(556, 441, 341, 225)
    }
}
