package com.mouthband.convert

/**
 * Frame-wise pitch of 16 kHz mono audio. Frame `t` covers samples `[t × hop, (t + 1) × hop)`;
 * [hz] is 0 where no pitch was found; [confidence] is 0..1.
 */
class PitchTrack(val hz: FloatArray, val confidence: FloatArray) {
    val size: Int get() = hz.size
}

/** A pitch estimator for 16 kHz audio (spec §2.5): SwiftF0 primary, YIN fallback. */
interface PitchTracker {
    /** Human-readable name for logs. */
    val name: String

    /** Confidence at or above which a frame counts as voiced. */
    val voicedThreshold: Float

    fun track(audio16k: FloatArray): PitchTrack

    companion object {
        const val SAMPLE_RATE = 16_000
        const val HOP = 256
        const val MIN_HZ = 46.875f
        const val MAX_HZ = 2093.75f

        /** Frames whose audio peaks below this are silent: confidence 0 (as SwiftF0 does). */
        const val SILENCE_PEAK = 1e-3f
    }
}
