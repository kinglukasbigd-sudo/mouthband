package com.mouthband.ui.arrange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mouthband.data.SongRepository
import com.mouthband.data.TrackRepository
import com.mouthband.data.reachStep
import com.mouthband.mix.AutoMix
import com.mouthband.mix.Fader
import com.mouthband.mix.SongPlayer
import com.mouthband.model.CoachStep
import com.mouthband.model.Song
import com.mouthband.model.Track
import com.mouthband.model.TrackType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Why the song did not start playing. */
enum class PlaybackProblem { NOTHING_AUDIBLE, AUDIO_BUSY }

/** What the Arrange screen shows; [faders] holds positions while a slider is being dragged. */
data class ArrangeState(
    val song: Song? = null,
    val tracks: List<Track> = emptyList(),
    val playing: Boolean = false,
    val preparing: Boolean = false,
    val playingSection: Int = -1,
    val sheetIndex: Int? = null,
    val faders: Map<TrackType, Float> = emptyMap(),
    val problem: PlaybackProblem? = null,
    /** The track whose sound / pan / re-record sheet is open. */
    val trackSheet: TrackType? = null,
    /** The track being auditioned on its own (a sound chip was tapped, or "Hear this part"). */
    val auditioning: TrackType? = null,
    /** Pan positions (-1..1) while a pan slider is being dragged. */
    val pans: Map<TrackType, Float> = emptyMap(),
) {
    val empty: Boolean get() = song != null && tracks.none { it.hasContent }
}

/**
 * Coach step 7 (spec §1 Arrange & mix): section toggles, faders, mute, one-button auto-mix and
 * whole-song playback through the same mixer the export uses. Edits apply to the playing song
 * at once and are saved right away (autosave).
 */
