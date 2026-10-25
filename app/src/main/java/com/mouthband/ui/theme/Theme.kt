package com.mouthband.ui.theme

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Largest font scale the layouts support (spec §3 accessibility: dynamic type up to 1.3×). */
private const val MAX_FONT_SCALE = 1.3f

/** True when the system "remove animations" setting is on. Components skip motion then. */
val LocalReduceMotion = staticCompositionLocalOf { false }

private val MbColorScheme = lightColorScheme(
    primary = MbColors.Indigo,
    onPrimary = MbColors.OnIndigo,
    secondary = MbColors.Tangerine,
    onSecondary = MbColors.Indigo,
    background = MbColors.Cream,
    onBackground = MbColors.Ink,
    surface = MbColors.Paper,
    onSurface = MbColors.Ink,
    surfaceVariant = MbColors.Cream,
    onSurfaceVariant = MbColors.InkSoft,
    surfaceContainer = MbColors.Paper,
    surfaceContainerLow = MbColors.Paper,
    surfaceContainerHigh = MbColors.Paper,
    surfaceContainerHighest = MbColors.Paper,
    surfaceContainerLowest = MbColors.Paper,
    outline = MbColors.Ink,
    outlineVariant = MbColors.Rule,
    scrim = MbColors.Scrim,
    error = MbColors.Indigo,
    onError = MbColors.OnIndigo,
)

@Composable
fun MouthBandTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val density = LocalDensity.current
    val cappedDensity = remember(density) {
        Density(density.density, density.fontScale.coerceAtMost(MAX_FONT_SCALE))
    }
    val type = DefaultTypography
    val materialType = Typography(
        displayLarge = type.display,
        headlineLarge = type.h1,
        headlineMedium = type.h2,
        titleLarge = type.h2,
        titleMedium = type.label,
        bodyLarge = type.body,
        bodyMedium = type.bodySm,
        labelLarge = type.button,
        labelMedium = type.label,
        labelSmall = type.mono,
    )
    CompositionLocalProvider(
        LocalMbTypography provides type,
        LocalReduceMotion provides reduceMotion,
        LocalDensity provides cappedDensity,
    ) {
        MaterialTheme(colorScheme = MbColorScheme, typography = materialType, content = content)
    }
}

/** Shorthand accessors used by every screen. */
object MbTheme {
    val type: MbTypography
        @Composable get() = LocalMbTypography.current
}
