package com.mouthband.model

/** Coach step 1 moods: each one sets tempo, key, drum kit and default instruments. */
enum class Mood(
    val bpm: Int,
    val key: MusicalKey,
    val kit: DrumKit,
    val bass: Instrument,
    val chords: Instrument,
    val melody: Instrument,
) {
    CHILL(
        85,
        MusicalKey(9, KeyMode.MINOR),
        DrumKit.LOFI,
        Instrument.ELECTRIC_BASS,
        Instrument.SOFT_KEYS,
        Instrument.FLUTE,
    ),
    POP(110, MusicalKey(0, KeyMode.MAJOR), DrumKit.POP, Instrument.SYNTH_BASS, Instrument.SOFT_KEYS, Instrument.PIANO),
    HYPE(
        140,
        MusicalKey(5, KeyMode.MINOR),
        DrumKit.TRAP,
        Instrument.DEEP_SUB,
        Instrument.WARM_PAD,
        Instrument.BRIGHT_LEAD,
    ),
    SAD(
        75,
        MusicalKey(2, KeyMode.MINOR),
        DrumKit.LOFI,
        Instrument.ELECTRIC_BASS,
        Instrument.WARM_PAD,
        Instrument.PIANO,
    ),
    ROCK(
        120,
        MusicalKey(4, KeyMode.MINOR),
        DrumKit.ROCK,
        Instrument.ELECTRIC_BASS,
        Instrument.ORGAN,
        Instrument.BRIGHT_LEAD,
    ),
    ;

    fun instrumentFor(type: TrackType): Instrument = when (type) {
        TrackType.BASS -> bass
        TrackType.CHORDS -> chords
        TrackType.MELODY -> melody
        TrackType.BEAT, TrackType.VOCALS_VERSE, TrackType.VOCALS_CHORUS -> Instrument.VOICE
    }

    companion object {
        const val MIN_BPM = 60
        const val MAX_BPM = 180
    }
}
