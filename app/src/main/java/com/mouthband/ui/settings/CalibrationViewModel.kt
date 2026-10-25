package com.mouthband.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mouthband.audio.AudioSession
import com.mouthband.audio.LatencyCalibrator
import com.mouthband.audio.OutputDeviceMonitor
import com.mouthband.data.prefs.AudioSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Runs latency calibration for Settings and onboarding page 3 (spec §2.2). */
class CalibrationViewModel(
    private val calibrator: LatencyCalibrator,
    private val devices: OutputDeviceMonitor,
    private val session: AudioSession,
) : ViewModel() {
    private val _phase = MutableStateFlow<CalibrationPhase>(CalibrationPhase.Idle)
    val phase: StateFlow<CalibrationPhase> = _phase.asStateFlow()
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        val route = devices.route.value
        if (!route.isSpeaker) {
            _phase.value = CalibrationPhase.Blocked(bluetooth = route.bluetooth)
            return
        }
        job = viewModelScope.launch {
            if (!session.begin { job?.cancel() }) {
                _phase.value = CalibrationPhase.Failed(AudioSettings.DEFAULT_LATENCY_MS)
                return@launch
            }
            _phase.value = CalibrationPhase.Running(0f)
            val outcome = try {
                calibrator.run { progress -> _phase.value = CalibrationPhase.Running(progress) }
            } finally {
                session.end()
            }
            _phase.value = when (outcome) {
                is LatencyCalibrator.Outcome.Measured -> CalibrationPhase.Done(outcome.latencyMs)
                is LatencyCalibrator.Outcome.TooQuiet -> CalibrationPhase.Failed(outcome.fallbackMs)
                LatencyCalibrator.Outcome.NoPermission,
                LatencyCalibrator.Outcome.AudioUnavailable,
                -> CalibrationPhase.Failed(AudioSettings.DEFAULT_LATENCY_MS)
            }
        }
        job?.invokeOnCompletion { cause ->
            if (cause != null && _phase.value is CalibrationPhase.Running) _phase.value = CalibrationPhase.Idle
        }
    }

    override fun onCleared() {
        job?.cancel()
    }
}
