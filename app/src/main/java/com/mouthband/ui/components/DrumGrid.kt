package com.mouthband.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.mouthband.ui.theme.MbColors

private val CellGap = 2.dp
private val RowLabelWidth = 56.dp
private const val STEPS_PER_BEAT = 4
private val PlayheadWidth = 2.dp
private const val CELL_ASPECT = 0.8f

/** One bar of drum steps: [cells][row][step] true when a hit is on. Rows: kick, snare, hi-hat. */
@Immutable
class DrumGridModel(val cells: List<BooleanArray>, val rowLabels: List<String>) {
    val steps: Int get() = cells.firstOrNull()?.size ?: 0
}

/** Labels TalkBack reads for a cell, e.g. "Kick, step 1, on". */
@Immutable
data class GridCellLabels(val stepWord: String, val on: String, val off: String)

/**
 * 3 × 16 drum grid. Hits are Ink on Paper (or Cream on Indigo); every 4th column is slightly
 * darker. When [onToggle] is set, tapping a cell toggles it. [playhead] returns the position in
 * the bar (0..1, < 0 for none) and is drawn as a moving line.
 */
@Composable
fun DrumGrid(
    model: DrumGridModel,
    labels: GridCellLabels,
    modifier: Modifier = Modifier,
    onIndigo: Boolean = false,
    playhead: () -> Float = NoPlayhead,
    onToggle: ((row: Int, step: Int) -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                // Read while drawing: the moving playhead only redraws, it never recomposes cells.
                val position = playhead()
                if (position >= 0f) {
                    val left = RowLabelWidth.toPx()
                    val x = left + position.coerceIn(0f, 1f) * (size.width - left)
                    drawLine(MbColors.Tangerine, Offset(x, 0f), Offset(x, size.height), PlayheadWidth.toPx())
                }
            },
        verticalArrangement = Arrangement.spacedBy(CellGap),
    ) {
        model.cells.forEachIndexed { row, steps ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonoLabel(
                    text = model.rowLabels.getOrElse(row) { "" },
                    color = if (onIndigo) MbColors.OnIndigoMuted else MbColors.Muted,
                    small = true,
                    modifier = Modifier.width(RowLabelWidth).padding(end = CellGap),
                )
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                    steps.forEachIndexed { step, on ->
                        GridCell(
                            on = on,
                            beatColumn = step % STEPS_PER_BEAT == 0,
                            onIndigo = onIndigo,
                            label = "${model.rowLabels.getOrElse(row) { "" }}, ${labels.stepWord} ${step + 1}",
                            state = if (on) labels.on else labels.off,
                            onClick = onToggle?.let { toggle -> { toggle(row, step) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridCell(
    on: Boolean,
    beatColumn: Boolean,
    onIndigo: Boolean,
    label: String,
    state: String,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val empty = if (onIndigo) MbColors.IndigoLine else MbColors.Cream
    val hit = if (onIndigo) MbColors.Cream else MbColors.Ink
    val overlay = if (onIndigo) MbColors.GridBeatOverlayIndigo else MbColors.GridBeatOverlayPaper
    var cell = modifier
        .aspectRatio(CELL_ASPECT)
        .background(if (on) hit else empty)
    if (!on && beatColumn) cell = cell.background(overlay)
    if (onClick != null) cell = cell.clickable(role = Role.Checkbox, onClick = onClick)
    Box(
        cell.semantics {
            contentDescription = label
            stateDescription = state
        },
    )
}
