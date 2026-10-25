package com.mouthband.ads

import android.app.Activity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.mouthband.BuildConfig

/**
 * Keeps one banner [AdView] for the whole activity: made once per width (one ad request per
 * session), shown by [HomeBannerLayer] and destroyed with the activity.
 */
class BannerHolder(private val activity: Activity) {
    private var view: AdView? = null
    private var widthDp = NO_WIDTH
    private val sizes = HashMap<Int, AdSize>()

    /** The adaptive banner size for a [width] in dp: what Home reserves and the view is given. */
    fun sizeFor(width: Int): AdSize = sizes.getOrPut(width) {
        AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, width)
    }

    fun viewFor(width: Int): AdView {
        val existing = view
        if (existing != null && widthDp == width) return existing
        existing?.destroy()
        return AdView(activity).apply {
            setAdSize(sizeFor(width))
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            loadAd(AdRequest.Builder().build())
        }.also {
            view = it
            widthDp = width
        }
    }

    fun destroy() {
        view?.destroy()
        view = null
        widthDp = NO_WIDTH
    }

    private companion object {
        const val NO_WIDTH = -1
    }
}
