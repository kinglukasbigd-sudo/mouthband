package com.mouthband.synth

import com.mouthband.dsp.SvfLowPass
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** A playing note. Implementations add their output to a mono sample stream. */
interface Voice {
    val isActive: Boolean
    val startedAt: Long

    fun start(midi: Int, velocity: Float, at: Long)

    fun release()

    /** Fast fade when the voice is stolen. */
    fun steal()

    fun next(): Float
}

private const val A4_MIDI = 69
private const val A4_HZ = 440.0
private const val SEMITONES = 12.0
private const val CENTS_PER_OCTAVE = 1200.0

fun midiToHz(midi: Double): Double = A4_HZ * 2.0.pow((midi - A4_MIDI) / SEMITONES)

/** Subtractive synth voice: 2 band-limited oscillators → 24 dB low-pass (enveloped) → amp ADSR. */
class SynthVoice(private val sampleRate: Int, private val patch: SynthPatch) : Voice {
    private val amp = Adsr(sampleRate)
    private val filterEnv = Adsr(sampleRate)
    private val filter = SvfLowPass(sampleRate)
    private var phase1 = 0.0
    private var phase2 = 0.0
    private var inc1 = 0.0
    private var inc2 = 0.0
    private var velocity = 0f
    private var counter = 0
    override var startedAt = 0L
        private set

    override val isActive: Boolean get() = amp.isActive

    override fun start(midi: Int, velocity: Float, at: Long) {
        val hz = midiToHz(midi.toDouble())
        inc1 = hz / sampleRate
        inc2 = hz * 2.0.pow(patch.osc.cents / CENTS_PER_OCTAVE) / sampleRate
        this.velocity = velocity
        startedAt = at
        filter.reset()
        amp.noteOn(patch.amp)
        filterEnv.noteOn(patch.filter.env)
        counter = 0
    }

    override fun release() {
        amp.noteOff()
        filterEnv.noteOff()
    }

    override fun steal() {
        amp.kill()
    }

    override fun next(): Float {
        val envelope = amp.next()
        val sweep = filterEnv.next()
        if (counter % CONTROL_RATE == 0) {
            filter.setCutoff(patch.filter.cutoffHz * 2f.pow(patch.filter.envOctaves * sweep), patch.filter.resonance)
        }
        counter++
        val o1 = oscillator(patch.osc.first, phase1, inc1)
        val o2 = oscillator(patch.osc.second, phase2, inc2)
        phase1 = (phase1 + inc1) % 1.0
        phase2 = (phase2 + inc2) % 1.0
        val mixed = (o1 + patch.osc.level * o2) / (1f + patch.osc.level)
        return filter.process(mixed) * envelope * velocity * patch.gain
    }

    private fun oscillator(wave: Waveform, phase: Double, inc: Double): Float = when (wave) {
        Waveform.SINE -> sin(2 * PI * phase).toFloat()
        Waveform.SAW -> (2 * phase - 1 - polyBlep(phase, inc)).toFloat()
        Waveform.SQUARE -> {
            val naive = if (phase < 0.5) 1.0 else -1.0
            (naive + polyBlep(phase, inc) - polyBlep((phase + 0.5) % 1.0, inc)).toFloat()
        }
    }

    /** PolyBLEP correction against aliasing at the waveform's discontinuity. */
    private fun polyBlep(t: Double, dt: Double): Double = when {
        t < dt -> {
            val x = t / dt
            x + x - x * x - 1.0
        }
        t > 1.0 - dt -> {
            val x = (t - 1.0) / dt
            x * x + x + x + 1.0
        }
        else -> 0.0
    }

    private companion object {
        const val CONTROL_RATE = 16
    }
}

/**
 * Sampler voice (spec §2.6 Sampler): plays the nearest multisample, pitch-shifted by
 * resampling with linear interpolation, shaped by an ADSR.
 */
class SamplerVoice(
    sampleRate: Int,
    private val set: SampleSet,
    private val envelope: Adsr.Settings,
    private val gain: Float,
) : Voice {
    private val amp = Adsr(sampleRate)
    private var sample: FloatArray = FloatArray(0)
    private var position = 0.0
    private var rate = 1.0
    private var velocity = 0f
    override var startedAt = 0L
        private set

    override val isActive: Boolean get() = amp.isActive && position < sample.size - 1

    override fun start(midi: Int, velocity: Float, at: Long) {
        val index = set.nearest(midi)
        sample = set.samples[index]
        rate = 2.0.pow((midi - set.roots[index]) / SEMITONES)
        position = 0.0
        this.velocity = velocity
        startedAt = at
        amp.noteOn(envelope)
    }

    override fun release() {
        amp.noteOff()
    }

    override fun steal() {
        amp.kill()
    }

    override fun next(): Float {
        val i = position.toInt()
        if (i >= sample.size - 1) return 0f
        val frac = (position - i).toFloat()
        val v = sample[i] + (sample[i + 1] - sample[i]) * frac
        position += rate
        return v * amp.next() * velocity * gain
    }
}
