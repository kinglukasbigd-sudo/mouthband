package com.mouthband.audio

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Musical time ↔ frames. Every track of a song uses exactly [loopFrames] (spec §2.1). */
object LoopMath {
    const val BEATS_PER_BAR = 4
    const val STEPS_PER_BEAT = 4
    const val STEPS_PER_BAR = BEATS_PER_BAR * STEPS_PER_BEAT
    private const val SECONDS_PER_MINUTE = 60.0
    private const val MS_PER_SECOND = 1000.0

    fun framesPerBeat(bpm: Int, sampleRate: Int): Double = SECONDS_PER_MINUTE / bpm * sampleRate

    fun framesPerBar(bpm: Int, sampleRate: Int): Double = framesPerBeat(bpm, sampleRate) * BEATS_PER_BAR

    fun framesPerStep(bpm: Int, sampleRate: Int): Double = framesPerBeat(bpm, sampleRate) / STEPS_PER_BEAT

    /** `bars × 4 × 60 / bpm × sampleRate`, rounded to an integer. */
    fun loopFrames(bars: Int, bpm: Int, sampleRate: Int): Int =
        (bars * BEATS_PER_BAR * framesPerBeat(bpm, sampleRate)).roundToInt()

    /** Frame of a bar boundary from the song start, rounded independently so sections never drift. */
    fun barStartFrame(bar: Int, bpm: Int, sampleRate: Int): Long =
        (bar * framesPerBar(bpm, sampleRate)).roundToLong()

    fun msToFrames(ms: Double, sampleRate: Int): Int = (ms * sampleRate / MS_PER_SECOND).roundToInt()

    fun framesToMs(frames: Long, sampleRate: Int): Double = frames * MS_PER_SECOND / sampleRate
}

/** The 16th-step grid of one loop: [bars] bars of [stepFrames] frames per step, [loopFrames] in total. */
class LoopGrid(val bars: Int, bpm: Int, sampleRate: Int) {
    val stepFrames: Double = LoopMath.framesPerStep(bpm, sampleRate)
    val loopFrames: Int = LoopMath.loopFrames(bars, bpm, sampleRate)
    val totalSteps: Int get() = bars * LoopMath.STEPS_PER_BAR
}
