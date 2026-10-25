package com.mouthband.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens

private val TrackThickness = 4.dp
private val ThumbRadius = 11.dp

/**
 * Thin-line slider: Rule track, Ink fill from the left (or from the center when [centered]),
 * Paper thumb with an Ink frame. Height is a 48 dp touch target.
 */
@Composable
fun MbSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    description: String,
    valueText: String,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    centered: Boolean = false,
    fillColor: Color = MbColors.Ink,
    onIndigo: Boolean = false,
    onValueChangeFinished: () -> Unit = {},
) {
    val latestChange by rememberUpdatedState(onValueChange)
    val latestFinished by rememberUpdatedState(onValueChangeFinished)
    val span = range.endInclusive - range.start
    fun fromX(x: Float, width: Float): Float = range.start + (x / width).coerceIn(0f, 1f) * span
    val trackColor = if (onIndigo) MbColors.IndigoLine else MbColors.Rule
    val thumbFrame = if (onIndigo) MbColors.Cream else MbColors.Ink
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(MbDimens.TouchTarget)
            .semantics {
                contentDescription = description
                stateDescription = valueText
                progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                setProgress { target ->
                    latestChange(target.coerceIn(range.start, range.endInclusive))
                    latestFinished()
                    true
                }
            }
            .pointerInput(range) {
                detectTapGestures { offset ->
                    latestChange(fromX(offset.x, size.width.toFloat()))
                    latestFinished()
                }
            }
            .pointerInput(range) {
                detectHorizontalDragGestures(
                    onDragEnd = { latestFinished() },
                    onHorizontalDrag = { change, _ -> latestChange(fromX(change.position.x, size.width.toFloat())) },
                )
            },
    ) {
        val radius = ThumbRadius.toPx()
        val left = radius
        val right = size.width - radius
        val y = size.height / 2f
        val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
        val thumbX = left + fraction * (right - left)
        val stroke = TrackThickness.toPx()
        drawLine(trackColor, Offset(left, y), Offset(right, y), strokeWidth = stroke, cap = StrokeCap.Round)
        val fillStart = if (centered) (left + right) / 2f else left
        drawLine(fillColor, Offset(fillStart, y), Offset(thumbX, y), strokeWidth = stroke, cap = StrokeCap.Round)
        drawCircle(if (onIndigo) MbColors.Indigo else MbColors.Paper, radius, Offset(thumbX, y))
        drawCircle(thumbFrame, radius, Offset(thumbX, y), style = Stroke(MbDimens.Border.toPx()))
    }
}
