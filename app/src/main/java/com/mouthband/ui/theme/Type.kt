package com.mouthband.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mouthband.R

/** The bundled variable fonts (OFL), one instance per weight the design uses. */
private fun variableFont(resId: Int, weight: FontWeight) =
    Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val Fraunces = FontFamily(
    variableFont(R.font.fraunces, FontWeight.SemiBold),
    variableFont(R.font.fraunces, FontWeight.Bold),
)

val Inter = FontFamily(
    variableFont(R.font.inter, FontWeight.Normal),
    variableFont(R.font.inter, FontWeight.SemiBold),
    variableFont(R.font.inter, FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    variableFont(R.font.jetbrains_mono, FontWeight.Medium),
    variableFont(R.font.jetbrains_mono, FontWeight.Bold),
)

/** Type scale from spec §3. Mono text is always rendered uppercase by [com.mouthband.ui.components.MonoLabel]. */
@Immutable
data class MbTypography(
    val display: TextStyle,
    val h1: TextStyle,
    val h2: TextStyle,
    val wordmark: TextStyle,
    val body: TextStyle,
    val bodySm: TextStyle,
    val label: TextStyle,
    val button: TextStyle,
    val mono: TextStyle,
    val monoBold: TextStyle,
    val monoSmall: TextStyle,
)

val DefaultTypography = MbTypography(
    display = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.045).em,
    ),
    h1 = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 39.sp,
        letterSpacing = (-0.045).em,
    ),
    h2 = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.02).em,
    ),
    wordmark = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.02).em,
    ),
    body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodySm = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    button = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
    mono = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.4.sp,
    ),
    monoBold = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.6.sp,
    ),
    monoSmall = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.2.sp,
    ),
)

val LocalMbTypography = staticCompositionLocalOf { DefaultTypography }
