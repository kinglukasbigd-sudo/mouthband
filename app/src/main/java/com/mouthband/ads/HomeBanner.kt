package com.mouthband.ads

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect

/*
 * The adaptive banner at the bottom of Home (spec §5), the app's only banner.
 *
 * Its AdView lives on a layer beneath every screen for the whole session ([HomeBannerLayer]) and
 * Home leaves a see-through gap where it shows ([HomeBannerSlot]). A view inside Home was detached
 * from the window on every navigation, and the ad's web content rebuilt its GPU drawing state when
 * Home came back: 15–60 ms frames on a 120 Hz phone. While Home is not shown the banner is paused and
 * invisible, so it is never displayed or measured as seen behind another screen.
 */

/** Home's gap for the banner: as large as the ad, transparent, reporting where it is. */
@Composable
fun HomeBannerSlot(holder: BannerHolder, slot: BannerSlot, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = maxWidth.value.toInt()
        val size = remember(width) { holder.sizeFor(width) }
        DisposableEffect(slot) {
            onDispose { slot.bounds = null }
        }
        Spacer(
            Modifier
                .size(size.width.dp, size.height.dp)
                .onGloballyPositioned {
                    val at = it.positionInRoot().round()
                    val bounds = IntRect(at.x, at.y, at.x + it.size.width, at.y + it.size.height)
                    slot.widthDp = width
                    slot.placed = bounds
                    slot.bounds = bounds
                },
        )
    }
}

/** The banner view, kept attached for the whole session and shown only in Home's gap. */
@Composable
fun HomeBannerLayer(holder: BannerHolder, slot: BannerSlot, modifier: Modifier = Modifier) {
    val width = slot.widthDp
    if (width <= 0) return
    val adView = remember(width) { holder.viewFor(width) }
    val visible = slot.bounds != null
    LifecycleResumeEffect(adView, visible) {
        if (visible) adView.resume() else adView.pause()
        onPauseOrDispose { adView.pause() }
    }
    Box(modifier.fillMaxSize()) {
        key(adView) {
            AndroidView(
                // The view outlives a width change of this layer: take it from a previous parent first.
                factory = { (adView.parent as? ViewGroup)?.removeView(adView) ?: Unit; adView },
                modifier = Modifier.layout { measurable, constraints ->
                    val at = slot.placed ?: IntRect.Zero
                    val placeable = measurable.measure(Constraints.fixed(at.width, at.height))
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(at.left, at.top) }
                },
                update = { it.visibility = if (visible) View.VISIBLE else View.INVISIBLE },
            )
        }
    }
}
