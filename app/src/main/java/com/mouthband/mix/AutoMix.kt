package com.mouthband.mix

import com.mouthband.model.Track
import com.mouthband.model.TrackType

/**
 * One-button mix (spec §2.8). Gains and pans are written to the tracks (the sliders stay
 * editable); the processing (bass low-pass, chord width, vocal ducking, bus compressor) is
 * switched on through `Arrangement.autoMix`.
 */
object AutoMix {
    /** ±15 % stereo spread of chord voices. */
    const val CHORD_WIDTH = 0.15f

    private const val BEAT_DB = 0f
    private const val BASS_DB = -2f
    private const val CHORDS_DB = -6f
    private const val MELODY_DB = -4f
    private const val VOCALS_DB = 1f
    private const val MELODY_PAN = 0.1f

    fun settingsFor(type: TrackType): Pair<Float, Float> = when (type) {
        TrackType.BEAT -> BEAT_DB to 0f
        TrackType.BASS -> BASS_DB to 0f
        TrackType.CHORDS -> CHORDS_DB to 0f
        TrackType.MELODY -> MELODY_DB to MELODY_PAN
        TrackType.VOCALS_VERSE, TrackType.VOCALS_CHORUS -> VOCALS_DB to 0f
    }

    fun apply(tracks: List<Track>): List<Track> = tracks.map { track ->
        val (gain, pan) = settingsFor(track.type)
        track.copy(gainDb = gain, pan = pan)
    }
}

/** Fader travel 0..1 ↔ gain in dB (−30 … +6 dB; the bottom of the travel is −60 dB). */
object Fader {
    const val MIN_DB = -30f
    const val MAX_DB = 6f
    const val SILENT_DB = -60f

    fun toDb(position: Float): Float =
        if (position <= 0f) SILENT_DB else MIN_DB + (MAX_DB - MIN_DB) * position.coerceAtMost(1f)

    fun toPosition(db: Float): Float = ((db - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0f, 1f)
}
