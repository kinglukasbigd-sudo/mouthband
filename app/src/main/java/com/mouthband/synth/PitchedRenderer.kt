package com.mouthband.synth

import com.mouthband.audio.PanLaw
import com.mouthband.model.Instrument
import com.mouthband.model.NoteEvent

/**
 * Renders notes into a seamless stereo loop with a 16-voice pool and oldest-first voice
 * stealing (spec §2.6). Sample presets use their multisamples when bundled, otherwise their
 * synth patch. Releases ring past the loop end and wrap to its start.
 */
class PitchedRenderer(private val library: InstrumentLibrary, private val sampleRate: Int) {
    /** [pans] gives each note's pan (-1..1), e.g. a chord spread; null centres everything. */
    fun render(notes: List<NoteEvent>, instrument: Instrument, loopFrames: Int, pans: FloatArray? = null): FloatArray {
        val out = FloatArray(loopFrames * 2)
        if (notes.isEmpty()) return out
        val voices = Array(MAX_VOICES) { createVoice(instrument) }
        val voicePan = FloatArray(MAX_VOICES)
        val owner = IntArray(MAX_VOICES) { -1 }
        val sorted = notes.indices.sortedBy { notes[it].startFrame }
        val tail = (TAIL_SECONDS * sampleRate).toInt()
        val end = (notes.maxOf { it.endFrame } + tail).coerceAtLeast(loopFrames)
        var next = 0
        val gains = Array(MAX_VOICES) { FloatArray(2) }
        for (t in 0 until end) {
            while (next < sorted.size && notes[sorted[next]].startFrame <= t) {
                val noteIndex = sorted[next]
                val slot = allocate(voices)
                voices[slot].start(notes[noteIndex].midi, notes[noteIndex].velocity, t.toLong())
                owner[slot] = noteIndex
                voicePan[slot] = pans?.getOrNull(noteIndex) ?: 0f
                panGains(voicePan[slot], gains[slot])
                next++
            }
            var left = 0f
            var right = 0f
            for (v in voices.indices) {
                val voice = voices[v]
                if (!voice.isActive) continue
                val note = owner[v]
                if (note >= 0 && t == notes[note].endFrame) {
                    voice.release()
                    owner[v] = -1
                }
                val s = voice.next()
                left += s * gains[v][0]
                right += s * gains[v][1]
            }
            val pos = t % loopFrames
            out[2 * pos] += left
            out[2 * pos + 1] += right
        }
        return out
    }

    private fun createVoice(instrument: Instrument): Voice {
        val patch = SynthPatches.of(instrument)
        val samples = library.sampleSet(instrument)
        return if (samples != null) SamplerVoice(sampleRate, samples, patch.amp, SAMPLER_GAIN) else SynthVoice(
            sampleRate,
            patch,
        )
    }

    /** A free voice, or the oldest one (stolen with a quick fade replaced immediately). */
    private fun allocate(voices: Array<Voice>): Int {
        voices.indices.firstOrNull { !voices[it].isActive }?.let { return it }
        val oldest = voices.indices.minBy { voices[it].startedAt }
        voices[oldest].steal()
        return oldest
    }

    private fun panGains(pan: Float, out: FloatArray) {
        out[0] = PanLaw.left(0f, pan)
        out[1] = PanLaw.right(0f, pan)
    }

    companion object {
        /** Spec §2.6: 16 voices with voice stealing. */
        const val MAX_VOICES = 16
        private const val TAIL_SECONDS = 2.0
        private const val SAMPLER_GAIN = 0.8f
    }
}
