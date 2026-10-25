package com.mouthband.convert

import ai.onnxruntime.OrtException
import com.mouthband.audio.AlignedTake
import com.mouthband.audio.AudioLog
import com.mouthband.audio.LoopMath
import com.mouthband.dsp.Resampler
import com.mouthband.dsp.Rms
import com.mouthband.model.MusicalKey
import com.mouthband.model.NoteEvent
import com.mouthband.model.TrackType
import kotlin.math.roundToInt

/** Converted pitched take: performed notes (loop frames) or a problem. */
data class NotesResult(val notes: List<NoteEvent>, val problem: TakeProblem?)

/**
 * Voice → notes (spec §2.5): resample to 16 kHz, track pitch (SwiftF0, YIN fallback), segment
 * notes, snap to the key, then fold bass/melody into their ranges or turn hummed roots into
 * diatonic triads. Positions stay as performed; the Timing slider quantizes them later.
 */
class PitchConverter(
    private val sampleRate: Int,
    private val primary: () -> PitchTracker?,
    private val fallback: PitchTracker = YinPitchTracker(),
) {
    private val segmenter = NoteSegmenter(PitchTracker.HOP.toDouble() / PitchTracker.SAMPLE_RATE)

    fun convert(take: AlignedTake, type: TrackType, key: MusicalKey, bpm: Int): NotesResult {
        val loopRms = Rms.of(take.samples, take.prerollFrames, take.loopFrames)
        if (Rms.toDb(loopRms) < SILENCE_DB) return NotesResult(emptyList(), TakeProblem.SILENCE)
        val audio16k = Resampler.resample(take.samples, sampleRate, PitchTracker.SAMPLE_RATE)
        val (track, threshold) = trackPitch(audio16k)
        val midi = FloatArray(track.size) { t ->
            val hz = track.hz[t]
            val usable = track.confidence[t] >= threshold && hz in PitchTracker.MIN_HZ..PitchTracker.MAX_HZ
            if (usable) MusicTheory.hzToMidi(hz) else Float.NaN
        }
        val segments = segmenter.segment(midi)
        val step = LoopMath.framesPerStep(bpm, sampleRate)
        val placed = segments.mapNotNull { place(it, take, step) }
        if (placed.isEmpty()) return NotesResult(emptyList(), TakeProblem.NO_PITCH)
        val snapped = snap(placed, key)
        val notes = when (type) {
            TrackType.CHORDS -> chords(snapped, key, take.loopFrames, LoopMath.framesPerBar(bpm, sampleRate))
            else -> {
                val folded = MusicTheory.rangeFor(type)
                    ?.let { range -> snapped.map { it.copy(midi = MusicTheory.foldIntoRange(it.midi, range)) } }
                    ?: snapped
                // Flow like the hummed line: short breaks close, wobble-split repeats join.
                Legato.apply(folded, take.loopFrames, maxGap = step.roundToInt())
            }
        }
        return NotesResult(notes.sortedBy { it.startFrame }, null)
    }

    private fun trackPitch(audio16k: FloatArray): Pair<PitchTrack, Float> {
        val tracker = primary()
        if (tracker != null) {
            try {
                return tracker.track(audio16k) to tracker.voicedThreshold
            } catch (e: OrtException) {
                AudioLog.w("${tracker.name} failed, using ${fallback.name}", e)
            }
        }
        return fallback.track(audio16k) to fallback.voicedThreshold
    }

    /** Maps a segment onto the loop; drops notes outside the take's loop window. */
    private fun place(note: PitchNote, take: AlignedTake, step: Double): Placed? {
        val scale = sampleRate.toDouble() * PitchTracker.HOP / PitchTracker.SAMPLE_RATE
        val start = (note.startFrame * scale).roundToInt() - take.prerollFrames
        val end = (note.endFrame * scale).roundToInt() - take.prerollFrames
        if (start < -step / 2 || start >= take.loopFrames + step / 2) return null
        val velocity = velocityOf(take, start + take.prerollFrames, end - start)
        val wrapped = Math.floorMod(start, take.loopFrames)
        return Placed(note.pitch, wrapped, wrapped + (end - start), velocity)
    }

    private class Placed(val pitch: Float, val start: Int, val end: Int, val velocity: Float)

    private fun velocityOf(take: AlignedTake, from: Int, length: Int): Float {
        val db = Rms.toDb(Rms.of(take.samples, from, length.coerceAtLeast(1)))
        return (MIN_VELOCITY + (db - QUIET_DB) / (LOUD_DB - QUIET_DB) * (1f - MIN_VELOCITY)).coerceIn(MIN_VELOCITY, 1f)
    }

    /** Snap in time order so ties follow the melody's direction (spec §2.5 step 1). */
    private fun snap(placed: List<Placed>, key: MusicalKey): List<NoteEvent> {
        var previous: Int? = null
        return placed.sortedBy { it.start }.map { p ->
            val midi = MusicTheory.snapToScale(p.pitch, key, previous)
            previous = midi
            NoteEvent(midi, p.start, p.end, p.velocity)
        }
    }

    /**
     * Each hummed note is a chord root (spec §2.5 Chords): more than 2 notes in a bar keeps only
     * the longest; every chord lasts until the next chord or the end of its bar. A root sung just
     * before the loop wraps around belongs to the first bar.
     */
    private fun chords(roots: List<NoteEvent>, key: MusicalKey, loopFrames: Int, barFrames: Double): List<NoteEvent> {
        val bars = (loopFrames / barFrames).roundToInt().coerceAtLeast(1)
        val halfStep = barFrames / LoopMath.STEPS_PER_BAR / 2
        fun barOf(start: Int) = ((start + halfStep) / barFrames).toInt().coerceIn(0, bars - 1)
        val anchored = roots.map { root ->
            if (root.startFrame + halfStep >= loopFrames) root.shiftedBy(-loopFrames) else root
        }
        val kept = anchored.groupBy { barOf(it.startFrame) }
            .flatMap { (_, inBar) ->
                if (inBar.size > MAX_CHORDS_PER_BAR) listOf(inBar.maxBy { it.endFrame - it.startFrame }) else inBar
            }
            .sortedBy { it.startFrame }
        return kept.flatMapIndexed { i, root ->
            val barEnd = ((barOf(root.startFrame) + 1) * barFrames).roundToInt()
            val nextStart = kept.getOrNull(i + 1)?.startFrame ?: Int.MAX_VALUE
            val length = minOf(barEnd, nextStart) - root.startFrame
            val start = Math.floorMod(root.startFrame, loopFrames)
            MusicTheory.triad(root.midi, key).map { NoteEvent(it, start, start + length, root.velocity) }
        }
    }

    private fun NoteEvent.shiftedBy(frames: Int) = copy(startFrame = startFrame + frames, endFrame = endFrame + frames)

    private companion object {
        /** Spec §2.5 chords: more hummed roots than this in a bar keep only the longest. */
        const val MAX_CHORDS_PER_BAR = 2
        const val SILENCE_DB = -55f
        const val MIN_VELOCITY = 0.6f
        const val QUIET_DB = -40f
        const val LOUD_DB = -10f
    }
}
