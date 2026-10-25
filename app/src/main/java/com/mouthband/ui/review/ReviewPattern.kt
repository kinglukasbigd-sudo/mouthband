package com.mouthband.ui.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.mouthband.R
import com.mouthband.ui.components.DrumGrid
import com.mouthband.ui.components.DrumGridModel
import com.mouthband.ui.components.GridCellLabels
import com.mouthband.ui.components.MiniPianoRoll
import com.mouthband.ui.components.RollModel
import com.mouthband.ui.components.SegmentedToggle
import com.mouthband.ui.components.TextLinkButton
import com.mouthband.ui.components.Waveform
import com.mouthband.ui.components.motionTween
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbTheme

private const val EDIT_BAR_MS = 180

/** What the Review screen shows as the take's pattern. */
@Immutable
sealed interface ReviewPattern {
    /** One grid per bar. */
    data class Drums(val bars: List<DrumGridModel>) : ReviewPattern

    /** One roll per bar. */
    data class Notes(val bars: List<RollModel>) : ReviewPattern

    /** Vocals keep only audio: show the take's waveform. */
    data class Vocal(val peaks: FloatArray) : ReviewPattern
}

/**
 * The take's pattern for the selected bar. [playhead] returns the position in the whole loop
 * (0..1, < 0 when stopped); it is read while drawing, so playback never recomposes the screen.
 */
@Composable
internal fun ReviewPatternView(state: ReviewUi, actions: ReviewActions, playhead: () -> Float) {
    val bars = state.bars
    val bar = state.selectedBar
    val inBar: () -> Float = {
        val p = playhead()
        val position = p * bars - bar
        if (p < 0f || position < 0f || position >= 1f) -1f else position
    }
    if (state.bars > 1 && state.pattern !is ReviewPattern.Vocal) {
        SegmentedToggle(
            options = (1..state.bars).map { stringResource(R.string.bar_label, it) },
            selectedIndex = state.selectedBar,
            onSelect = actions.onBar,
        )
    }
    when (val pattern = state.pattern) {
        is ReviewPattern.Drums -> {
            val grid = pattern.bars.getOrNull(state.selectedBar) ?: return
            DrumGrid(
                model = grid,
                labels = GridCellLabels(
                    stringResource(R.string.grid_step),
                    stringResource(R.string.grid_on),
                    stringResource(R.string.grid_off),
                ),
                playhead = inBar,
                onToggle = actions.onToggleHit,
            )
            Text(stringResource(R.string.review_edit_hint), style = MbTheme.type.bodySm, color = MbColors.Muted)
            EditBar(selection = null, canUndo = state.canUndo, deleteLabel = null, actions = actions)
        }
        is ReviewPattern.Notes -> {
            val roll = pattern.bars.getOrNull(state.selectedBar) ?: return
            MiniPianoRoll(
                model = roll,
                color = state.trackColor,
                description = pluralStringResource(R.plurals.roll_description, roll.notes.size, roll.notes.size),
                deleteActionLabel = stringResource(R.string.roll_delete_note),
                playhead = inBar,
                selectedNoteIds = state.selectedNotes,
                onDeleteNote = actions.onDeleteNote,
                onTapNote = actions.onTapNote,
                onTapCell = actions.onTapRollCell,
            )
            Text(stringResource(R.string.review_edit_hint_notes), style = MbTheme.type.bodySm, color = MbColors.Muted)
            val deleteLabel = stringResource(
                if (state.selectedNotes.size > 1) R.string.review_delete_chord else R.string.review_delete_note,
            )
            EditBar(state.selection, state.canUndo, deleteLabel, actions)
        }
        is ReviewPattern.Vocal -> Waveform(
            peaks = { pattern.peaks },
            description = stringResource(R.string.rec_waveform),
            color = state.trackColor,
            playhead = playhead,
        )
    }
}

/**
 * Keep or delete what was converted: shows the selected note or chord with a Delete button, and
 * Undo whenever an edit can be taken back. Slides in and out with the selection.
 */
@Composable
private fun EditBar(selection: String?, canUndo: Boolean, deleteLabel: String?, actions: ReviewActions) {
    val visible = selection != null || canUndo
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motionTween(EDIT_BAR_MS)) + expandVertically(motionTween(EDIT_BAR_MS)),
        exit = fadeOut(motionTween(EDIT_BAR_MS)) + shrinkVertically(motionTween(EDIT_BAR_MS)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MbDimens.Gap),
        ) {
            Text(
                selection.orEmpty(),
                style = MbTheme.type.label,
                color = MbColors.Ink,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (selection != null && deleteLabel != null) {
                TextLinkButton(deleteLabel, actions.onDeleteSelected)
            }
            if (canUndo) {
                TextLinkButton(
                    stringResource(R.string.review_undo),
                    actions.onUndo,
                    underline = false,
                    color = MbColors.Indigo,
                )
            }
        }
    }
}
