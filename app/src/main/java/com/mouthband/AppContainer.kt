package com.mouthband

import ai.onnxruntime.OrtException
import android.content.Context
import com.mouthband.ads.AdsController
import com.mouthband.audio.AudioEngine
import com.mouthband.audio.AudioLog
import com.mouthband.audio.AudioSession
import com.mouthband.audio.LatencyCalibrator
import com.mouthband.audio.MicMonitor
import com.mouthband.audio.OutputDeviceMonitor
import com.mouthband.audio.Recorder
import com.mouthband.audio.RecordingSession
import com.mouthband.convert.BeatboxConverter
import com.mouthband.convert.PitchConverter
import com.mouthband.convert.PitchTracker
import com.mouthband.convert.SwiftF0PitchTracker
import com.mouthband.convert.TakeProcessor
import com.mouthband.data.SongRepository
import com.mouthband.data.TakeStore
import com.mouthband.data.TrackRepository
import com.mouthband.data.db.MouthBandDatabase
import com.mouthband.data.prefs.SettingsStore
import com.mouthband.data.prefs.TemplateStore
import com.mouthband.mix.MixLoader
import com.mouthband.mix.SongPlayer
import com.mouthband.synth.InstrumentLibrary
import com.mouthband.synth.LoopRenderer
import com.mouthband.synth.RenderCache

/** Manual dependency injection: one instance, created in [MouthBandApp.onCreate]. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val settings = SettingsStore(appContext)
    val templates = TemplateStore(appContext)
    val takeStore = TakeStore(appContext.filesDir)
    val database: MouthBandDatabase by lazy { MouthBandDatabase.create(appContext) }
    val repository: SongRepository by lazy { SongRepository(database, takeStore) }
    val trackRepository: TrackRepository by lazy { TrackRepository(database, takeStore) }

    val engine: AudioEngine by lazy { AudioEngine(appContext) }
    val recorder: Recorder by lazy { Recorder(appContext, engine.sampleRate) }
    val audioSession: AudioSession by lazy { AudioSession(appContext) }
    val outputDevices: OutputDeviceMonitor by lazy { OutputDeviceMonitor(appContext) }
    val micMonitor: MicMonitor by lazy { MicMonitor(recorder) }
    val calibrator: LatencyCalibrator by lazy { LatencyCalibrator(engine, recorder, settings) }

    val instruments: InstrumentLibrary by lazy { InstrumentLibrary(appContext.assets, engine.sampleRate) }
    val loopRenderer: LoopRenderer by lazy { LoopRenderer(instruments, engine.sampleRate) }
    val renderCache: RenderCache by lazy { RenderCache(loopRenderer, takeStore, engine.sampleRate) }

    /** Loaded on first use (off the main thread); null makes the converter use YIN. */
    private val swiftF0: SwiftF0PitchTracker? by lazy { SwiftF0PitchTracker.load(appContext.assets) }
    val takeProcessor: TakeProcessor by lazy {
        TakeProcessor(
            engine.sampleRate,
            BeatboxConverter(engine.sampleRate),
            PitchConverter(engine.sampleRate, primary = { swiftF0 }),
        )
    }

    /** Consent + AdMob (spec §5). Ads never interrupt audio: see [AdsController.onExportFinished]. */
    val ads: AdsController by lazy {
        AdsController(appContext, settings, audioIdle = { engine.source == null && !recorder.isRunning })
    }

    /**
     * Loads the pitch model and runs it once on silence, off the main thread, so the first hummed
     * take converts without the load delay (and a broken model shows up in the log at start).
     */
    fun warmUpPitchModel() {
        val tracker = swiftF0
        if (tracker == null) {
            AudioLog.w("SwiftF0 unavailable, using YIN")
            return
        }
        try {
            tracker.track(FloatArray(PitchTracker.HOP * WARM_UP_HOPS))
            AudioLog.i("SwiftF0 ready")
        } catch (e: OrtException) {
            AudioLog.w("SwiftF0 warm-up failed", e)
        }
    }

    fun newRecordingSession(): RecordingSession = RecordingSession(engine, recorder)

    fun newMixLoader(): MixLoader = MixLoader(renderCache, engine.sampleRate)

    fun newSongPlayer(): SongPlayer = SongPlayer(engine, audioSession, newMixLoader())

    /** Activity stopped: nothing may keep playing or recording in the background (spec §2.1). */
    fun onAppStopped() {
        audioSession.interrupt()
        micMonitor.stop()
        recorder.stop()
        engine.stop()
    }

    private companion object {
        const val WARM_UP_HOPS = 16
    }
}
