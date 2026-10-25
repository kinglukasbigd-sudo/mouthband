package com.mouthband.model

import kotlinx.serialization.Serializable

/** Drum kits. Pop reuses the other kits' samples (spec §2.6). */
@Serializable
enum class DrumKit {
    BOOM_BAP,
    TRAP,
    LOFI,
    ROCK,
    POP,
}

/** Where a preset's sound comes from. Sample presets fall back to a synth patch until assets exist. */
enum class SoundSource { SAMPLE, SYNTH }

/** The 10 pitched presets, plus the user's own voice for vocal tracks. */
@Serializable
enum class Instrument(val track: TrackType?, val source: SoundSource, val assetFolder: String?) {
    SYNTH_BASS(TrackType.BASS, SoundSource.SYNTH, null),
    DEEP_SUB(TrackType.BASS, SoundSource.SYNTH, null),
    ELECTRIC_BASS(TrackType.BASS, SoundSource.SAMPLE, "electric_bass"),
    SOFT_KEYS(TrackType.CHORDS, SoundSource.SAMPLE, "soft_keys"),
    WARM_PAD(TrackType.CHORDS, SoundSource.SYNTH, null),
    ORGAN(TrackType.CHORDS, SoundSource.SYNTH, null),
    PIANO(TrackType.MELODY, SoundSource.SAMPLE, "piano"),
    FLUTE(TrackType.MELODY, SoundSource.SAMPLE, "flute"),
    BRIGHT_LEAD(TrackType.MELODY, SoundSource.SYNTH, null),
    BELL(TrackType.MELODY, SoundSource.SYNTH, null),
    VOICE(null, SoundSource.SAMPLE, null),
    ;

    companion object {
        fun forTrack(type: TrackType): List<Instrument> = entries.filter { it.track == type }
    }
}
