package com.mouthband.convert

import com.mouthband.audio.AlignedTake
import com.mouthband.audio.LoopMath
import com.mouthband.dsp.Rms
import com.mouthband.model.DrumHit
import kotlin.math.abs
import kotlin.math.roundToLong

/** Why a take produced no usable result. */
enum class TakeProblem { SILENCE, NO_HITS, NO_PITCH }

/** Converted beat: performed hit positions (loop frames) or a problem. */
data class BeatResult(val hits: List<DrumHit>, val problem: TakeProblem?)

/**
 * Beatbox → drums (spec §2.4): onsets → features → kNN / rules → noise-floor and click-bleed
 * rejection → velocity → same-class-same-16th dedupe. Hits keep their performed position; the
 * Timing slider quantizes them later ([Quantizer]).
 */
class BeatboxConverter(private val sampleRate: Int) {
    private val detector = OnsetDetector(sampleRate)
    private val features = DrumFeatures(sampleRate)

    fun convert(take: AlignedTake, bpm: Int, templates: BeatboxTemplates?, clicksAudible: Boolean): BeatResult {
        val samples = take.samples
        val loopRms = Rms.of(samples, take.prerollFrames, take.loopFrames)
        if (Rms.toDb(loopRms) < SILENCE_DB) return BeatResult(emptyList(), TakeProblem.SILENCE)
        val classifier = DrumClassifier(templates)
        val floorDb = maxOf(NOISE_FLOOR_DB, percentileFrameDb(samples, FLOOR_PERCENTILE))
        val step = LoopMath.framesPerStep(bpm, sampleRate)
        val beat = LoopMath.framesPerBeat(bpm, sampleRate)
        val accepted = ArrayList<Pair<DrumHit, Float>>()
        for (onset in detector.detect(samples)) {
            val analysis = features.extract(samples, onset)
            val frame = onset - take.prerollFrames
            val inWindow = frame >= -step / 2 && frame < take.loopFrames + step / 2
            val bleed = clicksAudible && isClickBleed(analysis, frame, beat)
            if (inWindow && analysis.peakRmsDb >= floorDb && !bleed) {
                val hit = DrumHit(classifier.classify(analysis), Math.floorMod(frame, take.loopFrames), 1f)
                accepted += hit to analysis.peakRmsDb
            }
        }
        if (accepted.isEmpty()) return BeatResult(emptyList(), TakeProblem.NO_HITS)
        val loudest = accepted.maxOf { it.second }
        val withVelocity = accepted.map { (hit, db) -> hit.copy(velocity = velocity(db, floorDb, loudest)) }
        val totalSteps = (take.loopFrames / step).roundToLong().toInt()
        return BeatResult(Quantizer.dedupe(withVelocity, step, totalSteps), null)
    }

    /** Metronome clicks picked up by the mic: tonal at 800/1000 Hz and within ±25 ms of a beat. */
    private fun isClickBleed(analysis: DrumFeatures.Analysis, frame: Int, beat: Double): Boolean {
        val nearestBeat = (frame / beat).roundToLong() * beat
        val nearBeat = abs(frame - nearestBeat) <= CLICK_GUARD_SECONDS * sampleRate
        return nearBeat && analysis.clickShare > CLICK_SHARE
    }

    private fun percentileFrameDb(samples: FloatArray, percentile: Float): Float {
        val count = samples.size / OnsetDetector.HOP
        if (count == 0) return NOISE_FLOOR_DB
        val levels = FloatArray(count) { Rms.toDb(Rms.of(samples, it * OnsetDetector.HOP, OnsetDetector.HOP)) }
        levels.sort()
        return levels[(percentile * (count - 1)).toInt()]
    }

    private fun velocity(db: Float, floorDb: Float, loudestDb: Float): Float {
        val span = loudestDb - floorDb
        if (span <= 0f) return MAX_VELOCITY
        return MIN_VELOCITY + (MAX_VELOCITY - MIN_VELOCITY) * ((db - floorDb) / span).coerceIn(0f, 1f)
    }

    companion object {
        /** Spec §2.4: reject below −45 dBFS or the 20th percentile of frame RMS, whichever is higher. */
        const val NOISE_FLOOR_DB = -45f
        const val FLOOR_PERCENTILE = 0.2f
        const val MIN_VELOCITY = 0.5f
        const val MAX_VELOCITY = 1.0f
        private const val SILENCE_DB = -55f
        private const val CLICK_GUARD_SECONDS = 0.025
        private const val CLICK_SHARE = 0.5f
    }
}
