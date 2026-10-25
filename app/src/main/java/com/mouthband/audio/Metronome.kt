package com.mouthband.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Synthesized click track (spec §2.1): 1 kHz on beat 1, 800 Hz on the other beats, a 15 ms
 * decaying sine. Clicks are prebuilt; [render] only copies samples.
 */
class Metronome(private val sampleRate: Int) : RenderSource {
    /** Immutable click schedule, swapped by reference from the control thread. */
    class Plan(
        val startFrame: Long,
        val framesPerBeat: Double,
        val countInEndFrame: Long,
        val endFrame: Long,
        val clickAfterCountIn: Boolean,
        val gain: Float = DEFAULT_GAIN,
    )

    private val accent = buildClick(ACCENT_HZ)
    private val normal = buildClick(NORMAL_HZ)

    @Volatile
    var plan: Plan? = null

    // Render-thread state.
    private var current: FloatArray? = null
    private var position = 0
    private var currentGain = 0f

    override fun render(out: FloatArray, frames: Int, transportFrame: Long) {
        val p = plan
        var beatIndex = if (p == null) 0L else firstBeatAtOrAfter(p, transportFrame)
        var nextBeat = if (p == null) Long.MAX_VALUE else beatFrame(p, beatIndex)
        for (i in 0 until frames) {
            if (p != null && transportFrame + i == nextBeat) {
                trigger(p, nextBeat, beatIndex)
                beatIndex++
                nextBeat = beatFrame(p, beatIndex)
            }
            val click = current
            if (click != null) {
                val s = click[position] * currentGain
                out[2 * i] += s
                out[2 * i + 1] += s
                position++
                if (position >= click.size) current = null
            }
        }
    }

    private fun trigger(p: Plan, frame: Long, beatIndex: Long) {
        if (frame >= p.endFrame || (frame >= p.countInEndFrame && !p.clickAfterCountIn)) return
        current = if (beatIndex % LoopMath.BEATS_PER_BAR == 0L) accent else normal
        position = 0
        currentGain = p.gain
    }

    private fun firstBeatAtOrAfter(p: Plan, frame: Long): Long {
        val rel = frame - p.startFrame
        var index = if (rel <= 0) 0L else floor(rel / p.framesPerBeat).toLong()
        while (beatFrame(p, index) < frame) index++
        return index
    }

    private fun beatFrame(p: Plan, index: Long): Long = p.startFrame + (index * p.framesPerBeat).roundToLong()

    private fun buildClick(hz: Double): FloatArray {
        val length = (sampleRate * CLICK_SECONDS).toInt()
        return FloatArray(length) { n ->
            val t = n.toDouble() / sampleRate
            val envelope = exp(-DECAY * n / length) * (1.0 - n.toDouble() / length)
            (sin(2.0 * PI * hz * t) * envelope).toFloat()
        }
    }

    companion object {
        const val ACCENT_HZ = 1000.0
        const val NORMAL_HZ = 800.0
        const val CLICK_SECONDS = 0.015
        private const val DECAY = 4.0
        const val DEFAULT_GAIN = 0.5f
    }
}
