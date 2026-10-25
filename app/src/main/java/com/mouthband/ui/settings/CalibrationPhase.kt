package com.mouthband.ui.settings

import androidx.compose.runtime.Immutable

/** Where a latency calibration run stands. */
@Immutable
sealed interface CalibrationPhase {
    data object Idle : CalibrationPhase

    data class Running(val progress: Float) : CalibrationPhase

    data class Done(val latencyMs: Int) : CalibrationPhase

    data class Failed(val fallbackMs: Int) : CalibrationPhase

    /** Calibration needs the phone speaker: Bluetooth or wired headphones block it. */
    data class Blocked(val bluetooth: Boolean) : CalibrationPhase
}
