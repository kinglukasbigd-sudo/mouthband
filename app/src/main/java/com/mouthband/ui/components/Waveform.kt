package com.mouthband.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mouthband.ui.theme.MbColors

private val BarWidth = 2.dp
private val MinBar = 1.dp

/** "No playhead": the default for [Waveform], [DrumGrid] and [MiniPianoRoll]. */
val NoPlayhead: () -> Float = { -1f }

/**
 * Mirrored peak waveform. [peaks] returns 0..1 peak values, oldest first; each one is drawn as a
 * vertical bar. [playhead] returns 0..1 (or < 0 for none) and draws a thin line. Both are read
 * while drawing, so fast-changing values only redraw this element.
 */
@Composable
fun Waveform(
    peaks: () -> FloatArray,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = MbColors.Tangerine,
    height: Dp = 72.dp,
    playhead: () -> Float = NoPlayhead,
    playheadColor: Color = MbColors.Ink,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description },
    ) {
        val mid = size.height / 2f
        val values = peaks()
        val count = values.size
        if (count > 0) {
            val step = size.width / count
            val stroke = minOf(BarWidth.toPx(), step * 0.7f).coerceAtLeast(1f)
            for (i in 0 until count) {
                val half = (values[i].coerceIn(0f, 1f) * mid).coerceAtLeast(MinBar.toPx())
                val x = i * step + step / 2f
                drawLine(
                    color,
                    Offset(x, mid - half),
                    Offset(x, mid + half),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
        val position = playhead()
        if (position >= 0f) {
            val x = position.coerceIn(0f, 1f) * size.width
            drawLine(playheadColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = MinBar.toPx() * 2)
        }
    }
}
