package com.mouthband.convert

import com.mouthband.audio.CaptureSink
import com.mouthband.dsp.Rms
import kotlin.math.sqrt

/**
 * Lightweight onset detection on live mic audio (Teach flow, live drum grid while recording).
 * Runs on the capture consumer coroutine: 5 ms energy frames, an onset when the level jumps
 * well above the recent minimum, then — once 80 ms of the hit have arrived — its features are
 * handed to [onHit] together with the absolute sample index of the onset.
 */
class LiveOnsetDetector(
    private val sampleRate: Int,
    private val onHit: (sampleIndex: Long, analysis: DrumFeatures.Analysis) -> Unit,
) : CaptureSink {
    private val features = DrumFeatures(sampleRate)
    private val history = FloatArray(HISTORY_SECONDS * sampleRate)
    private val frameSize = (FRAME_SECONDS * sampleRate).toInt()
    private val window = (DrumFeatures.WINDOW_SECONDS * sampleRate).toInt()
    private val refractory = (REFRACTORY_SECONDS * sampleRate).toLong()
    private val minimumFrames = (MIN_TRACK_SECONDS / FRAME_SECONDS).toInt()
    private val recentDb = FloatArray(minimumFrames) { SILENCE_DB }
    private var recentIndex = 0

    /** Frames measured so far: the first [minimumFrames] only establish the background level. */
    private var measured = 0
    private var total = 0L
    private var frameFill = 0
    private var frameSum = 0.0
    private var lastOnset = Long.MIN_VALUE / 2
    private var pending = -1L
    private val scratch = FloatArray(window + MFCC_FRAME_PAD)

    override fun accept(samples: FloatArray, count: Int) {
        for (i in 0 until count) {
            val v = samples[i]
            history[(total % history.size).toInt()] = v
            total++
            frameSum += v * v
            frameFill++
            if (frameFill == frameSize) {
                onFrame(Rms.toDb(sqrt(frameSum / frameSize).toFloat()))
                frameSum = 0.0
                frameFill = 0
            }
        }
        val onset = pending
        if (onset >= 0 && total >= onset + window + MFCC_FRAME_PAD) {
            pending = -1L
            emit(onset)
        }
    }

    private fun onFrame(db: Float) {
        val floor = recentDb.min()
        recentDb[recentIndex] = db
        recentIndex = (recentIndex + 1) % recentDb.size
        // Until the background level is known, the first noise frames would look like a jump.
        if (measured < minimumFrames) {
            measured++
            return
        }
        val frameStart = total - frameSize
        val jump = db - floor >= JUMP_DB && db >= TRIGGER_DB
        if (jump && frameStart - lastOnset >= refractory && pending < 0) {
            lastOnset = frameStart
            pending = frameStart
        }
    }

    private fun emit(onset: Long) {
        for (j in scratch.indices) {
            val idx = onset + j
            scratch[j] = if (idx in (total - history.size) until total) history[(idx % history.size).toInt()] else 0f
        }
        onHit(onset, features.extract(scratch, 0))
    }

    private companion object {
        const val HISTORY_SECONDS = 2
        const val FRAME_SECONDS = 0.005
        const val MIN_TRACK_SECONDS = 0.1
        const val REFRACTORY_SECONDS = 0.12
        const val JUMP_DB = 12f
        const val TRIGGER_DB = -80f
        const val SILENCE_DB = -90f

        /** Extra samples so the last MFCC frame of the 80 ms window is complete. */
        const val MFCC_FRAME_PAD = 1024
    }
}
