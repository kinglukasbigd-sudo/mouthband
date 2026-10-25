package com.mouthband.ads

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect

/**
 * Where Home shows the banner, in the activity content's coordinates. [HomeBannerSlot] reports it
 * while Home is composed; [HomeBannerLayer] places the one banner view there.
 */
@Stable
class BannerSlot {
    /** The gap Home leaves for the banner right now; null while Home is not composed. */
    var bounds: IntRect? by mutableStateOf(null)

    /** The last gap Home left: the banner stays there, hidden, while another screen is shown. */
    var placed: IntRect? by mutableStateOf(null)

    /** Home's width in dp, which picks the adaptive banner size; 0 until Home was laid out. */
    var widthDp: Int by mutableIntStateOf(0)
}
