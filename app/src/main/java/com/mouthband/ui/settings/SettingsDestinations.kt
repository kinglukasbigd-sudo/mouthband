package com.mouthband.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mouthband.BuildConfig
import com.mouthband.R
import com.mouthband.ui.common.appViewModel

@Composable
fun SettingsDestination(navigation: SettingsNavigation, privacyRequired: Boolean) {
    val vm = appViewModel { c, _ -> SettingsViewModel(c.settings) }
    val audio by vm.audio.collectAsStateWithLifecycle()
    val latency = if (vm.isMeasured(audio)) {
        stringResource(R.string.settings_latency_value, audio.latencyMs)
    } else {
        stringResource(R.string.settings_latency_not_set, audio.latencyMs)
    }
    SettingsScreen(
        state = SettingsUi(
            latencyText = latency,
            clickOn = audio.clickOn,
            countInBars = audio.countInBars,
            privacyRequired = privacyRequired,
            version = BuildConfig.VERSION_NAME,
        ),
        actions = SettingsActions(
            onBack = navigation.onBack,
            onCalibrate = navigation.onCalibrate,
            onNudge = navigation.onNudge,
            onTeach = navigation.onTeach,
            onClick = vm::setClick,
            onCountIn = vm::setCountIn,
            onPrivacy = navigation.onPrivacy,
            onLicenses = navigation.onLicenses,
        ),
    )
}

@Composable
fun CalibrationDestination(onBack: () -> Unit) {
    val vm = appViewModel { c, _ -> CalibrationViewModel(c.calibrator, c.outputDevices, c.audioSession) }
    val phase by vm.phase.collectAsStateWithLifecycle()
    CalibrationScreen(phase = phase, onBack = onBack, onStart = vm::start)
}

@Composable
fun NudgeDestination(onBack: () -> Unit) {
    val vm = appViewModel { c, _ -> NudgeViewModel(c.engine, c.audioSession, c.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    NudgeScreen(
        state = state,
        actions = NudgeActions(
            onBack = onBack,
            onOffset = vm::setOffset,
            onPlayToggle = vm::togglePlay,
            onSave = { vm.save(onBack) },
        ),
    )
}
