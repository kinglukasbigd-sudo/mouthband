package com.mouthband.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mouthband.audio.LiveTake
import com.mouthband.ui.common.rememberFrameTick
import com.mouthband.ui.theme.MbColors
import kotlin.math.ceil

private val LaneRowLabelWidth = 56.dp
private val LaneRowGap = 2.dp
private val PlayheadStroke = 2.dp
private val PitchStroke = 3.dp
private val HitCorner = 2.dp
private const val STEPS_PER_BAR = 16
private const val STEPS_PER_BEAT = 4
private const val HIT_WIDTH_STEPS = 0.7f
private const val HIT_POP_NANOS = 160_000_000L
private const val POP_START_SCALE = 0.4f
private const val PITCH_MARGIN = 2f
private const val PITCH_MIN_SPAN = 12f
private const val DEFAULT_PITCH = 60f
private const val RANGE_EASE = 0.12f
private const val WAVE_BAR_SHARE = 0.75f

/**
 * Live waveform of the take across the whole loop: each column fills in at the place where it was
 * heard while the playhead sweeps left to right. Redrawn every display frame, never recomposed.
 * [playheadFrame] returns the loop-relative frame being heard (negative during the count-in).
 */
@Composable
fun LiveWaveform(
    take: LiveTake?,
    playheadFrame: () -> Long,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = MbColors.Tangerine,
    height: Dp = 72.dp,
) {
    val tick = rememberFrameTick()
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        tick.value
        val mid = size.height / 2f
        drawLine(MbColors.IndigoLine, Offset(0f, mid), Offset(size.width, mid))
        val t = take ?: return@Canvas
        val colW = size.width / t.columns
        val stroke = (colW * WAVE_BAR_SHARE).coerceAtLeast(1f)
        val scale = t.displayScale()
        for (c in 0 until t.columns) {
            val peak = t.peaks[c]
            if (peak <= 0f) continue
            val x = c * colW + colW / 2f
            val half = (peak * scale).coerceAtMost(1f).times(mid).coerceAtLeast(1f)
            drawLine(color, Offset(x, mid - half), Offset(x, mid + half), strokeWidth = stroke)
        }
        drawPlayhead(playheadFrame(), t.loopFrames)
    }
}

/**
 * Live drum lane: kick, snare and hi-hat rows over the loop's 16th grid. Every hit appears at the
 * exact moment it was heard (not snapped) with a short pop, while the playhead sweeps along.
 */
@Composable
fun LiveDrumLane(
    take: LiveTake?,
    rowLabels: List<String>,
    loopBars: Int,
    playheadFrame: () -> Long,
    description: String,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 28.dp,
) {
    val tick = rememberFrameTick()
    // Frame time at which each hit was first drawn, for its pop-in.
    val firstSeen = remember { LongArray(LiveTake.MAX_HITS) }
    val rows = rowLabels.size.coerceAtLeast(1)
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        Column(Modifier.width(LaneRowLabelWidth), verticalArrangement = Arrangement.spacedBy(LaneRowGap)) {
            rowLabels.forEach { label ->
                Box(Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) {
                    MonoLabel(label, color = MbColors.OnIndigoMuted, small = true)
                }
            }
        }
        Canvas(Modifier.weight(1f).height(rowHeight * rows + LaneRowGap * (rows - 1))) {
            val now = tick.value
            val rowH = rowHeight.toPx()
            val gap = LaneRowGap.toPx()
            val steps = loopBars * STEPS_PER_BAR
            drawDrumGrid(rows, rowH, gap, steps)
            val t = take ?: return@Canvas
            val stepW = size.width / steps
            for (i in 0 until t.hitCount.coerceAtMost(LiveTake.MAX_HITS)) {
                if (firstSeen[i] == 0L) firstSeen[i] = now
                val age = (now - firstSeen[i]).coerceAtLeast(0L)
                val pop = if (age >= HIT_POP_NANOS) {
                    1f
                } else {
                    POP_START_SCALE + (1f - POP_START_SCALE) * age / HIT_POP_NANOS
                }
                val row = t.hitClass(i).coerceIn(0, rows - 1)
                val x = t.hitFrame(i).toFloat() / t.loopFrames * size.width
                val w = stepW * HIT_WIDTH_STEPS * pop
                val h = rowH * pop
                val top = row * (rowH + gap) + (rowH - h) / 2f
                drawRoundRect(
                    MbColors.Cream,
                    Offset(x - w / 2f, top),
                    Size(w, h),
                    CornerRadius(HitCorner.toPx()),
                )
            }
            drawPlayhead(playheadFrame(), t.loopFrames)
        }
    }
}

