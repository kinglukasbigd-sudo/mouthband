package com.mouthband.synth

import android.content.res.AssetManager
import com.mouthband.audio.AudioLog
import com.mouthband.dsp.Resampler
import com.mouthband.model.DrumKit
import com.mouthband.model.Instrument
import java.io.IOException
import kotlin.math.abs

/**
 * Source of instrument sounds. Sample assets (`assets/samples/...`, see tools/SAMPLES.md) are
 * decoded once, resampled to the engine rate and cached; anything missing falls back to the
 * built-in synthesis so every preset always plays (spec §2.6).
 */
class InstrumentLibrary(private val assets: AssetManager?, private val sampleRate: Int) {
    private val drumSynth = DrumSynth(sampleRate)
    private val drums = HashMap<String, FloatArray>()
    private val sampleSets = HashMap<Instrument, SampleSet?>()

    /** One-shot for a kit piece: the kit's WAV, Pop's borrowed WAV, or the drum synth. */
    @Synchronized
    fun drum(kit: DrumKit, piece: DrumPiece): FloatArray {
        val key = "${kit.name}/${piece.name}"
        return drums.getOrPut(key) {
            loadWav("samples/kits/${kit.folder()}/${piece.fileName()}.wav")
                ?: loadWav("samples/kits/${drumSynth.sourceKit(kit, piece).folder()}/${piece.fileName()}.wav")
                ?: drumSynth.render(kit, piece)
        }
    }

    /** Multisample set of a sample-based preset, or null when its assets are not bundled. */
    @Synchronized
    fun sampleSet(instrument: Instrument): SampleSet? = sampleSets.getOrPut(instrument) {
        val folder = instrument.assetFolder ?: return@getOrPut null
        val path = "samples/$folder"
        val names = try {
            assets?.list(path).orEmpty()
        } catch (e: IOException) {
            AudioLog.w("list $path", e)
            emptyArray()
        }
        val roots = names.mapNotNull { name -> name.removeSuffix(".wav").toIntOrNull()?.let { it to "$path/$name" } }
            .sortedBy { it.first }
        val loaded = roots.mapNotNull { (midi, file) -> loadWav(file)?.let { midi to it } }
        if (loaded.isEmpty()) null else SampleSet(loaded.map { it.first }.toIntArray(), loaded.map { it.second })
    }

    private fun loadWav(path: String): FloatArray? {
        val manager = assets ?: return null
        return try {
            manager.open(path).use { stream ->
                val decoded = WavReader.read(stream)
                Resampler.resample(decoded.samples, decoded.sampleRate, sampleRate)
            }
        } catch (e: IOException) {
            // Missing assets are expected until samples are exported (tools/SAMPLES.md).
            AudioLog.d("no asset $path: ${e.message}")
            null
        }
    }

    private fun DrumKit.folder(): String = name.lowercase()

    private fun DrumPiece.fileName(): String = name.lowercase()
}

/** Multisampled instrument: one recording every few semitones, keyed by MIDI root. */
class SampleSet(val roots: IntArray, val samples: List<FloatArray>) {
    /** Index of the sample whose root is nearest to [midi]. */
    fun nearest(midi: Int): Int {
        var best = 0
        for (i in roots.indices) {
            if (abs(roots[i] - midi) < abs(roots[best] - midi)) best = i
        }
        return best
    }
}
