package com.mouthband.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mouthband.data.prefs.AudioSettings
import com.mouthband.data.prefs.LatencySource
import com.mouthband.data.prefs.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings screen state (spec §1 Settings). Privacy options are added by the consent manager. */
class SettingsViewModel(private val settings: SettingsStore) : ViewModel() {
    val audio: StateFlow<AudioSettings> = settings.audio
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AudioSettings())

    fun setClick(on: Boolean) {
        viewModelScope.launch { settings.setClickOn(on) }
    }

    fun setCountIn(bars: Int) {
        viewModelScope.launch { settings.setCountInBars(bars) }
    }

    /** Whether the stored latency was measured (otherwise it is the 150 ms fallback). */
    fun isMeasured(audio: AudioSettings): Boolean = audio.latencySource != LatencySource.FALLBACK

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
