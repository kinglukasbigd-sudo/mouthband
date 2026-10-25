package com.mouthband.ui.arrange

import com.mouthband.data.SongRepository
import com.mouthband.data.TrackRepository
import com.mouthband.mix.Fader
import com.mouthband.mix.SongPlayer
import com.mouthband.model.Arrangement
import com.mouthband.model.DrumKit
import com.mouthband.model.Instrument
import com.mouthband.model.Section
import com.mouthband.model.SectionType
import com.mouthband.model.Track
import com.mouthband.model.TrackKind
import com.mouthband.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The per-track sheet in Arrange: choose another sound for a part and hear it on its own, move
 * its pan, or hear the part as it is now. Every change is saved at once and heard immediately.
 * It shares [state] with the Arrange view model and borrows its playback through [hooks].
 */
class TrackSheetController(
    private val state: MutableStateFlow<ArrangeState>,
    private val repository: SongRepository,
    private val trackRepository: TrackRepository,
    private val player: SongPlayer,
    private val scope: CoroutineScope,
    private val hooks: Hooks,
) {
    /** What the Arrange view model lends: stopping its ticker, restarting it, and re-applying levels. */
    class Hooks(
        val stopAll: () -> Unit,
        val interrupted: () -> Unit,
        val ticking: () -> Unit,
        val levels: () -> Unit,
    )

    private var auditionJob: Job? = null

    fun open(type: TrackType) = state.update { it.copy(trackSheet = type) }

    /** Closing the sheet ends an audition that is still playing. */
    fun close() {
        if (state.value.auditioning != null) {
            cancelAudition()
            hooks.stopAll()
            player.stop()
            state.update { it.copy(auditioning = null, preparing = false) }
        }
        state.update { it.copy(trackSheet = null) }
    }

    fun cancelAudition() {
        auditionJob?.cancel()
    }

    /**
     * Gives a track another sound and plays it on its own, so sounds can be compared by ear. The
     * choice is saved at once: a drum kit belongs to the song, an instrument to the track.
     */
    fun selectSound(type: TrackType, id: String) {
        val applied = if (type.kind == TrackKind.DRUMS) applyKit(id) else applyInstrument(type, id)
        if (applied) audition(type)
    }

    private fun applyKit(id: String): Boolean {
        val kit = DrumKit.entries.firstOrNull { it.name == id }
        val song = state.value.song
        if (kit == null || song == null) return false
        val updated = song.copy(kit = kit)
        state.update { it.copy(song = updated) }
        scope.launch { repository.updateSong(updated) }
        return true
    }

    private fun applyInstrument(type: TrackType, id: String): Boolean {
        val instrument = Instrument.entries.firstOrNull { it.name == id && it.track == type }
        val track = trackOf(type)
        if (instrument == null || track == null) return false
        val updated = track.copy(instrument = instrument)
        state.update { s -> s.copy(tracks = s.tracks.replace(updated)) }
        scope.launch { trackRepository.update(updated) }
        return true
    }

    /** Plays [type] alone, two loops long, with the sound it has now. */
    fun audition(type: TrackType) {
        val song = state.value.song ?: return
        // A muted part is still heard when the user asks to hear it (to compare sounds on it).
        val tracks = state.value.tracks.map { if (it.type == type) it.copy(muted = false) else it }
        hooks.stopAll()
        cancelAudition()
        player.stop()
        state.update { it.copy(playing = false, preparing = true, playingSection = -1, auditioning = type) }
        auditionJob = scope.launch {
            val solo = Section(SectionType.VERSE, song.loopBars * AUDITION_LOOPS, setOf(type))
            val soloSong = song.copy(arrangement = Arrangement(listOf(solo), song.arrangement.autoMix))
            val audible = player.load(soloSong, tracks)
            hooks.levels()
            val started = audible && player.start(onInterrupt = hooks.interrupted)
            state.update { it.copy(preparing = false, auditioning = type.takeIf { started }) }
            if (started) hooks.ticking()
        }
    }

    /** Pan slider drag: heard immediately, saved on release. */
    fun setPan(type: TrackType, pan: Float) {
        val track = trackOf(type) ?: return
        val value = pan.coerceIn(-1f, 1f)
        state.update { it.copy(pans = it.pans + (type to value)) }
        val gain = state.value.faders[type]?.let { Fader.toDb(it) } ?: track.gainDb
        player.mixer.lanes[type.ordinal].setGainPan(gain, value)
    }

    fun panDone(type: TrackType) {
        val pan = state.value.pans[type] ?: return
        val track = trackOf(type) ?: return
        val updated = track.copy(pan = pan)
        state.update { s -> s.copy(tracks = s.tracks.replace(updated), pans = s.pans - type) }
        scope.launch { trackRepository.update(updated) }
    }

    private fun trackOf(type: TrackType): Track? = state.value.tracks.firstOrNull { it.type == type }

    private fun List<Track>.replace(track: Track): List<Track> = map { if (it.type == track.type) track else it }

    private companion object {
        const val AUDITION_LOOPS = 2
    }
}
