package com.mouthband.audio

/**
 * Something the render thread mixes into the output. Implementations must be allocation-free,
 * lock-free and must not log: [render] runs on the urgent-audio thread.
 */
fun interface RenderSource {
    /**
     * Adds [frames] stereo frames into [out] (interleaved L/R, already holding other sources).
     * [transportFrame] is the transport position of the first frame of the block.
     */
    fun render(out: FloatArray, frames: Int, transportFrame: Long)
}
