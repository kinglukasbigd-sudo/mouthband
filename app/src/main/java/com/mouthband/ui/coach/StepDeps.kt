package com.mouthband.ui.coach

import com.mouthband.AppContainer
import com.mouthband.audio.RecordingSession
import com.mouthband.convert.DrumClassifier
import com.mouthband.convert.DrumFeatures
import com.mouthband.model.DrumClass

/** Collaborators of a recording step, taken from the AppContainer. */
class StepDeps(container: AppContainer) {
    val repository = container.repository
    val tracks = container.trackRepository
    val settings = container.settings
    val templates = container.templates
    val engine = container.engine
    val micMonitor = container.micMonitor
    val session = container.audioSession
    val outputDevices = container.outputDevices
    val renderer = container.loopRenderer
    val cache = container.renderCache
    val processor = container.takeProcessor
    val takes = container.takeStore
    val newSession: () -> RecordingSession = container::newRecordingSession

    @Volatile
    private var liveClassifier: DrumClassifier? = null

    /** Classifies live hits with the user's templates (loaded once per take) or the rules. */
    fun classifierFor(analysis: DrumFeatures.Analysis): DrumClass =
        (liveClassifier ?: DrumClassifier(null)).classify(analysis)

    suspend fun loadLiveClassifier() {
        liveClassifier = DrumClassifier(templates.current())
    }
}
