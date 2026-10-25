package com.mouthband.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.mouthband.R
import com.mouthband.ui.common.ScreenScaffold
import com.mouthband.ui.components.EmptyState
import com.mouthband.ui.components.HighlighterTag
import com.mouthband.ui.components.IconCircleButton
import com.mouthband.ui.components.IndigoPillButton
import com.mouthband.ui.components.MonoLabel
import com.mouthband.ui.components.PrimaryPillButton
import com.mouthband.ui.components.ProgressRing
import com.mouthband.ui.components.SecondaryPillButton
import com.mouthband.ui.components.SegmentedToggle
import com.mouthband.ui.components.TextLinkButton
import com.mouthband.ui.components.TopBar
import com.mouthband.ui.icons.MbIcons
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbTheme

private const val STEP_NUMBER = 8
private const val PERCENT = 100

@Immutable
sealed interface ExportPhase {
    data class Working(val progress: Float, val encoding: Boolean) : ExportPhase

    data class Done(val duration: String) : ExportPhase

    data object Failed : ExportPhase

    data object NothingToExport : ExportPhase
}

@Immutable
data class ExportUi(
    val phase: ExportPhase = ExportPhase.Working(0f, encoding = false),
    val m4aSelected: Boolean = true,
    val playing: Boolean = false,
    val message: String? = null,
)

@Immutable
data class ExportActions(
    val onBack: () -> Unit,
    val onCancel: () -> Unit,
    val onPlayToggle: () -> Unit,
    val onFormat: (m4a: Boolean) -> Unit,
    val onSave: () -> Unit,
    val onShare: () -> Unit,
    val onRetry: () -> Unit,
    val onHome: () -> Unit,
)

/** Coach step 8: render progress ring, then play / save / share. */
@Composable
fun ExportScreen(state: ExportUi, actions: ExportActions, modifier: Modifier = Modifier) {
    ScreenScaffold(modifier = modifier, bottomBar = { ExportButtons(state, actions) }) {
        TopBar(backLabel = stringResource(R.string.action_back), onBack = actions.onBack)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MbDimens.GapSmall),
        ) {
            HighlighterTag(stringResource(R.string.step_tag, STEP_NUMBER))
            MonoLabel(stringResource(R.string.step_name_export), color = MbColors.Ink, bold = true)
        }
        Text(
            stringResource(R.string.export_title),
            style = MbTheme.type.h1,
            color = MbColors.Indigo,
            modifier = Modifier.semantics { heading() },
        )
        when (val phase = state.phase) {
            is ExportPhase.Working -> {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ProgressRing(progress = phase.progress, label = "${(phase.progress * PERCENT).toInt()}%")
                }
                Text(
                    stringResource(if (phase.encoding) R.string.export_encoding else R.string.export_rendering),
                    style = MbTheme.type.label,
                    color = MbColors.Ink,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            is ExportPhase.Done -> {
                Text(stringResource(R.string.export_done), style = MbTheme.type.h2, color = MbColors.Indigo)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MbDimens.Gap),
                ) {
                    IconCircleButton(
                        icon = if (state.playing) MbIcons.Pause else MbIcons.Play,
                        contentDescription = stringResource(
                            if (state.playing) R.string.action_pause else R.string.action_play,
                        ),
                        onClick = actions.onPlayToggle,
                        background = MbColors.Tangerine,
                        tint = MbColors.Indigo,
                        framed = false,
                    )
                    MonoLabel(
                        stringResource(R.string.export_duration, phase.duration),
                        color = MbColors.Ink,
                        bold = true,
                    )
                }
                SegmentedToggle(
                    options = listOf(
                        stringResource(R.string.export_format_m4a),
                        stringResource(R.string.export_format_wav),
                    ),
                    selectedIndex = if (state.m4aSelected) 0 else 1,
                    onSelect = { actions.onFormat(it == 0) },
                )
            }
            ExportPhase.Failed -> EmptyState(
                title = stringResource(R.string.export_failed_title),
                body = stringResource(R.string.export_failed_body),
                actionLabel = stringResource(R.string.action_retry),
                onAction = actions.onRetry,
            )
            ExportPhase.NothingToExport -> EmptyState(
                title = stringResource(R.string.export_nothing_title),
                body = stringResource(R.string.export_nothing_body),
            )
        }
        state.message?.let {
            Text(
                it,
                style = MbTheme.type.label,
                color = MbColors.Indigo,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun ColumnScope.ExportButtons(state: ExportUi, actions: ExportActions) {
    when (state.phase) {
        is ExportPhase.Working -> SecondaryPillButton(
            stringResource(R.string.export_cancel),
            actions.onCancel,
            Modifier.fillMaxWidth(),
        )
        is ExportPhase.Done -> {
            PrimaryPillButton(
                stringResource(R.string.export_save),
                actions.onSave,
                Modifier.fillMaxWidth(),
                showArrow = true,
            )
            IndigoPillButton(
                stringResource(R.string.export_share),
                actions.onShare,
                Modifier.fillMaxWidth(),
                icon = MbIcons.Share,
            )
            TextLinkButton(
                stringResource(R.string.export_back_home),
                actions.onHome,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                underline = false,
                color = MbColors.Muted,
            )
        }
        ExportPhase.Failed, ExportPhase.NothingToExport ->
            SecondaryPillButton(stringResource(R.string.export_back_home), actions.onHome, Modifier.fillMaxWidth())
    }
}
