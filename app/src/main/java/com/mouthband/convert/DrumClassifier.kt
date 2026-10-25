package com.mouthband.convert

import com.mouthband.model.DrumClass
import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/** One taught example: a sound class and its feature vector. */
@Serializable
data class TemplateExample(val cls: DrumClass, val features: List<Float>)

/** The user's taught sounds (spec §1 Teach your sounds): 4 examples × 3 classes. */
@Serializable
data class BeatboxTemplates(val examples: List<TemplateExample>) {
    val isComplete: Boolean
        get() = DrumClass.entries.all { cls -> examples.count { it.cls == cls } >= EXAMPLES_PER_CLASS }

    companion object {
        const val EXAMPLES_PER_CLASS = 4
    }
}

/**
 * Classifies a hit (spec §2.4 step 3): k-nearest neighbours (k = 3, Euclidean) over
 * z-normalized features against the user's templates, or the rule fallback without templates.
 */
class DrumClassifier(templates: BeatboxTemplates?) {
    private val examples = templates?.takeIf { it.isComplete }?.examples.orEmpty()
    private val mean = FloatArray(DrumFeatures.SIZE)
    private val std = FloatArray(DrumFeatures.SIZE) { 1f }
    private val normalized: List<Pair<DrumClass, FloatArray>>

    init {
        if (examples.isNotEmpty()) {
            for (d in 0 until DrumFeatures.SIZE) {
                val column = examples.map { it.features[d] }
                val m = column.average().toFloat()
                val variance = column.sumOf { ((it - m) * (it - m)).toDouble() } / column.size
                mean[d] = m
                std[d] = sqrt(variance).toFloat().takeIf { it > MIN_STD } ?: 1f
            }
        }
        normalized = examples.map { it.cls to normalize(it.features.toFloatArray()) }
    }

    val usesTemplates: Boolean get() = normalized.isNotEmpty()

    fun classify(analysis: DrumFeatures.Analysis): DrumClass =
        if (usesTemplates) nearest(normalize(analysis.values)) else byRules(analysis)

    private fun normalize(values: FloatArray): FloatArray =
        FloatArray(DrumFeatures.SIZE) { d -> (values[d] - mean[d]) / std[d] }

    private fun nearest(query: FloatArray): DrumClass {
        val ranked = normalized
            .map { (cls, v) -> cls to distance(query, v) }
            .sortedBy { it.second }
            .take(K)
        val votes = ranked.groupingBy { it.first }.eachCount()
        val best = votes.values.max()
        val tied = votes.filterValues { it == best }.keys
        // Ties go to the class of the closest neighbour among the tied classes.
        return ranked.first { it.first in tied }.first
    }

    /**
     * Euclidean distance over the sound-shape features. The hit's absolute level is left out: it
     * depends on how close the phone was and how hard the user beatboxed, and takes are brought
     * to a different level than the sounds were taught at.
     */
    private fun distance(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in 0 until DrumFeatures.PEAK_RMS) {
            val d = a[i] - b[i]
            sum += d * d
        }
        return sqrt(sum)
    }

    companion object {
        const val K = 3
        private const val MIN_STD = 1e-6f
        const val KICK_LOW_RATIO = 0.45f
        const val HAT_CENTROID_HZ = 5000f
        const val HAT_ZCR = 0.15f

        /** Rule fallback (spec §2.4): low band > 0.45 → kick; centroid > 5 kHz + high ZCR → hi-hat; else snare. */
        fun byRules(analysis: DrumFeatures.Analysis): DrumClass = when {
            analysis.lowRatio > KICK_LOW_RATIO -> DrumClass.KICK
            analysis.centroidHz > HAT_CENTROID_HZ && analysis.zeroCrossingRate > HAT_ZCR -> DrumClass.HIHAT
            else -> DrumClass.SNARE
        }
    }
}
