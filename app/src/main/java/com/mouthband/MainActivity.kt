package com.mouthband

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mouthband.ads.BannerHolder
import com.mouthband.ads.BannerSlot
import com.mouthband.ads.HomeBannerLayer
import com.mouthband.ads.HomeBannerSlot
import com.mouthband.ui.navigation.AppShell
import com.mouthband.ui.navigation.MouthBandNavHost
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MouthBandTheme

class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as MouthBandApp).container
    private val banner = BannerHolder(this)
    private val bannerSlot = BannerSlot()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container.ads.gatherConsent(this)
        setContent {
            val adsReady by container.ads.ready.collectAsStateWithLifecycle()
            val privacyRequired by container.ads.privacyOptionsRequired.collectAsStateWithLifecycle()
            MouthBandTheme {
                // The banner layer lies beneath every screen; Home shows it through its gap.
                Box(Modifier.fillMaxSize().background(MbColors.Cream)) {
                    if (adsReady) HomeBannerLayer(banner, bannerSlot)
                    MouthBandNavHost(
                        AppShell(
                            homeBanner = if (adsReady) ({ HomeBannerSlot(banner, bannerSlot) }) else null,
                            onExportFinished = { container.ads.onExportFinished(this@MainActivity) },
                            onPrivacyOptions = { container.ads.showPrivacyOptions(this@MainActivity) },
                            privacyOptionsRequired = privacyRequired,
                        ),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        banner.destroy()
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        // Spec §2.1: playback and recording stop when the app leaves the screen.
        if (!isChangingConfigurations) container.onAppStopped()
    }
}
