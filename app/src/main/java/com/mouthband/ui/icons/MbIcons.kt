package com.mouthband.ui.icons

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.mouthband.ui.theme.MbColors

private const val VIEWPORT = 24f
private const val STROKE = 1.8f

/**
 * Thin line icons (1.8 dp stroke on a 24 dp grid), drawn for MouthBand. The stroke color is a
 * placeholder: every usage tints the icon.
 */
private fun lineIcon(name: String, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = VIEWPORT.dp,
        defaultHeight = VIEWPORT.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT,
    )
    paths.forEach { d ->
        builder.addPath(
            pathData = addPathNodes(d),
            stroke = SolidColor(MbColors.Ink),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

object MbIcons {
    val Back by lazy { lineIcon("back", "M19,12H5", "M11,6L5,12L11,18") }
    val ArrowRight by lazy { lineIcon("arrowRight", "M5,12H19", "M13,6L19,12L13,18") }
    val Settings by lazy {
        lineIcon(
            "settings",
            "M4,7H7M11,7H20M4,17H13M17,17H20",
            "M7,7a2,2 0 1,0 4,0a2,2 0 1,0 -4,0",
            "M13,17a2,2 0 1,0 4,0a2,2 0 1,0 -4,0",
        )
    }
    val Mic by lazy {
        lineIcon("mic", "M9,6a3,3 0 0,1 6,0v5a3,3 0 0,1 -6,0z", "M6,11a6,6 0 0,0 12,0", "M12,17v3", "M9,20h6")
    }
    val Play by lazy { lineIcon("play", "M8,5L19,12L8,19Z") }
    val Pause by lazy { lineIcon("pause", "M9,5v14", "M15,5v14") }
    val Stop by lazy { lineIcon("stop", "M7,7h10v10h-10z") }
    val Close by lazy { lineIcon("close", "M6,6L18,18", "M18,6L6,18") }
    val Check by lazy { lineIcon("check", "M5,12.5L10,17L19,7") }
    val Plus by lazy { lineIcon("plus", "M12,5v14", "M5,12h14") }
    val More by lazy {
        lineIcon(
            "more",
            "M11,6a1,1 0 1,0 2,0a1,1 0 1,0 -2,0",
            "M11,12a1,1 0 1,0 2,0a1,1 0 1,0 -2,0",
            "M11,18a1,1 0 1,0 2,0a1,1 0 1,0 -2,0",
        )
    }
    val Headphones by lazy {
        lineIcon(
            "headphones",
            "M4,16v-3a8,8 0 0,1 16,0v3",
            "M4,15h3v5h-2a1,1 0 0,1 -1,-1z",
            "M20,15h-3v5h2a1,1 0 0,0 1,-1z",
        )
    }
    val Bluetooth by lazy { lineIcon("bluetooth", "M7,7L17,17L12,21V3L17,7L7,17") }
    val Trash by lazy {
        lineIcon("trash", "M5,7h14", "M10,7V5h4v2", "M7,7l1,13h8l1,-13", "M10,11v6", "M14,11v6")
    }
    val Copy by lazy { lineIcon("copy", "M9,9h11v11h-11z", "M15,9V4H4v11h5") }
    val Edit by lazy { lineIcon("edit", "M4,20l1,-4L16,5l3,3L8,19z", "M14,7l3,3") }
    val Share by lazy {
        lineIcon(
            "share",
            "M15.5,5a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0",
            "M3.5,12a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0",
            "M15.5,19a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0",
            "M8.2,10.8L15.8,6.2",
            "M8.2,13.2L15.8,17.8",
        )
    }
    val Save by lazy { lineIcon("save", "M12,4v11", "M7,10l5,5l5,-5", "M5,20h14") }
    val Retry by lazy { lineIcon("retry", "M19,12A7,7 0 1,1 16.95,7.05", "M17,3.5V7.1H13.4") }
    val Volume by lazy {
        lineIcon("volume", "M4,9h4l5,-4v14l-5,-4h-4z", "M16,9a4,4 0 0,1 0,6", "M18.5,6.5a7.5,7.5 0 0,1 0,11")
    }
    val VolumeOff by lazy { lineIcon("volumeOff", "M4,9h4l5,-4v14l-5,-4h-4z", "M16,9.5l5,5", "M21,9.5l-5,5") }
    val Alert by lazy {
        lineIcon("alert", "M21,12A9,9 0 1,1 3,12A9,9 0 1,1 21,12Z", "M12,7.5v6", "M12,16.6v0.1")
    }
    val Note by lazy {
        lineIcon(
            "note",
            "M9,18V5l10,-2v13",
            "M4,18a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0",
            "M14,16a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0",
        )
    }
    val Wave by lazy { lineIcon("wave", "M6,10v4", "M9,7v10", "M12,4v16", "M15,7v10", "M18,10v4") }
    val ChevronDown by lazy { lineIcon("chevronDown", "M6,9l6,6l6,-6") }
    val ChevronRight by lazy { lineIcon("chevronRight", "M9,6l6,6l-6,6") }
    val Lock by lazy { lineIcon("lock", "M6,11h12v9h-12z", "M8.5,11V8a3.5,3.5 0 0,1 7,0v3") }
    val Speaker by lazy {
        lineIcon("speaker", "M7,3h10v18h-10z", "M10,15a2,2 0 1,0 4,0a2,2 0 1,0 -4,0", "M12,7v0.1")
    }
}