class ArrangeViewModel(
    private val songId: Long,
    private val repository: SongRepository,
    private val trackRepository: TrackRepository,
    private val player: SongPlayer,
) : ViewModel() {
    private val _state = MutableStateFlow(ArrangeState())
    val state: StateFlow<ArrangeState> = _state.asStateFlow()
    private var ticker: Job? = null

    /** The per-track sheet: sound, pan, hear it alone. */
    val trackSheet = TrackSheetController(
        state = _state,
        repository = repository,
        trackRepository = trackRepository,
        player = player,
        scope = viewModelScope,
        hooks = TrackSheetController.Hooks(
            stopAll = ::stopTicking,
            interrupted = ::onInterrupted,
            ticking = ::startTicker,
            levels = ::applyFaders,
        ),
    )

    init {
        viewModelScope.launch { repository.reachStep(songId, CoachStep.ARRANGE) }
        viewModelScope.launch {
            combine(repository.song(songId), trackRepository.observe(songId)) { song, tracks -> song to tracks }
                .collect { (song, tracks) ->
                    _state.update { it.copy(song = song, tracks = tracks) }
                    if (song != null && _state.value.playing) reload(song, tracks)
                }
        }
    }

    fun togglePlay() {
        if (_state.value.playing || _state.value.preparing) stop() else play()
    }

    private fun play() {
        val song = _state.value.song ?: return
        trackSheet.cancelAudition()
        player.stop()
        _state.update { it.copy(preparing = true, problem = null, auditioning = null) }
        viewModelScope.launch {
            val audible = player.load(song, _state.value.tracks)
            applyFaders()
            val started = audible && player.start(onInterrupt = ::onInterrupted)
            val problem = when {
                !audible -> PlaybackProblem.NOTHING_AUDIBLE
                !started -> PlaybackProblem.AUDIO_BUSY
                else -> null
            }
            _state.update { it.copy(preparing = false, playing = started, problem = problem) }
            if (started) startTicker()
        }
    }

    fun stop() {
        stopTicking()
        trackSheet.cancelAudition()
        player.stop()
        _state.update { it.copy(playing = false, preparing = false, playingSection = -1, auditioning = null) }
    }

    private fun stopTicking() {
        ticker?.cancel()
    }

    private fun onInterrupted() {
        ticker?.cancel()
        _state.update { it.copy(playing = false, playingSection = -1, auditioning = null) }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                if (player.finished()) {
                    stop()
                    return@launch
                }
                // A solo audition has its own one-section layout: it must not light the song's strip.
                val solo = _state.value.auditioning != null
                val section = if (solo) -1 else player.mixer.sectionPlaying(player.heardFrame())
                _state.update { it.copy(playingSection = section) }
                delay(TICK_MS)
            }
        }
    }

    private suspend fun reload(song: Song, tracks: List<Track>) {
        player.load(song, tracks)
        applyFaders()
    }

    fun openSection(index: Int) = _state.update { it.copy(sheetIndex = index) }

    fun closeSection() = _state.update { it.copy(sheetIndex = null) }

    fun toggleTrack(section: Int, type: TrackType, on: Boolean) {
        val song = _state.value.song ?: return
        if (section !in song.arrangement.sections.indices) return
        val updated = song.copy(arrangement = song.arrangement.withSectionTrack(section, type, on))
        _state.update { it.copy(song = updated) }
        viewModelScope.launch { repository.updateSong(updated) }
    }

    /** Slider drag: heard immediately, saved on release. */
    fun setVolume(type: TrackType, position: Float) {
        val track = trackOf(type) ?: return
        _state.update { it.copy(faders = it.faders + (type to position)) }
        player.mixer.lanes[type.ordinal].setGainPan(Fader.toDb(position), track.pan)
    }

    fun volumeDone(type: TrackType) {
        val position = _state.value.faders[type] ?: return
        val track = trackOf(type) ?: return
        val updated = track.copy(gainDb = Fader.toDb(position))
        _state.update { s -> s.copy(tracks = s.tracks.replace(updated), faders = s.faders - type) }
        viewModelScope.launch { trackRepository.update(updated) }
    }

    fun toggleMute(type: TrackType) {
        val track = trackOf(type) ?: return
        val updated = track.copy(muted = !track.muted)
        _state.update { s -> s.copy(tracks = s.tracks.replace(updated)) }
        player.updateLevels(listOf(updated))
        viewModelScope.launch { trackRepository.update(updated) }
    }

    /** Auto-mix on: writes the level table and switches the processing on. Tapped again: off. */
    fun toggleAutoMix() {
        val song = _state.value.song ?: return
        val turnOn = !song.arrangement.autoMix
        val updatedSong = song.copy(arrangement = song.arrangement.copy(autoMix = turnOn))
        val updatedTracks = if (turnOn) AutoMix.apply(_state.value.tracks) else _state.value.tracks
        _state.update { it.copy(song = updatedSong, tracks = updatedTracks, faders = emptyMap()) }
        viewModelScope.launch {
            if (turnOn) updatedTracks.forEach { trackRepository.update(it) }
            repository.updateSong(updatedSong)
        }
    }

    private fun applyFaders() {
        val s = _state.value
        s.faders.forEach { (type, position) ->
            val pan = s.pans[type] ?: s.tracks.firstOrNull { it.type == type }?.pan ?: 0f
            player.mixer.lanes[type.ordinal].setGainPan(Fader.toDb(position), pan)
        }
        s.pans.filterKeys { it !in s.faders }.forEach { (type, pan) ->
            val gain = s.tracks.firstOrNull { it.type == type }?.gainDb ?: 0f
            player.mixer.lanes[type.ordinal].setGainPan(gain, pan)
        }
    }

    private fun trackOf(type: TrackType): Track? = _state.value.tracks.firstOrNull { it.type == type }

    private fun List<Track>.replace(track: Track): List<Track> = map { if (it.type == track.type) track else it }

    override fun onCleared() {
        stop()
    }

    private companion object {
        const val TICK_MS = 100L
    }
}
