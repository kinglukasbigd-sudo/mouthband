package com.mouthband.synth

import com.mouthband.model.Instrument

enum class Waveform { SAW, SQUARE, SINE }

/** Two oscillators; the second is offset by [cents] (detune or an interval) and mixed at [level]. */
class Oscillators(val first: Waveform, val second: Waveform, val cents: Float, val level: Float)

/** 24 dB/oct low-pass whose cutoff rises by [envOctaves] at the envelope's peak. */
class FilterSection(val cutoffHz: Float, val resonance: Float, val envOctaves: Float, val env: Adsr.Settings)

/** Subtractive synth patch (spec §2.6 Synth): 2 oscillators with detune, 24 dB low-pass with envelope, ADSR. */
class SynthPatch(val osc: Oscillators, val filter: FilterSection, val amp: Adsr.Settings, val gain: Float)

/** The synth patch of every preset; sample presets use theirs as the fallback (spec §2.6). */
object SynthPatches {
    fun of(instrument: Instrument): SynthPatch = Patches.getValue(instrument)
}

private fun adsr(attack: Float, decay: Float, sustain: Float, release: Float) =
    Adsr.Settings(attack, decay, sustain, release)

private val Patches: Map<Instrument, SynthPatch> = mapOf(
    Instrument.SYNTH_BASS to SynthPatch(
        osc = Oscillators(Waveform.SAW, Waveform.SQUARE, cents = 7f, level = 0.6f),
        filter = FilterSection(
            cutoffHz = 380f,
            resonance = 1.1f,
            envOctaves = 2.2f,
            env = adsr(attack = 0.002f, decay = 0.22f, sustain = 0.2f, release = 0.1f),
        ),
        amp = adsr(attack = 0.004f, decay = 0.25f, sustain = 0.75f, release = 0.08f),
        gain = 0.55f,
    ),
    Instrument.DEEP_SUB to SynthPatch(
        osc = Oscillators(Waveform.SINE, Waveform.SINE, cents = 1200f, level = 0.12f),
        filter = FilterSection(
            cutoffHz = 500f,
            resonance = 0.7f,
            envOctaves = 0.5f,
            env = adsr(attack = 0.002f, decay = 0.3f, sustain = 0.5f, release = 0.2f),
        ),
        amp = adsr(attack = 0.004f, decay = 0.4f, sustain = 0.9f, release = 0.12f),
        gain = 0.9f,
    ),
    Instrument.ELECTRIC_BASS to SynthPatch(
        osc = Oscillators(Waveform.SQUARE, Waveform.SAW, cents = -5f, level = 0.5f),
        filter = FilterSection(
            cutoffHz = 650f,
            resonance = 0.8f,
            envOctaves = 1.5f,
            env = adsr(attack = 0.001f, decay = 0.15f, sustain = 0.1f, release = 0.1f),
        ),
        amp = adsr(attack = 0.003f, decay = 0.6f, sustain = 0.45f, release = 0.08f),
        gain = 0.6f,
    ),
    Instrument.SOFT_KEYS to SynthPatch(
        osc = Oscillators(Waveform.SINE, Waveform.SAW, cents = 1200f, level = 0.18f),
        filter = FilterSection(
            cutoffHz = 1800f,
            resonance = 0.7f,
            envOctaves = 1.2f,
            env = adsr(attack = 0.002f, decay = 0.5f, sustain = 0.2f, release = 0.3f),
        ),
        amp = adsr(attack = 0.003f, decay = 1.4f, sustain = 0.35f, release = 0.35f),
        gain = 0.45f,
    ),
    Instrument.WARM_PAD to SynthPatch(
        osc = Oscillators(Waveform.SAW, Waveform.SAW, cents = 12f, level = 0.9f),
        filter = FilterSection(
            cutoffHz = 900f,
            resonance = 0.8f,
            envOctaves = 0.8f,
            env = adsr(attack = 0.4f, decay = 0.8f, sustain = 0.5f, release = 0.8f),
        ),
        amp = adsr(attack = 0.25f, decay = 0.5f, sustain = 0.85f, release = 0.7f),
        gain = 0.3f,
    ),
    Instrument.ORGAN to SynthPatch(
        osc = Oscillators(Waveform.SQUARE, Waveform.SINE, cents = 1200f, level = 0.7f),
        filter = FilterSection(
            cutoffHz = 3200f,
            resonance = 0.6f,
            envOctaves = 0f,
            env = adsr(attack = 0.001f, decay = 0.1f, sustain = 1f, release = 0.05f),
        ),
        amp = adsr(attack = 0.006f, decay = 0.05f, sustain = 1f, release = 0.07f),
        gain = 0.28f,
    ),
    Instrument.PIANO to SynthPatch(
        osc = Oscillators(Waveform.SAW, Waveform.SINE, cents = 1200f, level = 0.5f),
        filter = FilterSection(
            cutoffHz = 1600f,
            resonance = 0.7f,
            envOctaves = 1.8f,
            env = adsr(attack = 0.001f, decay = 0.6f, sustain = 0.1f, release = 0.3f),
        ),
        amp = adsr(attack = 0.002f, decay = 1.6f, sustain = 0f, release = 0.3f),
        gain = 0.45f,
    ),
    Instrument.FLUTE to SynthPatch(
        osc = Oscillators(Waveform.SINE, Waveform.SQUARE, cents = 1200f, level = 0.08f),
        filter = FilterSection(
            cutoffHz = 2400f,
            resonance = 0.6f,
            envOctaves = 0.3f,
            env = adsr(attack = 0.06f, decay = 0.2f, sustain = 0.8f, release = 0.15f),
        ),
        amp = adsr(attack = 0.06f, decay = 0.2f, sustain = 0.85f, release = 0.15f),
        gain = 0.6f,
    ),
    Instrument.BRIGHT_LEAD to SynthPatch(
        osc = Oscillators(Waveform.SAW, Waveform.SAW, cents = 10f, level = 0.8f),
        filter = FilterSection(
            cutoffHz = 2600f,
            resonance = 1.3f,
            envOctaves = 1f,
            env = adsr(attack = 0.003f, decay = 0.25f, sustain = 0.6f, release = 0.15f),
        ),
        amp = adsr(attack = 0.004f, decay = 0.2f, sustain = 0.8f, release = 0.15f),
        gain = 0.32f,
    ),
    Instrument.BELL to SynthPatch(
        osc = Oscillators(Waveform.SINE, Waveform.SINE, cents = 1760f, level = 0.45f),
        filter = FilterSection(
            cutoffHz = 6000f,
            resonance = 0.6f,
            envOctaves = 0f,
            env = adsr(attack = 0.001f, decay = 0.1f, sustain = 1f, release = 0.5f),
        ),
        amp = adsr(attack = 0.001f, decay = 1.6f, sustain = 0f, release = 0.6f),
        gain = 0.55f,
    ),
    Instrument.VOICE to SynthPatch(
        osc = Oscillators(Waveform.SINE, Waveform.SINE, cents = 0f, level = 0f),
        filter = FilterSection(
            cutoffHz = 2000f,
            resonance = 0.7f,
            envOctaves = 0f,
            env = adsr(attack = 0.01f, decay = 0.1f, sustain = 1f, release = 0.1f),
        ),
        amp = adsr(attack = 0.01f, decay = 0.1f, sustain = 1f, release = 0.1f),
        gain = 0.5f,
    ),
)
