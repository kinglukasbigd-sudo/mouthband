package com.mouthband.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mouthband.ui.theme.LocalReduceMotion
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens

private const val METER_SEGMENTS = 20
private val MeterHeight = 14.dp
private val MeterGap = 2.dp
private val BeatBoxSize = 44.dp
private const val BEATS = 4

/** Full scale per second the meter falls after a peak (a quick rise, a calm fall). */
private const val METER_FALL_PER_SECOND = 1.6f
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val BEAT_FADE_MS = 150

/** Coarse mic level verdict shown next to the meter. */
enum class LevelStatus { TOO_QUIET, GOOD, TOO_LOUD }

/**
 * 20-segment level meter. [level] returns 0..1 (already mapped from dBFS); it is sampled once
 * per display frame and drawn without recomposition, rising instantly and falling smoothly.
 * Lit segments use [litColor]; the loudest quarter turns Tangerine to warn about clipping.
 */
@Composable
fun LevelMeter(
    level: () -> Float,
    description: String,
    modifier: Modifier = Modifier,
    onIndigo: Boolean = false,
    litColor: Color = if (onIndigo) MbColors.OnIndigo else MbColors.Indigo,
) {
    val shown = remember { mutableFloatStateOf(0f) }
    val target by rememberUpdatedState(level)
    val instant = LocalReduceMotion.current
    LaunchedEffect(instant) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val raw = target().coerceIn(0f, 1f)
                val dt = if (last == 0L) 0f else (now - last) / NANOS_PER_SECOND
                last = now
                val current = shown.floatValue
                shown.floatValue = if (instant || raw >= current) {
                    raw
                } else {
                    maxOf(raw, current - METER_FALL_PER_SECOND * dt)
                }
            }
        }
    }
    val hotStart = METER_SEGMENTS - METER_SEGMENTS / BEATS
    val emptyColor = if (onIndigo) MbColors.IndigoLine else MbColors.Rule
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(MeterHeight)
            .semantics { contentDescription = description },
    ) {
        val gap = MeterGap.toPx()
        val segment = (size.width - gap * (METER_SEGMENTS - 1)) / METER_SEGMENTS
        val lit = (shown.floatValue * METER_SEGMENTS).toInt()
        for (index in 0 until METER_SEGMENTS) {
            val color = when {
                index >= lit -> emptyColor
                index >= hotStart -> MbColors.Tangerine
                else -> litColor
            }
            drawRect(color, Offset(index * (segment + gap), 0f), Size(segment, size.height))
        }
    }
}

/** 4 framed boxes; the current beat (0..3) is filled Tangerine. -1 shows none. */
@Composable
fun BeatCounter(
    currentBeat: Int,
    description: String,
    modifier: Modifier = Modifier,
    onIndigo: Boolean = true,
) {
    val frame = if (onIndigo) MbColors.OnIndigoMuted else MbColors.Ink
    // A beat lights up exactly on the click and fades out along the spec's ease-out curve.
    val fadeOut = easeOut<Color>(motionMs(BEAT_FADE_MS))
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(MbDimens.GapSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(BEATS) { beat ->
            val active = beat == currentBeat
            val fill by animateColorAsState(
                if (active) MbColors.Tangerine else MbColors.Transparent,
                if (active) snap() else fadeOut,
                label = "beat",
            )
            val border by animateColorAsState(
                if (active) MbColors.Tangerine else frame,
                if (active) snap() else fadeOut,
                label = "beatBorder",
            )
            Box(
                Modifier
                    .size(BeatBoxSize)
                    .background(fill)
                    .border(MbDimens.Border, border),
            )
        }
    }
}
