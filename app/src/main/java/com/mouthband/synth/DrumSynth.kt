package com.mouthband.synth

import com.mouthband.dsp.Biquad
import com.mouthband.model.DrumKit
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/** The five sounds of every kit (spec §2.6). */
enum class DrumPiece { KICK, SNARE, HAT_CLOSED, HAT_OPEN, CLAP }

private class KickSpec(
    val startHz: Double,
    val endHz: Double,
    val pitchDecay: Double,
    val ampDecay: Double,
    val click: Float,
)

private class SnareSpec(
    val toneHz: Double,
    val tone: Float,
    val noiseLowHz: Float,
    val noiseHighHz: Float,
    val decay: Double,
)

/** Per-kit voicing: pitch sweep, decay and click of the kick. */
private val KickSpecs: Map<DrumKit, KickSpec> = mapOf(
    DrumKit.TRAP to KickSpec(95.0, 44.0, 0.06, 0.9, 0.15f),
    DrumKit.ROCK to KickSpec(170.0, 60.0, 0.03, 0.3, 0.6f),
    DrumKit.LOFI to KickSpec(120.0, 50.0, 0.04, 0.3, 0.1f),
    DrumKit.BOOM_BAP to KickSpec(140.0, 55.0, 0.035, 0.35, 0.3f),
    DrumKit.POP to KickSpec(140.0, 55.0, 0.035, 0.35, 0.3f),
)

/** Per-kit snare: body tone, noise band and decay. */
private val SnareSpecs: Map<DrumKit, SnareSpec> = mapOf(
    DrumKit.TRAP to SnareSpec(225.0, 0.35f, 1800f, 9000f, 0.14),
    DrumKit.ROCK to SnareSpec(185.0, 0.45f, 900f, 7000f, 0.25),
    DrumKit.LOFI to SnareSpec(190.0, 0.4f, 700f, 4000f, 0.18),
    DrumKit.BOOM_BAP to SnareSpec(195.0, 0.4f, 1200f, 6000f, 0.18),
    DrumKit.POP to SnareSpec(195.0, 0.4f, 1200f, 6000f, 0.18),
)

/** Per-kit hi-hat brightness (high-pass corner). */
private val HatHighPassHz: Map<DrumKit, Float> = mapOf(
    DrumKit.TRAP to 9000f,
    DrumKit.ROCK to 6000f,
    DrumKit.LOFI to 5000f,
    DrumKit.BOOM_BAP to 7000f,
    DrumKit.POP to 7000f,
)

/**
 * Synthesized drum one-shots: the fallback for every kit until sample assets exist (spec §2.6).
 * Each kit has its own character; Pop reuses pieces of the other kits, as its sample set does.
 */
class DrumSynth(private val sampleRate: Int) {
    fun render(kit: DrumKit, piece: DrumPiece): FloatArray {
        val source = sourceKit(kit, piece)
        val raw = when (piece) {
            DrumPiece.KICK -> kick(KickSpecs.getValue(source))
            DrumPiece.SNARE -> snare(SnareSpecs.getValue(source))
            DrumPiece.HAT_CLOSED -> hat(HatHighPassHz.getValue(source), CLOSED_DECAY)
            DrumPiece.HAT_OPEN -> hat(HatHighPassHz.getValue(source), OPEN_DECAY)
            DrumPiece.CLAP -> clap()
        }
        if (source == DrumKit.LOFI) lowPass(raw, LOFI_CUTOFF_HZ)
        return normalize(raw)
    }

    /** Pop reuses other kits' sounds. */
    fun sourceKit(kit: DrumKit, piece: DrumPiece): DrumKit = if (kit != DrumKit.POP) {
        kit
    } else {
        when (piece) {
            DrumPiece.KICK -> DrumKit.BOOM_BAP
            DrumPiece.SNARE, DrumPiece.CLAP -> DrumKit.TRAP
            DrumPiece.HAT_CLOSED, DrumPiece.HAT_OPEN -> DrumKit.ROCK
        }
    }

