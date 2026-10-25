package com.mouthband.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens

private const val STEPS_PER_BEAT = 4
private val SelectionStroke = 2.dp
private val NoteInset = 1.dp

/** A note in grid units: [row] indexes [RollModel.rowPitches], [startStep] and [lengthSteps] are 16ths. */
@Immutable
data class RollNote(val row: Int, val startStep: Int, val lengthSteps: Int, val id: Int)

/** One bar (or more) of a pitched track, top row = highest pitch. */
@Immutable
class RollModel(
    val rowPitches: IntArray,
    val steps: Int,
    val notes: List<RollNote>,
    val noteDescriptions: List<String>,
)

/**
 * Mini piano roll: rows are the key's scale tones, columns are 16ths. Tap a note to select it
 * (notes in [selectedNoteIds] are outlined), tap an empty cell to add a one-16th note. TalkBack users get one
 * "Delete note" action per note. [playhead] returns 0..1 in the bar (< 0 for none).
 */
@Composable
fun MiniPianoRoll(
    model: RollModel,
    color: Color,
    description: String,
    deleteActionLabel: String,
    modifier: Modifier = Modifier,
    onIndigo: Boolean = false,
    height: Dp = 168.dp,
    playhead: () -> Float = NoPlayhead,
    selectedNoteIds: Set<Int> = emptySet(),
    onDeleteNote: ((noteId: Int) -> Unit)? = null,
    onTapNote: ((noteId: Int) -> Unit)? = null,
    onTapCell: ((row: Int, step: Int) -> Unit)? = null,
) {
    val background = if (onIndigo) MbColors.Indigo else MbColors.Paper
    val stripe = if (onIndigo) MbColors.IndigoLine else MbColors.Cream
    val gridLine = if (onIndigo) MbColors.IndigoLine else MbColors.Rule
    val actions = rollActions(model, deleteActionLabel, onDeleteNote)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(background)
            .border(MbDimens.Border, if (onIndigo) MbColors.IndigoLine else MbColors.Ink)
            .semantics {
                contentDescription = description
                if (actions.isNotEmpty()) customActions = actions
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(height).rollTap(model, onTapNote, onTapCell)) {
            drawRoll(model, RollColors(color, stripe, gridLine), selectedNoteIds, playhead())
        }
    }
}

private fun rollActions(
    model: RollModel,
    deleteActionLabel: String,
    onDeleteNote: ((Int) -> Unit)?,
): List<CustomAccessibilityAction> {
    if (onDeleteNote == null) return emptyList()
    return model.notes.mapIndexed { index, note ->
        CustomAccessibilityAction("$deleteActionLabel: ${model.noteDescriptions.getOrElse(index) { "" }}") {
            onDeleteNote(note.id)
            true
        }
    }
}

private fun Modifier.rollTap(
    model: RollModel,
    onTapNote: ((Int) -> Unit)?,
    onTapCell: ((Int, Int) -> Unit)?,
): Modifier {
    if (onTapNote == null && onTapCell == null) return this
    return pointerInput(model) {
        detectTapGestures { offset ->
            val rows = model.rowPitches.size.coerceAtLeast(1)
            val row = (offset.y / (size.height.toFloat() / rows)).toInt().coerceIn(0, rows - 1)
            val step = (offset.x / (size.width.toFloat() / model.steps)).toInt().coerceIn(0, model.steps - 1)
            val hit = model.notes.firstOrNull {
                it.row == row && step in it.startStep until it.startStep + it.lengthSteps
            }
            if (hit != null) onTapNote?.invoke(hit.id) else onTapCell?.invoke(row, step)
        }
    }
}

private class RollColors(val note: Color, val stripe: Color, val gridLine: Color)

private fun DrawScope.drawRoll(model: RollModel, colors: RollColors, selected: Set<Int>, playhead: Float) {
    val rows = model.rowPitches.size.coerceAtLeast(1)
    val rowH = size.height / rows
    val stepW = size.width / model.steps.coerceAtLeast(1)
    for (r in 1 until rows step 2) {
        drawRect(colors.stripe, Offset(0f, r * rowH), Size(size.width, rowH))
    }
    for (s in 0..model.steps step STEPS_PER_BEAT) {
        val x = s * stepW
        drawLine(colors.gridLine, Offset(x, 0f), Offset(x, size.height))
    }
    val inset = NoteInset.toPx()
    model.notes.forEach { note ->
        val topLeft = Offset(note.startStep * stepW + inset, note.row * rowH + inset)
        val noteSize = Size(
            (note.lengthSteps * stepW - 2 * inset).coerceAtLeast(1f),
            (rowH - 2 * inset).coerceAtLeast(1f),
        )
        drawRect(color = colors.note, topLeft = topLeft, size = noteSize)
        if (note.id in selected) {
            drawRect(MbColors.Ink, topLeft, noteSize, style = Stroke(SelectionStroke.toPx()))
        }
    }
    if (playhead >= 0f) {
        val x = playhead.coerceIn(0f, 1f) * size.width
        drawLine(MbColors.Tangerine, Offset(x, 0f), Offset(x, size.height), strokeWidth = inset * 2)
    }
}
