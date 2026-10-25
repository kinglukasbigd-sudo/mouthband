package com.mouthband.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mouthband.audio.AudioEngine
import com.mouthband.audio.AudioLog
import com.mouthband.audio.AudioSession
import com.mouthband.audio.LoopMath
import com.mouthband.audio.LoopPlayer
import com.mouthband.audio.Metronome
import com.mouthband.audio.TakeFile
import com.mouthband.data.prefs.LastTake
import com.mouthband.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Manual latency nudge (spec §2.2): loops the last kept take over the click, shifted by a
 * ±150 ms slider. Saving adds the shift to the stored latency.
 */
class NudgeViewModel(
    private val engine: AudioEngine,
    private val session: AudioSession,
    private val settings: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(NudgeUi())
    val state: StateFlow<NudgeUi> = _state.asStateFlow()
    private val player = LoopPlayer(1)
    private var take: FloatArray? = null
    private var last: LastTake? = null

    init {
        viewModelScope.launch {
            val ref = settings.lastTake.first() ?: return@launch
            val audio = withContext(Dispatchers.IO) {
                try {
                    TakeFile.read(File(ref.path)).takeIf { it.sampleRate == engine.sampleRate }?.samples
                } catch (e: IOException) {
                    AudioLog.w("nudge take", e)
                    null
                }
            }
            if (audio != null) {
                take = audio
                last = ref
                _state.value = _state.value.copy(hasTake = true)
            }
        }
    }

    fun setOffset(ms: Int) {
        _state.value = _state.value.copy(offsetMs = ms.coerceIn(-NUDGE_RANGE_MS, NUDGE_RANGE_MS))
        if (_state.value.playing) loadShifted()
    }

    fun togglePlay() {
        if (_state.value.playing) stop() else play()
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val audio = settings.audioNow()
            // Moving the take later means the stored compensation was too large.
            settings.setLatency(audio.latencyMs - _state.value.offsetMs, audio.latencySource)
            stop()
            onSaved()
        }
    }

    private fun play() {
        val ref = last ?: return
        if (!session.begin { stop() } || !engine.start()) return
        val beat = LoopMath.framesPerBeat(ref.bpm, engine.sampleRate)
        player.loopStartFrame = 0
        player.loopFrames = LoopMath.loopFrames(ref.loopBars, ref.bpm, engine.sampleRate)
        loadShifted()
        engine.source = player
        engine.metronome.plan = Metronome.Plan(0, beat, Long.MAX_VALUE, Long.MAX_VALUE, clickAfterCountIn = true)
        engine.resetTransport(0)
        _state.value = _state.value.copy(playing = true)
    }

    private fun stop() {
        engine.metronome.plan = null
        engine.source = null
        engine.stop()
        session.end()
        _state.value = _state.value.copy(playing = false)
    }

    /** Rebuilds the stereo loop with the take delayed by the slider offset (off the audio thread). */
    private fun loadShifted() {
        val mono = take ?: return
        val frames = player.loopFrames
        if (frames <= 0) return
        val shift = LoopMath.msToFrames(_state.value.offsetMs.toDouble(), engine.sampleRate)
        val stereo = FloatArray(frames * 2)
        for (j in 0 until frames) {
            val source = Math.floorMod(j - shift, frames)
            val v = if (source < mono.size) mono[source] else 0f
            stereo[2 * j] = v
            stereo[2 * j + 1] = v
        }
        player.lanes[0].buffer = stereo
    }

    override fun onCleared() {
        if (_state.value.playing) stop()
    }
}
