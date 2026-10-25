package com.mouthband.model

import kotlinx.serialization.Serializable

@Serializable
enum class DrumClass { KICK, SNARE, HIHAT }

/**
 * One drum hit. [frameInLoop] is where the user performed it (after alignment); the grid
 * position is derived with the track's quantize strength so the Timing slider stays
 * non-destructive.
 */
@Serializable
data class DrumHit(
    val cls: DrumClass,
    val frameInLoop: Int,
    val velocity: Float,
)

/** One note, in loop frames, as performed (snapped to the key, not yet to the grid). */
@Serializable
data class NoteEvent(
    val midi: Int,
    val startFrame: Int,
    val endFrame: Int,
    val velocity: Float,
)

/**
 * Converted content of a track (eventsJson in Room). Frames are relative to [loopFrames] at
 * [sampleRate]; a different engine rate rescales them proportionally.
 */
@Serializable
data class TrackEvents(
    val sampleRate: Int,
    val loopFrames: Int,
    val hits: List<DrumHit> = emptyList(),
    val notes: List<NoteEvent> = emptyList(),
) {
    val isEmpty: Boolean get() = hits.isEmpty() && notes.isEmpty()

    /** Rescales event positions to another loop length (e.g. a different device sample rate). */
    fun rescaledTo(targetLoopFrames: Int, targetRate: Int): TrackEvents {
        if (targetLoopFrames == loopFrames) return copy(sampleRate = targetRate)
        val ratio = targetLoopFrames.toDouble() / loopFrames
        fun scale(frame: Int) = (frame * ratio).toInt().coerceIn(0, targetLoopFrames - 1)
        return TrackEvents(
            sampleRate = targetRate,
            loopFrames = targetLoopFrames,
            hits = hits.map { it.copy(frameInLoop = scale(it.frameInLoop)) },
            notes = notes.map {
                it.copy(
                    startFrame = scale(it.startFrame),
                    endFrame = (it.endFrame * ratio).toInt().coerceAtMost(targetLoopFrames),
                )
            },
        )
    }

    companion object {
        fun empty(sampleRate: Int, loopFrames: Int) = TrackEvents(sampleRate, loopFrames)
    }
}
