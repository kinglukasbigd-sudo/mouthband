package com.mouthband.audio

import android.os.SystemClock

/**
 * Notices a microphone that stopped delivering audio mid-take (device error, input taken by
 * another app), so a recording ends with an error instead of waiting forever.
 */
class MicWatchdog(
    private val limitMs: Long = STALL_MS,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private var lastFrames = -1L
    private var lastChange = clock()

    /** Feed the recorder's captured-frame count; true once it has not moved for [limitMs]. */
    fun stalled(frames: Long): Boolean {
        val now = clock()
        if (frames != lastFrames) {
            lastFrames = frames
            lastChange = now
        }
        return now - lastChange > limitMs
    }

    private companion object {
        const val STALL_MS = 1_500L
    }
}
