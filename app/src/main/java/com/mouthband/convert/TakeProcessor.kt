package com.mouthband.convert

import com.mouthband.audio.AlignedTake
import com.mouthband.dsp.Rms
import com.mouthband.model.DrumHit
import com.mouthband.model.MusicalKey
import com.mouthband.model.NoteEvent
import com.mouthband.model.TrackEvents
import com.mouthband.model.TrackKind
import com.mouthband.model.TrackType

/**
 * Result of converting one take: performed events (null for vocals) or why it failed, and the
 * conditioned [loop] (one loop of audio at a clear level) that is kept as the raw take.
 */
class Converted(val events: TrackEvents?, val problem: TakeProblem?, val loop: FloatArray)

/** Picks the converter for a track type (spec §1 track table). Runs on Dispatchers.Default. */
class TakeProcessor(
    private val sampleRate: Int,
    private val beatbox: BeatboxConverter,
    private val pitch: PitchConverter,
) {
    /** What the converters need besides the audio. */
    class Context(val bpm: Int, val key: MusicalKey, val templates: BeatboxTemplates?, val clicksAudible: Boolean)

    /**
     * Conditions the take (any recording level, clipped or not) and converts it. A take with
     * nothing above the microphone's own noise reports [TakeProblem.SILENCE].
     */
    fun process(type: TrackType, take: AlignedTake, context: Context): Converted {
        val conditioned = TakeConditioner.condition(take)
        val clear = conditioned.take
        val loop = clear.loopOnly()
        if (!conditioned.audible) return Converted(null, TakeProblem.SILENCE, loop)
        return when (type.kind) {
            TrackKind.DRUMS -> {
                val result = beatbox.convert(clear, context.bpm, context.templates, context.clicksAudible)
                Converted(eventsOrNull(clear, hits = result.hits), result.problem, loop)
            }
            TrackKind.PITCHED -> {
                val result = pitch.convert(clear, type, context.key, context.bpm)
                Converted(eventsOrNull(clear, notes = result.notes), result.problem, loop)
            }
            TrackKind.RAW -> {
                val db = Rms.toDb(Rms.of(clear.samples, clear.prerollFrames, clear.loopFrames))
                // A vocal take must hold a voice: noise is reported, never boosted into a loud hiss.
                val silent = db < VOCAL_SILENCE_DB || !VoicePresence.hasVoice(clear)
                Converted(null, if (silent) TakeProblem.SILENCE else null, loop)
            }
        }
    }

    private fun eventsOrNull(
        take: AlignedTake,
        hits: List<DrumHit> = emptyList(),
        notes: List<NoteEvent> = emptyList(),
    ): TrackEvents? =
        if (hits.isEmpty() && notes.isEmpty()) null else TrackEvents(sampleRate, take.loopFrames, hits, notes)

    private companion object {
        const val VOCAL_SILENCE_DB = -55f
    }
}
