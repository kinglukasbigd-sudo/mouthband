package com.mouthband.mix

import com.mouthband.dsp.Biquad
import com.mouthband.dsp.Compressor
import com.mouthband.dsp.Reverb

/**
 * Vocal cleanup (spec §2.7), applied non-destructively whenever a vocal loop is rendered:
 * high-pass 90 Hz → compressor 3:1 at −18 dB (10 ms / 120 ms) → +3 dB presence at 4 kHz.
 * The 15 % reverb send is added by the mixer; [withReverb] bakes it in for loop-only playback.
 */
class VocalChain(private val sampleRate: Int) {
    /** Dry processed copy of a mono vocal loop. */
    fun process(mono: FloatArray): FloatArray {
        val out = mono.copyOf()
        val highPass = Biquad().apply { coefficients = Biquad.highPass(sampleRate, HIGH_PASS_HZ) }
        val presence = Biquad().apply { coefficients = Biquad.peak(sampleRate, PRESENCE_HZ, PRESENCE_DB, PRESENCE_Q) }
        val compressor = Compressor(sampleRate, THRESHOLD_DB, RATIO, ATTACK_MS, RELEASE_MS, makeupDb = MAKEUP_DB)
        for (i in out.indices) {
            out[i] = presence.process(compressor.process(highPass.process(out[i])))
        }
        return out
    }

    /**
     * Interleaved stereo loop = dry + [REVERB_SEND] reverb. The loop is fed twice so the tail of
     * the end wraps seamlessly into the start.
     */
    fun withReverb(dry: FloatArray): FloatArray {
        val n = dry.size
        val reverb = Reverb(sampleRate)
        val warmup = FloatArray(n * 2)
        reverb.processAdd(dry, warmup, n, REVERB_SEND)
        val out = FloatArray(n * 2)
        reverb.processAdd(dry, out, n, REVERB_SEND)
        for (i in 0 until n) {
            out[2 * i] += dry[i]
            out[2 * i + 1] += dry[i]
        }
        return out
    }

    companion object {
        const val HIGH_PASS_HZ = 90f
        const val THRESHOLD_DB = -18f
        const val RATIO = 3f
        const val ATTACK_MS = 10f
        const val RELEASE_MS = 120f
        const val PRESENCE_HZ = 4000f
        const val PRESENCE_DB = 3f
        const val REVERB_SEND = 0.15f
        private const val PRESENCE_Q = 0.9f
        private const val MAKEUP_DB = 3f
    }
}
