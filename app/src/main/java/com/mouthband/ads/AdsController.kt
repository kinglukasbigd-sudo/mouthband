package com.mouthband.ads

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.mouthband.BuildConfig
import com.mouthband.audio.AudioLog
import com.mouthband.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ads and consent (spec §5): consent info is refreshed on every app start and the UMP form is
 * shown when required; the Mobile Ads SDK starts only once `canRequestAds()` is true. Owns the
 * one interstitial (after an export, rate-limited by [AdPolicy]) and the privacy options form.
 * The only network use of the app.
 */
class AdsController(
    context: Context,
    private val settings: SettingsStore,
    private val audioIdle: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val randomInput: () -> Boolean = { ActivityManager.isUserAMonkey() },
) {
    private val appContext = context.applicationContext
    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val initStarted = AtomicBoolean(false)
    private var interstitial: InterstitialAd? = null
    private var loading = false

    private val _ready = MutableStateFlow(false)

    /** True once the SDK is initialized and ads may be requested. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)

    /** Settings shows "Privacy options" only when UMP says the form is required. */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /**
     * Call on every app start from the activity. While the Android UI monkey drives the app, no
     * consent form and no ad is started: random input would click through test-ad landing pages
     * and Play Store flows that belong to Google's SDK, not to the app under test (and stall a
     * slow emulator). Users are never "a monkey", so this changes nothing for them.
     */
    fun gatherConsent(activity: Activity) {
        if (randomInput()) return
        val params = ConsentRequestParameters.Builder().build()
        consent.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    error?.let { AudioLog.w("consent form: ${it.message}") }
                    onConsentSettled()
                }
            },
            { error ->
                AudioLog.w("consent info: ${error.message}")
                onConsentSettled()
            },
        )
        // Consent given in an earlier session lets ads start without waiting for the update.
        startAdsIfAllowed()
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            error?.let { AudioLog.w("privacy options: ${it.message}") }
            onConsentSettled()
        }
    }

    private fun onConsentSettled() {
        _privacyOptionsRequired.value =
            consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        startAdsIfAllowed()
    }

    private fun startAdsIfAllowed() {
        if (!consent.canRequestAds() || !initStarted.compareAndSet(false, true)) return
        scope.launch {
            withContext(Dispatchers.IO) {
                MobileAds.initialize(appContext) { scope.launch { onInitialized() } }
            }
        }
    }

    private suspend fun onInitialized() {
        _ready.value = true
        if (settings.flagsNow().sessionCount > AdPolicy.FIRST_SESSION) preloadInterstitial()
    }

    private fun preloadInterstitial() {
        if (!_ready.value || interstitial != null || loading) return
        loading = true
        InterstitialAd.load(
            appContext,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loading = false
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    AudioLog.w("interstitial: ${error.message}")
                }
            },
        )
    }

    /** An export was saved or shared: show the interstitial when [AdPolicy] allows it. */
    fun onExportFinished(activity: Activity) {
        scope.launch {
            val flags = settings.flagsNow()
            val now = clock()
            val allowed = AdPolicy.interstitialAllowed(flags.sessionCount, flags.lastInterstitialAt, now, audioIdle())
            if (!_ready.value || !allowed) return@launch
            val ad = interstitial
            if (ad == null) {
                preloadInterstitial()
                return@launch
            }
            interstitial = null
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() = preloadInterstitial()
            }
            settings.setLastInterstitialAt(now)
            ad.show(activity)
        }
    }
}
