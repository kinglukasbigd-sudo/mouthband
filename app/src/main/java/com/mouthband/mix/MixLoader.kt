package com.mouthband.mix

import com.mouthband.model.Song
import com.mouthband.model.Track
import com.mouthband.model.TrackType
import com.mouthband.synth.RenderCache

/**
 * Fills a [SongMixer] from a song: every kept track's loop (chords spread when auto-mix is on,
 * vocals dry — the mixer adds their reverb send), gains, pans, mutes and the layout.
 * Call off the main thread; it may render.
 */
class MixLoader(private val cache: RenderCache, private val sampleRate: Int) {
    /** Loads [mixer]; returns false when nothing in the arrangement has audio. */
    fun load(mixer: SongMixer, song: Song, tracks: List<Track>): Boolean {
        mixer.autoMix = song.arrangement.autoMix
        var anything = false
        TrackType.entries.forEach { type ->
            val lane = mixer.lanes[type.ordinal]
            val track = tracks.firstOrNull { it.type == type }
            val width = if (type == TrackType.CHORDS && song.arrangement.autoMix) AutoMix.CHORD_WIDTH else 0f
            lane.buffer = track?.let { cache.loopOf(song, it, chordWidth = width) }
            lane.setGainPan(track?.gainDb ?: 0f, track?.pan ?: 0f)
            lane.muted = track?.muted ?: false
            val playsSomewhere = song.arrangement.sections.any { type in it.active }
            if (lane.buffer != null && playsSomewhere && !lane.muted) anything = true
        }
        mixer.layout = SongLayout.of(song.arrangement, song.bpm, song.loopBars, sampleRate)
        return anything
    }

    /** Applies only gain/pan/mute changes (no re-render): slider moves while playing. */
    fun updateLevels(mixer: SongMixer, tracks: List<Track>) {
        tracks.forEach { track ->
            val lane = mixer.lanes[track.type.ordinal]
            lane.setGainPan(track.gainDb, track.pan)
            lane.muted = track.muted
        }
    }
}
