package com.mouthband.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Live mic level for the coach step meter (spec §1): opens the mic without recording to a file.
 * [sink] can additionally receive the samples (Teach flow).
 */
class MicMonitor(private val recorder: Recorder) {
    private val _level = MutableStateFlow(MicLevel.Silent)
    val level: StateFlow<MicLevel> = _level.asStateFlow()

    private val _peaks = MutableStateFlow(FloatArray(CaptureConsumer.DEFAULT_HISTORY))
    val peaks: StateFlow<FloatArray> = _peaks.asStateFlow()

    private var job: Job? = null

    /** Starts listening in [scope]. Returns the mic open result. */
    fun start(scope: CoroutineScope, sink: CaptureSink? = null): RecorderStart {
        stop()
        val result = recorder.start(engine = null)
        if (result != RecorderStart.OK) return result
        val consumer = CaptureConsumer(recorder)
        job = scope.launch(Dispatchers.Default) {
            launch { consumer.level.collect { _level.value = it } }
            launch { consumer.peaks.collect { _peaks.value = it } }
            consumer.drain(listOfNotNull(sink))
        }
        return RecorderStart.OK
    }

    fun stop() {
        job?.cancel()
        job = null
        recorder.stop()
        _level.value = MicLevel.Silent
    }

    val isRunning: Boolean get() = job?.isActive == true
}
