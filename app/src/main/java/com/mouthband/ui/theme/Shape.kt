package com.mouthband.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/** Shapes and spacing from spec §3: thin frames, no shadows, 4 dp grid. */
object MbShapes {
    val Card = RoundedCornerShape(10.dp)
    val Pill = RoundedCornerShape(30.dp)
    val Tag = RectangleShape
    val Circle = CircleShape
}

object MbDimens {
    val Border = 1.5.dp
    val BorderActive = 3.dp
    val ScreenPadding = 24.dp
    val GapSmall = 10.dp
    val Gap = 12.dp
    val GapLarge = 16.dp
    val Grid = 4.dp
    val SectionGap = 32.dp
    val PrimaryButtonHeight = 58.dp
    val SecondaryButtonHeight = 54.dp
    val ArrowCircle = 44.dp
    val TouchTarget = 48.dp
    val MuteButton = 44.dp
    val IconSize = 24.dp
    val IconSmall = 20.dp
    val RecordOuter = 100.dp
    val RecordInner = 84.dp
    val RecordStopSquare = 30.dp
    val StripHeight = 6.dp
    val ChipHeight = 40.dp
    val RuleThickness = 1.5.dp
    val Hairline = 1.dp

    /** Wide windows (tablets, unfolded foldables, landscape) keep the phone layout, centred. */
    val ContentMaxWidth = 600.dp
}
