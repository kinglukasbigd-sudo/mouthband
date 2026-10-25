package com.mouthband.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mouthband.R
import com.mouthband.ui.common.ScreenScaffold
import com.mouthband.ui.components.EmptyState
import com.mouthband.ui.components.IconCircleButton
import com.mouthband.ui.components.IndigoPillButton
import com.mouthband.ui.components.MbSlider
import com.mouthband.ui.components.MonoLabel
import com.mouthband.ui.components.TopBar
import com.mouthband.ui.icons.MbIcons
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbTheme

/** Largest manual latency correction in either direction (spec §1 Settings: ±150 ms). */
const val NUDGE_RANGE_MS = 150

@Immutable
data class NudgeUi(
    val hasTake: Boolean = false,
    val offsetMs: Int = 0,
    val playing: Boolean = false,
)

@Immutable
data class NudgeActions(
    val onBack: () -> Unit,
    val onOffset: (Int) -> Unit,
    val onPlayToggle: () -> Unit,
    val onSave: () -> Unit,
)

/** Settings → Fine-tune by ear: click loop plus the last take, shifted by a ±150 ms slider. */
@Composable
fun NudgeScreen(state: NudgeUi, actions: NudgeActions, modifier: Modifier = Modifier) {
    ScreenScaffold(
        modifier = modifier,
        bottomBar = {
            if (state.hasTake) {
                IndigoPillButton(stringResource(R.string.nudge_save), actions.onSave, Modifier.fillMaxWidth())
            }
        },
    ) {
        TopBar(backLabel = stringResource(R.string.action_back), onBack = actions.onBack)
        Text(
            stringResource(R.string.nudge_title),
            style = MbTheme.type.h1,
            color = MbColors.Indigo,
            modifier = Modifier.semantics { heading() },
        )
        if (!state.hasTake) {
            EmptyState(title = stringResource(R.string.nudge_title), body = stringResource(R.string.nudge_no_take))
            return@ScreenScaffold
        }
        Text(stringResource(R.string.nudge_body), style = MbTheme.type.body, color = MbColors.InkSoft)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MbDimens.Gap),
        ) {
            IconCircleButton(
                icon = if (state.playing) MbIcons.Stop else MbIcons.Play,
                contentDescription = stringResource(if (state.playing) R.string.action_stop else R.string.action_play),
                onClick = actions.onPlayToggle,
                background = MbColors.Tangerine,
                tint = MbColors.Indigo,
                framed = false,
            )
            MonoLabel(stringResource(R.string.nudge_offset, state.offsetMs), color = MbColors.Ink, bold = true)
        }
        MbSlider(
            value = state.offsetMs.toFloat(),
            onValueChange = { actions.onOffset(it.toInt()) },
            description = stringResource(R.string.nudge_title),
            valueText = stringResource(R.string.nudge_offset, state.offsetMs),
            range = -NUDGE_RANGE_MS.toFloat()..NUDGE_RANGE_MS.toFloat(),
            centered = true,
            fillColor = MbColors.Indigo,
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.nudge_earlier),
                style = MbTheme.type.bodySm,
                color = MbColors.Muted,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.nudge_later), style = MbTheme.type.bodySm, color = MbColors.Muted)
        }
    }
}
