package com.mouthband

import android.app.Application
import android.system.Os
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Creates the single [AppContainer] that every screen gets its collaborators from. */
class MouthBandApp : Application() {
    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Before ONNX Runtime is loaded: its 1DS telemetry stays off (see the manifest).
        Os.setenv(ORT_DISABLE_TELEMETRY, "1", true)
        container = AppContainer(this)
        // Every process start is a session; the first one never shows an interstitial (spec §5).
        scope.launch { container.settings.incrementSessionCount() }
        scope.launch { container.warmUpPitchModel() }
        scope.launch(Dispatchers.IO) { container.takeStore.sweepTemporaryFiles() }
    }

    private companion object {
        /** ONNX Runtime's switch for its usage telemetry: "1" turns it off. */
        const val ORT_DISABLE_TELEMETRY = "ORT_DISABLE_TELEMETRY"
    }
}