    private fun kick(spec: KickSpec): FloatArray {
        val out = FloatArray(seconds(spec.ampDecay * LENGTH_FACTOR))
        var phase = 0.0
        for (n in out.indices) {
            val t = n.toDouble() / sampleRate
            val hz = spec.endHz + (spec.startHz - spec.endHz) * exp(-t / spec.pitchDecay)
            phase += 2 * PI * hz / sampleRate
            val body = sin(phase) * exp(-t / spec.ampDecay)
            val click = spec.click * exp(-t / CLICK_DECAY) * sin(2 * PI * CLICK_HZ * t)
            out[n] = tanh((body + click) * KICK_DRIVE).toFloat()
        }
        return out
    }

    private fun snare(spec: SnareSpec): FloatArray {
        val out = FloatArray(seconds(spec.decay * LENGTH_FACTOR))
        val random = Random(SEED_SNARE)
        for (n in out.indices) {
            val t = n.toDouble() / sampleRate
            val tone = sin(2 * PI * spec.toneHz * t) * exp(-t / (spec.decay * TONE_DECAY_SHARE))
            out[n] = (spec.tone * tone).toFloat() + random.nextGaussian().toFloat() * exp(-t / spec.decay).toFloat()
        }
        bandPassNoise(out, spec.noiseLowHz, spec.noiseHighHz)
        return out
    }

    private fun hat(highPassHz: Float, decay: Double): FloatArray {
        val out = FloatArray(seconds(decay * LENGTH_FACTOR))
        val random = Random(SEED_HAT)
        for (n in out.indices) {
            val t = n.toDouble() / sampleRate
            out[n] = random.nextGaussian().toFloat() * exp(-t / decay).toFloat()
        }
        val hp = Biquad().apply { coefficients = Biquad.highPass(sampleRate, highPassHz) }
        hp.processBlock(out)
        return out
    }

    private fun clap(): FloatArray {
        val out = FloatArray(seconds(CLAP_LENGTH))
        val random = Random(SEED_CLAP)
        for (n in out.indices) {
            val t = n.toDouble() / sampleRate
            // Three quick bursts then a short tail.
            val burst = (0 until CLAP_BURSTS).sumOf { b ->
                val start = b * CLAP_SPACING
                if (t >= start) exp(-(t - start) / CLAP_BURST_DECAY) else 0.0
            }
            val tailStart = CLAP_BURSTS * CLAP_SPACING
            val tail = if (t >= tailStart) exp(-(t - tailStart) / CLAP_TAIL_DECAY) else 0.0
            out[n] = (random.nextGaussian() * (burst + tail)).toFloat()
        }
        bandPassNoise(out, CLAP_LOW_HZ, CLAP_HIGH_HZ)
        return out
    }

    private fun bandPassNoise(samples: FloatArray, lowHz: Float, highHz: Float) {
        Biquad().apply { coefficients = Biquad.highPass(sampleRate, lowHz) }.processBlock(samples)
        Biquad().apply { coefficients = Biquad.lowPass(sampleRate, highHz) }.processBlock(samples)
    }

    private fun lowPass(samples: FloatArray, hz: Float) {
        Biquad().apply { coefficients = Biquad.lowPass(sampleRate, hz) }.processBlock(samples)
    }

    private fun normalize(samples: FloatArray): FloatArray {
        val peak = samples.maxOfOrNull { abs(it) } ?: 0f
        if (peak > 0f) for (i in samples.indices) samples[i] *= PEAK / peak
        return samples
    }

    private fun seconds(s: Double): Int = (s * sampleRate).toInt().coerceAtLeast(1)

    private companion object {
        const val LENGTH_FACTOR = 5.0
        const val CLICK_DECAY = 0.004
        const val CLICK_HZ = 3000.0
        const val KICK_DRIVE = 1.4
        const val TONE_DECAY_SHARE = 0.6
        const val CLOSED_DECAY = 0.045
        const val OPEN_DECAY = 0.3
        const val CLAP_LENGTH = 0.4
        const val CLAP_BURSTS = 3
        const val CLAP_SPACING = 0.011
        const val CLAP_BURST_DECAY = 0.004
        const val CLAP_TAIL_DECAY = 0.08
        const val CLAP_LOW_HZ = 900f
        const val CLAP_HIGH_HZ = 3500f
        const val LOFI_CUTOFF_HZ = 4500f
        const val PEAK = 0.9f
        const val SEED_SNARE = 17L
        const val SEED_HAT = 23L
        const val SEED_CLAP = 31L
    }
}
