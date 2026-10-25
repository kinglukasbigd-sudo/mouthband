package com.mouthband.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos

/**
 * Ticks once per display frame while [active]. Read its value inside draw code (`Canvas`,
 * `drawWithContent`): every frame then only redraws that one element and nothing recomposes,
 * which keeps live audio views smooth at 60–120 Hz.
 */
@Composable
fun rememberFrameTick(active: Boolean = true): State<Long> {
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(active) {
        if (active) {
            while (true) withFrameNanos { tick.longValue = it }
        }
    }
    return tick
}

/** [sample] evaluated once per display frame while [active], otherwise [idle]. Read it in draw code. */
@Composable
fun rememberFrameSample(active: Boolean, idle: Float = -1f, sample: () -> Float): State<Float> {
    val value = remember { mutableFloatStateOf(idle) }
    val current by rememberUpdatedState(sample)
    LaunchedEffect(active) {
        if (active) {
            while (true) withFrameNanos { value.floatValue = current() }
        } else {
            value.floatValue = idle
        }
    }
    return value
}
