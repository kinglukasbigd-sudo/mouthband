package com.mouthband.ui.teach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mouthband.audio.MicMonitor
import com.mouthband.audio.RecorderStart
import com.mouthband.convert.BeatboxConverter
import com.mouthband.convert.BeatboxTemplates
import com.mouthband.convert.LiveOnsetDetector
import com.mouthband.convert.TemplateExample
import com.mouthband.data.prefs.SettingsStore
import com.mouthband.data.prefs.TemplateStore
import com.mouthband.model.DrumClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Teach your sounds (spec §1): the user says kick, snare and hi-hat 4 times each; every
 * detected sound becomes a feature template. All 12 are saved to DataStore at the end.
 */
class TeachViewModel(
    private val monitor: MicMonitor,
    private val templates: TemplateStore,
    private val settings: SettingsStore,
    private val sampleRate: Int,
) : ViewModel() {
    private val _state = MutableStateFlow(TeachUi())
    /** The live mic waveform; read by the waveform while drawing, so it never recomposes Teach. */
    val peaks: StateFlow<FloatArray> = monitor.peaks
    val state: StateFlow<TeachUi> = _state.asStateFlow()
    private val collected = ArrayList<TemplateExample>()
    private val classes = DrumClass.entries

    init {
        viewModelScope.launch {
            val existing = templates.current()
            if (existing?.isComplete == true) {
                _state.update { it.copy(counts = List(classes.size) { TEACH_REPEATS }, phase = TeachPhase.DONE) }
            }
        }
    }

    /** Starts listening for the first sound. */
    fun start(): RecorderStart {
        synchronized(collected) { collected.clear() }
        _state.update { it.copy(counts = List(classes.size) { 0 }, activeIndex = 0, phase = TeachPhase.LISTENING) }
        val detector = LiveOnsetDetector(sampleRate) { _, analysis ->
            if (analysis.peakRmsDb >= BeatboxConverter.NOISE_FLOOR_DB) onExample(analysis.values.toList())
        }
        val result = monitor.start(viewModelScope, detector)
        if (result != RecorderStart.OK) _state.update { it.copy(activeIndex = -1, phase = TeachPhase.READY) }
        return result
    }

    private fun onExample(features: List<Float>) {
        val finished = synchronized(collected) {
            val index = _state.value.activeIndex
            if (index !in classes.indices) return
            collected += TemplateExample(classes[index], features)
            val counts = _state.value.counts.toMutableList().also { it[index] = it[index] + 1 }
            val next = if (counts[index] >= TEACH_REPEATS) index + 1 else index
            val done = next >= classes.size
            _state.update {
                it.copy(
                    counts = counts,
                    activeIndex = if (done) -1 else next,
                    phase = if (done) TeachPhase.DONE else TeachPhase.LISTENING,
                )
            }
            if (done) BeatboxTemplates(collected.toList()) else null
        }
        if (finished != null) {
            viewModelScope.launch {
                monitor.stop()
                templates.save(finished)
                settings.setTeachPromptShown()
            }
        }
    }

    fun stop() {
        monitor.stop()
        if (_state.value.phase == TeachPhase.LISTENING) {
            _state.update { it.copy(phase = TeachPhase.READY, activeIndex = -1) }
        }
    }

    /** "Reset" clears the stored templates (spec §1). */
    fun reset() {
        monitor.stop()
        synchronized(collected) { collected.clear() }
        viewModelScope.launch { templates.reset() }
        _state.value = TeachUi()
    }

    override fun onCleared() {
        monitor.stop()
    }
}
