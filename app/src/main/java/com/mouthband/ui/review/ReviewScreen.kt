package com.mouthband.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mouthband.R
import com.mouthband.ui.common.ScreenScaffold
import com.mouthband.ui.components.ChipItem
import com.mouthband.ui.components.ChipRow
import com.mouthband.ui.components.EmptyState
import com.mouthband.ui.components.HighlighterTag
import com.mouthband.ui.components.IconCircleButton
import com.mouthband.ui.components.MbSlider
import com.mouthband.ui.components.MonoLabel
import com.mouthband.ui.components.NoPlayhead
import com.mouthband.ui.components.PrimaryPillButton
import com.mouthband.ui.components.SecondaryPillButton
import com.mouthband.ui.components.SegmentedToggle
import com.mouthband.ui.components.TopBar
import com.mouthband.ui.icons.MbIcons
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbTheme

private const val PERCENT = 100

/** Why a take produced nothing usable. */
enum class ReviewProblem { SILENCE, NO_HITS, NO_PITCH }

@Immutable
data class ReviewUi(
    val trackLabel: String,
    val stepNumber: Int,
    val trackColor: Color,
    val trackOnColor: Color,
    val showAb: Boolean,
    val abConverted: Boolean,
    val playing: Boolean,
    val pattern: ReviewPattern,
    val bars: Int,
    val selectedBar: Int,
    val sounds: List<ChipItem>,
    val selectedSound: String?,
    val quantize: Float,
    val nextLabel: String,
    val problem: ReviewProblem?,
    /** Notes outlined in the roll (the selected note, or every note of the selected chord). */
    val selectedNotes: Set<Int> = emptySet(),
    /** "E4 selected" / "Chord C · E · G selected"; null when nothing is selected. */
    val selection: String? = null,
    val canUndo: Boolean = false,
    /** Editing a take that was kept earlier: "Save changes" instead of "Keep it · next". */
    val editing: Boolean = false,
)

@Immutable
data class ReviewActions(
    val onBack: () -> Unit,
    val onAb: (converted: Boolean) -> Unit,
    val onPlayToggle: () -> Unit,
    val onBar: (Int) -> Unit,
    val onToggleHit: (row: Int, step: Int) -> Unit,
    val onTapNote: (Int) -> Unit,
    val onTapRollCell: (row: Int, step: Int) -> Unit,
    val onDeleteNote: (Int) -> Unit = {},
    val onDeleteSelected: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onSound: (String) -> Unit,
    val onQuantize: (Float) -> Unit,
    val onQuantizeDone: () -> Unit,
    val onTryAgain: () -> Unit,
    val onKeep: () -> Unit,
)

/** Review after every take: A/B, pattern editing, sound picker and timing. */
@Composable
fun ReviewScreen(
    state: ReviewUi,
    actions: ReviewActions,
    modifier: Modifier = Modifier,
    playhead: () -> Float = NoPlayhead,
) {
    ScreenScaffold(
        modifier = modifier,
        bottomBar = {
            if (state.problem == null) {
                PrimaryPillButton(
                    text = if (state.editing) {
                        stringResource(R.string.review_save_changes)
                    } else {
                        stringResource(R.string.review_keep_next, state.nextLabel)
                    },
                    onClick = actions.onKeep,
                    modifier = Modifier.fillMaxWidth(),
                    showArrow = true,
                )
            }
            SecondaryPillButton(
                text = stringResource(if (state.editing) R.string.review_record_again else R.string.review_try_again),
                onClick = actions.onTryAgain,
                modifier = Modifier.fillMaxWidth(),
                icon = MbIcons.Retry,
            )
        },
    ) {
        TopBar(backLabel = stringResource(R.string.action_back), onBack = actions.onBack)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MbDimens.GapSmall),
        ) {
            HighlighterTag(stringResource(R.string.step_tag, state.stepNumber))
            MonoLabel(state.trackLabel, color = MbColors.Ink, bold = true)
        }
        Text(
            stringResource(R.string.review_title),
            style = MbTheme.type.h1,
            color = MbColors.Indigo,
            modifier = Modifier.semantics { heading() },
        )
        val problem = state.problem
        if (problem != null) {
            ProblemCard(problem, actions.onTryAgain)
            return@ScreenScaffold
        }
        PlaybackRow(state, actions)
        MonoLabel(stringResource(R.string.review_pattern))
        ReviewPatternView(state, actions, playhead)
        if (state.sounds.isNotEmpty()) {
            MonoLabel(stringResource(R.string.review_sound))
            ChipRow(
                items = state.sounds,
                selectedId = state.selectedSound,
                onSelect = actions.onSound,
                selectedFill = state.trackColor,
                selectedText = state.trackOnColor,
            )
        }
        if (state.pattern !is ReviewPattern.Vocal) TimingSlider(state, actions)
    }
}

@Composable
private fun PlaybackRow(state: ReviewUi, actions: ReviewActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MbDimens.Gap)) {
        IconCircleButton(
            icon = if (state.playing) MbIcons.Pause else MbIcons.Play,
            contentDescription = stringResource(if (state.playing) R.string.action_pause else R.string.action_play),
            onClick = actions.onPlayToggle,
            background = MbColors.Tangerine,
            tint = MbColors.Indigo,
            framed = false,
        )
        if (state.showAb) {
            SegmentedToggle(
                options = listOf(
                    stringResource(R.string.review_ab_voice),
                    stringResource(R.string.review_ab_converted),
                ),
                selectedIndex = if (state.abConverted) 1 else 0,
                onSelect = { actions.onAb(it == 1) },
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                stringResource(R.string.review_vocal_hint),
                style = MbTheme.type.bodySm,
                color = MbColors.InkSoft,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TimingSlider(state: ReviewUi, actions: ReviewActions) {
    MonoLabel(stringResource(R.string.review_timing))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MbDimens.GapSmall),
    ) {
        Text(stringResource(R.string.review_human), style = MbTheme.type.bodySm, color = MbColors.Muted)
        MbSlider(
            value = state.quantize,
            onValueChange = actions.onQuantize,
            onValueChangeFinished = actions.onQuantizeDone,
            description = stringResource(R.string.review_timing),
            valueText = stringResource(R.string.review_timing_value, (state.quantize * PERCENT).toInt()),
            modifier = Modifier.weight(1f),
            fillColor = MbColors.Indigo,
        )
        Text(stringResource(R.string.review_tight), style = MbTheme.type.bodySm, color = MbColors.Muted)
    }
}

@Composable
private fun ProblemCard(problem: ReviewProblem, onTryAgain: () -> Unit) {
    val (title, body) = when (problem) {
        ReviewProblem.SILENCE -> R.string.review_silence_title to R.string.review_silence_body
        ReviewProblem.NO_HITS -> R.string.review_no_hits_title to R.string.review_no_hits_body
        ReviewProblem.NO_PITCH -> R.string.review_no_pitch_title to R.string.review_no_pitch_body
    }
    EmptyState(
        title = stringResource(title),
        body = stringResource(body),
        icon = MbIcons.Mic,
        actionLabel = stringResource(R.string.review_try_again),
        onAction = onTryAgain,
    )
}
