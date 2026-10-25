package com.mouthband.convert

import com.mouthband.model.DrumHit
import com.mouthband.model.NoteEvent
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 16th-note quantization (spec §2.4 step 4, §2.5): `t' = t + strength × (gridT − t)`, wrapped
 * into the loop. Strength 0 keeps the performance ("Human"), 1 snaps fully ("Tight").
 */
object Quantizer {
    /** Quantized loop position of [frame]. */
    fun frame(frame: Int, stepFrames: Double, strength: Float, loopFrames: Int): Int {
        val grid = (frame / stepFrames).roundToLong() * stepFrames
        val moved = frame + strength.coerceIn(0f, 1f) * (grid - frame)
        return Math.floorMod(moved.roundToLong(), loopFrames.toLong()).toInt()
    }

    /** Nearest 16th cell of [frame] (0 until totalSteps), independent of strength. */
    fun cell(frame: Int, stepFrames: Double, totalSteps: Int): Int =
        Math.floorMod((frame / stepFrames).roundToInt(), totalSteps)

    fun hits(hits: List<DrumHit>, stepFrames: Double, strength: Float, loopFrames: Int): List<DrumHit> =
        hits.map { it.copy(frameInLoop = frame(it.frameInLoop, stepFrames, strength, loopFrames)) }
            .sortedBy { it.frameInLoop }

    /** If two hits of the same class land in the same 16th, keep the louder one. */
    fun dedupe(hits: List<DrumHit>, stepFrames: Double, totalSteps: Int): List<DrumHit> =
        hits.groupBy { it.cls to cell(it.frameInLoop, stepFrames, totalSteps) }
            .values
            .map { group -> group.maxBy { it.velocity } }
            .sortedBy { it.frameInLoop }

    /**
     * Quantizes note start and end; the minimum length is one 16th. Notes keep loop-relative
     * frames; a note may end after the loop end (it wraps when rendered).
     */
    fun notes(notes: List<NoteEvent>, stepFrames: Double, strength: Float, loopFrames: Int): List<NoteEvent> =
        notes.map { note ->
            val s = strength.coerceIn(0f, 1f)
            val startGrid = (note.startFrame / stepFrames).roundToLong() * stepFrames
            val endGrid = (note.endFrame / stepFrames).roundToLong() * stepFrames
            val start = (note.startFrame + s * (startGrid - note.startFrame)).roundToInt()
            var end = (note.endFrame + s * (endGrid - note.endFrame)).roundToInt()
            val minLength = stepFrames.roundToInt()
            if (end - start < minLength) end = start + minLength
            val wrappedStart = Math.floorMod(start, loopFrames)
            note.copy(startFrame = wrappedStart, endFrame = wrappedStart + (end - start))
        }.sortedBy { it.startFrame }
}
