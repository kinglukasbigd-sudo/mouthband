package com.mouthband.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mouthband.R
import com.mouthband.ui.common.ScreenScaffold
import com.mouthband.ui.components.MonoLabel
import com.mouthband.ui.components.PrimaryPillButton
import com.mouthband.ui.components.StepProgressStrip
import com.mouthband.ui.components.StepSegment
import com.mouthband.ui.components.easeOut
import com.mouthband.ui.icons.MbIcons
import com.mouthband.ui.settings.CalibrationPanel
import com.mouthband.ui.settings.CalibrationPhase
import com.mouthband.ui.theme.LocalReduceMotion
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbShapes
import com.mouthband.ui.theme.MbTheme

const val ONBOARDING_PAGES = 3
private const val TRANSITION_MS = 200
private val HeroSize = 120.dp
private val HeroIconSize = 56.dp

@Immutable
data class OnboardingUi(val page: Int = 0, val calibration: CalibrationPhase = CalibrationPhase.Idle)

@Immutable
data class OnboardingActions(
    val onNext: () -> Unit,
    val onAllowMic: () -> Unit,
    val onCalibrate: () -> Unit,
    val onFinish: () -> Unit,
)

/** First launch only: 3 short pages, the last one runs latency calibration. */
@Composable
fun OnboardingScreen(state: OnboardingUi, actions: OnboardingActions, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    ScreenScaffold(
        modifier = modifier,
        bottomBar = {
            when (state.page) {
                0 -> PrimaryPillButton(
                    stringResource(R.string.action_next),
                    actions.onNext,
                    Modifier.fillMaxWidth(),
                    showArrow = true,
                )
                1 -> PrimaryPillButton(
                    stringResource(R.string.mic_allow),
                    actions.onAllowMic,
                    Modifier.fillMaxWidth(),
                    showArrow = true,
                )
                else -> CalibrationPanel(
                    phase = state.calibration,
                    onStart = actions.onCalibrate,
                    onSkip = actions.onFinish,
                    onContinue = actions.onFinish,
                )
            }
        },
    ) {
        StepProgressStrip(
            segments = List(ONBOARDING_PAGES) {
                if (it <= state.page) StepSegment.Done(MbColors.Indigo) else StepSegment.Empty
            },
            description = stringResource(R.string.onb_page, state.page + 1),
        )
        MonoLabel(stringResource(R.string.onb_page, state.page + 1))
        AnimatedContent(
            targetState = state.page,
            transitionSpec = {
                val duration = if (reduceMotion) 0 else TRANSITION_MS
                fadeIn(easeOut(duration)) togetherWith fadeOut(easeOut(duration))
            },
            label = "onboardingPage",
        ) { page ->
            OnboardingPage(page)
        }
    }
}

@Composable
private fun OnboardingPage(page: Int) {
    val (title, body, icon) = when (page) {
        0 -> Triple(R.string.onb1_title, R.string.onb1_body, MbIcons.Wave)
        1 -> Triple(R.string.onb2_title, R.string.onb2_body, MbIcons.Mic)
        else -> Triple(R.string.onb3_title, R.string.onb3_body, MbIcons.Speaker)
    }
    Column(verticalArrangement = Arrangement.spacedBy(MbDimens.GapLarge)) {
        Box(
            Modifier.size(HeroSize).clip(MbShapes.Circle).background(MbColors.Indigo),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MbColors.Tangerine, modifier = Modifier.size(HeroIconSize))
        }
        Text(
            stringResource(title),
            style = MbTheme.type.display,
            color = MbColors.Indigo,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(body), style = MbTheme.type.body, color = MbColors.InkSoft)
    }
}
