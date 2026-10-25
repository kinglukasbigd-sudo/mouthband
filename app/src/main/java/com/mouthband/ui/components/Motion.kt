package com.mouthband.ui.components

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import com.mouthband.ui.theme.LocalReduceMotion

/** Spec §3 Motion: 150–220 ms ease-out transitions. */
const val MOTION_MS = 200

private const val PRESSED_SCALE = 0.97f

/** [ms], or 0 when the system "remove animations" setting is on. */
@Composable
fun motionMs(ms: Int = MOTION_MS): Int = if (LocalReduceMotion.current) 0 else ms

/** The spec's ease-out curve over [ms]. */
fun <T> easeOut(ms: Int): TweenSpec<T> = tween(ms, easing = EaseOut)

/** [easeOut] over [ms], instant when the system "remove animations" setting is on. */
@Composable
fun <T> motionTween(ms: Int = MOTION_MS): TweenSpec<T> = easeOut(motionMs(ms))

/**
 * Press feedback for tappable surfaces: they sink to 97 % while pressed and spring back. Read
 * the value inside `graphicsLayer {}` so pressing only redraws.
 */
@Composable
fun rememberPressScale(interaction: InteractionSource): State<Float> {
    val pressed by interaction.collectIsPressedAsState()
    val still = LocalReduceMotion.current
    return animateFloatAsState(
        targetValue = if (pressed && !still) PRESSED_SCALE else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
}
