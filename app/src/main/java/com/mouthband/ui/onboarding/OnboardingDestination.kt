package com.mouthband.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mouthband.ui.coach.MicAccess
import com.mouthband.ui.common.appContainer
import com.mouthband.ui.common.appViewModel
import com.mouthband.ui.common.rememberMicPermission
import com.mouthband.ui.settings.CalibrationViewModel
import kotlinx.coroutines.launch

/** First-launch flow: welcome, mic permission (rationale then system dialog), calibration. */
@Composable
fun OnboardingDestination(onFinished: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableIntStateOf(0) }
    val calibration = appViewModel { c, _ -> CalibrationViewModel(c.calibrator, c.outputDevices, c.audioSession) }
    val phase by calibration.phase.collectAsStateWithLifecycle()
    val mic = rememberMicPermission { page = 2 }
    OnboardingScreen(
        state = OnboardingUi(page = page, calibration = phase),
        actions = OnboardingActions(
            onNext = { page = 1 },
            onAllowMic = { if (mic.access == MicAccess.GRANTED) page = 2 else mic.request() },
            onCalibrate = calibration::start,
            onFinish = {
                scope.launch {
                    container.settings.setOnboardingDone()
                    onFinished()
                }
            },
        ),
    )
}
