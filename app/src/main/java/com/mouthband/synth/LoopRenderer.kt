package com.mouthband.synth

import com.mouthband.audio.LoopMath
import com.mouthband.convert.Quantizer
import com.mouthband.mix.VocalChain
import com.mouthband.model.DrumKit
import com.mouthband.model.Instrument
import com.mouthband.model.TrackEvents
import com.mouthband.model.TrackKind
import com.mouthband.model.TrackType

/** Everything that determines a track's rendered loop. */
data class RenderSpec(
    val type: TrackType,
    val events: TrackEvents?,
    val rawLoop: FloatArray?,
    val instrument: Instrument,
    val kit: DrumKit,
    val quantize: Float,
    val bpm: Int,
    val loopFrames: Int,
    val chordWidth: Float = 0f,
    /** Bake the vocal reverb send into the loop (loop-only playback; the song mixer adds its own). */
    val vocalReverb: Boolean = false,
)

/**
 * Turns a track into its pre-rendered stereo loop buffer (spec §2.6 Rendering). Called on
 * Dispatchers.Default whenever events, sound or timing change; the engine only ever plays the
 * result. Vocal takes pass through the [VocalChain] (non-destructive).
 */
class LoopRenderer(library: InstrumentLibrary, private val sampleRate: Int) {
    private val vocalChain = VocalChain(sampleRate)
    private val drums = DrumKitRenderer(library)
    private val pitched = PitchedRenderer(library, sampleRate)

    /** Stereo loop of exactly `loopFrames × 2` samples, or null when the track has no content. */
    fun render(spec: RenderSpec): FloatArray? {
        val step = LoopMath.framesPerStep(spec.bpm, sampleRate)
        return when (spec.type.kind) {
            TrackKind.DRUMS -> spec.events?.takeIf { it.hits.isNotEmpty() }?.let { events ->
                val hits = Quantizer.hits(
                    events.rescaledTo(spec.loopFrames, sampleRate).hits,
                    step,
                    spec.quantize,
                    spec.loopFrames,
                )
                drums.render(hits, spec.kit, spec.loopFrames, step)
            }
            TrackKind.PITCHED -> spec.events?.takeIf { it.notes.isNotEmpty() }?.let { events ->
                val notes = Quantizer.notes(
                    events.rescaledTo(spec.loopFrames, sampleRate).notes,
                    step,
                    spec.quantize,
                    spec.loopFrames,
                )
                val spread = spec.type == TrackType.CHORDS && spec.chordWidth > 0f
                val pans = if (spread) chordPans(notes.map { it.startFrame to it.midi }, spec.chordWidth) else null
                pitched.render(notes, spec.instrument, spec.loopFrames, pans)
            }
            TrackKind.RAW -> spec.rawLoop?.let { mono ->
                val dry = vocalChain.process(fit(mono, spec.loopFrames))
                if (spec.vocalReverb) vocalChain.withReverb(dry) else toStereo(dry)
            }
        }
    }

    /** Raw take as a centred stereo loop (A/B "Your voice"). */
    fun rawStereo(mono: FloatArray, loopFrames: Int): FloatArray = toStereo(fit(mono, loopFrames))

    private fun fit(mono: FloatArray, loopFrames: Int): FloatArray =
        if (mono.size == loopFrames) mono else FloatArray(loopFrames) { if (it < mono.size) mono[it] else 0f }

    private fun toStereo(mono: FloatArray): FloatArray {
        val out = FloatArray(mono.size * 2)
        for (i in mono.indices) {
            out[2 * i] = mono[i]
            out[2 * i + 1] = mono[i]
        }
        return out
    }

    /** Spreads each chord's notes from left to right by ±[width] (auto-mix "±15 % stereo width"). */
    private fun chordPans(notes: List<Pair<Int, Int>>, width: Float): FloatArray {
        val pans = FloatArray(notes.size)
        notes.indices.groupBy { notes[it].first }.values.forEach { chord ->
            val ordered = chord.sortedBy { notes[it].second }
            ordered.forEachIndexed { rank, index ->
                pans[index] = if (ordered.size == 1) 0f else -width + 2f * width * rank / (ordered.size - 1)
            }
        }
        return pans
    }
}
