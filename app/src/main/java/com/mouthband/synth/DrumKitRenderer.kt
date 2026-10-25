package com.mouthband.synth

import com.mouthband.audio.LoopMath
import com.mouthband.audio.PanLaw
import com.mouthband.model.DrumClass
import com.mouthband.model.DrumHit
import com.mouthband.model.DrumKit
import kotlin.math.roundToInt

/**
 * Renders quantized drum hits into a seamless stereo loop buffer (spec §2.6 Rendering): the
 * kick/snare/hi-hat classes map onto the kit's five pieces (snares get a clap layer in Trap and
 * Pop, an off-beat hi-hat followed by space opens up). Tails wrap around the loop end.
 */
class DrumKitRenderer(private val library: InstrumentLibrary) {
    private class Voice(val piece: DrumPiece, val gain: Float, pan: Float) {
        val left = PanLaw.left(0f, pan)
        val right = PanLaw.right(0f, pan)
    }

    fun render(hits: List<DrumHit>, kit: DrumKit, loopFrames: Int, stepFrames: Double): FloatArray {
        val out = FloatArray(loopFrames * 2)
        val totalSteps = (loopFrames / stepFrames).roundToInt().coerceAtLeast(1)
        val hatSteps = hits.filter { it.cls == DrumClass.HIHAT }
            .map { (it.frameInLoop / stepFrames).roundToInt() % totalSteps }
            .sorted()
        for (hit in hits) {
            val step = (hit.frameInLoop / stepFrames).roundToInt() % totalSteps
            for (voice in voicesFor(hit.cls, kit, step, hatSteps, totalSteps)) {
                val gain = voice.gain * hit.velocity
                mix(out, library.drum(kit, voice.piece), hit.frameInLoop, gain * voice.left, gain * voice.right)
            }
        }
        return out
    }

    private fun voicesFor(cls: DrumClass, kit: DrumKit, step: Int, hatSteps: List<Int>, totalSteps: Int): List<Voice> =
        when (cls) {
            DrumClass.KICK -> listOf(Voice(DrumPiece.KICK, KICK_GAIN, 0f))
            DrumClass.SNARE -> buildList {
                add(Voice(DrumPiece.SNARE, SNARE_GAIN, 0f))
                if (kit == DrumKit.TRAP || kit == DrumKit.POP) add(Voice(DrumPiece.CLAP, CLAP_GAIN, CLAP_PAN))
            }
            DrumClass.HIHAT -> {
                val open = isOpenHat(step, hatSteps, totalSteps)
                val voice = if (open) {
                    Voice(DrumPiece.HAT_OPEN, OPEN_GAIN, HAT_PAN)
                } else {
                    Voice(DrumPiece.HAT_CLOSED, HAT_GAIN, HAT_PAN)
                }
                listOf(voice)
            }
        }

    /** An off-beat 8th hat with no other hat for the next 3 steps rings open. */
    private fun isOpenHat(step: Int, hatSteps: List<Int>, totalSteps: Int): Boolean {
        if (step % LoopMath.STEPS_PER_BEAT != OFFBEAT_STEP) return false
        val next = hatSteps.firstOrNull { it > step } ?: (hatSteps.firstOrNull()?.plus(totalSteps) ?: return false)
        return next - step >= OPEN_GAP_STEPS
    }

    /** Adds [sound] at [start], wrapping around the end of the loop buffer [out]. */
    private fun mix(out: FloatArray, sound: FloatArray, start: Int, gl: Float, gr: Float) {
        val loopFrames = out.size / 2
        for (n in sound.indices) {
            val pos = (start + n) % loopFrames
            out[2 * pos] += sound[n] * gl
            out[2 * pos + 1] += sound[n] * gr
        }
    }

    private companion object {
        const val KICK_GAIN = 1.0f
        const val SNARE_GAIN = 0.8f
        const val CLAP_GAIN = 0.55f
        const val HAT_GAIN = 0.45f
        const val OPEN_GAIN = 0.4f
        const val HAT_PAN = 0.15f
        const val CLAP_PAN = -0.1f
        const val OFFBEAT_STEP = 2
        const val OPEN_GAP_STEPS = 3
    }
}
