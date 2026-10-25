package com.mouthband.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.mouthband.R
import com.mouthband.ui.common.ScreenScaffold
import com.mouthband.ui.components.LevelMeter
import com.mouthband.ui.components.PrimaryPillButton
import com.mouthband.ui.components.TextLinkButton
import com.mouthband.ui.components.TopBar
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbTheme

/** Status text and controls shared by onboarding page 3 and the Settings calibration screen. */
@Composable
fun CalibrationPanel(
    phase: CalibrationPhase,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MbDimens.Gap)) {
        val status = calibrationStatus(phase)
        if (phase is CalibrationPhase.Running) {
            LevelMeter(level = { phase.progress }, description = stringResource(R.string.cal_running))
        }
        if (status != null) {
            Text(
                status,
                style = MbTheme.type.label,
                color = MbColors.Indigo,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        when (phase) {
            is CalibrationPhase.Done -> PrimaryPillButton(
                stringResource(R.string.action_done),
                onContinue,
                Modifier.fillMaxWidth(),
                showArrow = true,
            )
            is CalibrationPhase.Running -> Unit
            else -> PrimaryPillButton(
                stringResource(if (phase is CalibrationPhase.Idle) R.string.cal_start else R.string.action_retry),
                onStart,
                Modifier.fillMaxWidth(),
                showArrow = true,
            )
        }
        if (phase !is CalibrationPhase.Done && phase !is CalibrationPhase.Running) {
            TextLinkButton(
                stringResource(R.string.cal_skip),
                onSkip,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                underline = false,
                color = MbColors.Muted,
            )
        }
    }
}

/** Settings → Latency calibration. */
@Composable
fun CalibrationScreen(
    phase: CalibrationPhase,
    onBack: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(
        modifier = modifier,
        bottomBar = { CalibrationPanel(phase, onStart = onStart, onSkip = onBack, onContinue = onBack) },
    ) {
        TopBar(backLabel = stringResource(R.string.action_back), onBack = onBack)
        Text(
            stringResource(R.string.cal_title),
            style = MbTheme.type.h1,
            color = MbColors.Indigo,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.cal_body), style = MbTheme.type.body, color = MbColors.InkSoft)
    }
}

@Composable
private fun calibrationStatus(phase: CalibrationPhase): String? = when (phase) {
    CalibrationPhase.Idle -> null
    is CalibrationPhase.Running -> stringResource(R.string.cal_running)
    is CalibrationPhase.Done -> stringResource(R.string.cal_done, phase.latencyMs)
    is CalibrationPhase.Failed ->
        stringResource(R.string.cal_failed) + "\n" + stringResource(R.string.cal_fallback, phase.fallbackMs)
    is CalibrationPhase.Blocked ->
        stringResource(if (phase.bluetooth) R.string.cal_bluetooth else R.string.cal_headphones)
}
