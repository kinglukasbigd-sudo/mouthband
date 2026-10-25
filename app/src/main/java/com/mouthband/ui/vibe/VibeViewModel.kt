package com.mouthband.ui.vibe

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mouthband.data.SongRepository
import com.mouthband.data.TrackRepository
import com.mouthband.data.reachStep
import com.mouthband.data.setVibe
import com.mouthband.model.CoachStep
import com.mouthband.model.KeyMode
import com.mouthband.model.Mood
import com.mouthband.model.MusicalKey
import com.mouthband.model.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Settings a Vibe screen shows; [locked] once any take was kept. */
data class VibeState(val song: Song? = null, val mood: Mood? = null, val locked: Boolean = false, val taps: Int = 0)

/**
 * Coach step 1 (spec §1): a mood sets tempo, key, kit and instruments; tap tempo and manual
 * pickers fine-tune. Edits are kept locally and written after a short pause.
 */
class VibeViewModel(
    private val songId: Long,
    private val repository: SongRepository,
    private val tracks: TrackRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(VibeState())
    val state: StateFlow<VibeState> = _state.asStateFlow()
    private val next = Channel<Unit>(Channel.CONFLATED)
    val proceed = next.receiveAsFlow()
    private val taps = ArrayList<Long>()
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            combine(repository.song(songId), tracks.observe(songId)) { song, list ->
                song to list.any { it.hasContent }
            }
                .collect { (song, locked) ->
                    if (saveJob?.isActive != true) {
                        _state.value = _state.value.copy(
                            song = song,
                            locked = locked,
                            mood = _state.value.mood ?: song?.let(::matchMood),
                        )
                    }
                }
        }
    }

    fun selectMood(mood: Mood) {
        if (_state.value.locked) return
        _state.value = _state.value.copy(mood = mood)
        viewModelScope.launch { tracks.applyMood(songId, mood) }
    }

    /** Tap tempo: median interval of the last taps; a pause over 2 s starts over. */
    fun tap() {
        val now = SystemClock.elapsedRealtime()
        if (taps.isNotEmpty() && now - taps.last() > TAP_RESET_MS) taps.clear()
        taps += now
        if (taps.size > MAX_TAPS) taps.removeAt(0)
        _state.value = _state.value.copy(taps = taps.size)
        if (taps.size >= MIN_TAPS) {
            val intervals = taps.zipWithNext { a, b -> b - a }.sorted()
            val median = intervals[intervals.size / 2]
            setBpm((MS_PER_MINUTE / median).roundToInt())
        }
    }

    fun setBpm(bpm: Int) = edit { it.copy(bpm = bpm.coerceIn(Mood.MIN_BPM, Mood.MAX_BPM)) }

    fun setKeyRoot(root: Int) = edit { it.copy(key = MusicalKey(root, it.key.mode)) }

    fun setKeyMode(mode: KeyMode) = edit { it.copy(key = MusicalKey(it.key.root, mode)) }

    fun setLoopBars(bars: Int) = edit { it.copy(loopBars = bars) }

    fun next() {
        viewModelScope.launch {
            saveJob?.join()
            repository.reachStep(songId, CoachStep.BEAT)
            next.send(Unit)
        }
    }

    private fun edit(change: (Song) -> Song) {
        val song = _state.value.song ?: return
        if (_state.value.locked) return
        val updated = change(song)
        _state.value = _state.value.copy(song = updated, mood = matchMood(updated))
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            repository.setVibe(songId, updated.bpm, updated.key, updated.loopBars)
        }
    }

    private fun matchMood(song: Song): Mood? = Mood.entries.firstOrNull { it.bpm == song.bpm && it.key == song.key }

    private companion object {
        const val TAP_RESET_MS = 2_000L
        const val MIN_TAPS = 4
        const val MAX_TAPS = 8
        const val MS_PER_MINUTE = 60_000f
        const val SAVE_DELAY_MS = 300L
    }
}