/**
 * Live note lane for pitched takes: the hummed pitch drawn as a continuous line at the time it was
 * heard, on semitone lines whose range follows the voice smoothly.
 */
@Composable
fun LivePitchLane(
    take: LiveTake?,
    loopBars: Int,
    playheadFrame: () -> Long,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
) {
    val tick = rememberFrameTick()
    // Displayed MIDI range [low, high], eased towards the take's range every frame.
    val range = remember { floatArrayOf(DEFAULT_PITCH - PITCH_MIN_SPAN / 2, DEFAULT_PITCH + PITCH_MIN_SPAN / 2) }
    val path = remember { Path() }
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        tick.value
        val t = take
        if (t != null) easeRange(t.pitch, range)
        val span = range[1] - range[0]
        val semitone = size.height / span
        val firstLine = ceil(range[0]).toInt()
        for (m in firstLine..range[1].toInt()) {
            val y = size.height - (m - range[0]) * semitone
            drawLine(MbColors.IndigoLine, Offset(0f, y), Offset(size.width, y))
        }
        val barW = size.width / loopBars.coerceAtLeast(1)
        for (b in 1 until loopBars) {
            drawLine(MbColors.OnIndigoMuted, Offset(b * barW, 0f), Offset(b * barW, size.height))
        }
        if (t == null) return@Canvas
        path.reset()
        val colW = size.width / t.columns
        var drawing = false
        for (c in 0 until t.columns) {
            val midi = t.pitch[c]
            if (midi.isNaN()) {
                drawing = false
                continue
            }
            val x = c * colW + colW / 2f
            val y = size.height - (midi - range[0]).coerceIn(0f, span) * semitone
            if (drawing) path.lineTo(x, y) else path.moveTo(x, y)
            drawing = true
        }
        drawPath(
            path,
            MbColors.Tangerine,
            style = Stroke(PitchStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPlayhead(playheadFrame(), t.loopFrames)
    }
}

private fun DrawScope.drawDrumGrid(rows: Int, rowH: Float, gap: Float, steps: Int) {
    val stepW = size.width / steps
    for (r in 0 until rows) {
        val top = r * (rowH + gap)
        drawRect(MbColors.IndigoLine, Offset(0f, top), Size(size.width, rowH))
        for (s in 0 until steps step STEPS_PER_BEAT) {
            drawRect(MbColors.GridBeatOverlayIndigo, Offset(s * stepW, top), Size(stepW, rowH))
        }
    }
    for (s in STEPS_PER_BAR until steps step STEPS_PER_BAR) {
        drawLine(MbColors.OnIndigoMuted, Offset(s * stepW, 0f), Offset(s * stepW, size.height))
    }
}

private fun DrawScope.drawPlayhead(frame: Long, loopFrames: Int) {
    if (frame < 0 || loopFrames <= 0) return
    val x = (frame.toFloat() / loopFrames).coerceIn(0f, 1f) * size.width
    drawLine(MbColors.Tangerine, Offset(x, 0f), Offset(x, size.height), strokeWidth = PlayheadStroke.toPx())
}

/** Eases [range] towards the voiced pitches (plus a margin, at least an octave). */
private fun easeRange(pitch: FloatArray, range: FloatArray) {
    var low = Float.MAX_VALUE
    var high = -Float.MAX_VALUE
    for (p in pitch) {
        if (p.isNaN()) continue
        if (p < low) low = p
        if (p > high) high = p
    }
    if (low > high) return
    var targetLow = low - PITCH_MARGIN
    var targetHigh = high + PITCH_MARGIN
    if (targetHigh - targetLow < PITCH_MIN_SPAN) {
        val centre = (targetLow + targetHigh) / 2f
        targetLow = centre - PITCH_MIN_SPAN / 2f
        targetHigh = centre + PITCH_MIN_SPAN / 2f
    }
    range[0] += (targetLow - range[0]) * RANGE_EASE
    range[1] += (targetHigh - range[1]) * RANGE_EASE
}
