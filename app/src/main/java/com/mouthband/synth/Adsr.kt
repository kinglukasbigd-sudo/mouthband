package com.mouthband.synth

/** Linear ADSR envelope, one per voice. Times in seconds, [sustain] 0..1. Allocation-free. */
class Adsr(private val sampleRate: Int) {
    class Settings(val attack: Float, val decay: Float, val sustain: Float, val release: Float)

    private enum class Stage { IDLE, ATTACK, DECAY, SUSTAIN, RELEASE }

    private var stage = Stage.IDLE
    private var level = 0f
    private var attackStep = 0f
    private var decayStep = 0f
    private var releaseStep = 0f
    private var releaseSeconds = 0f
    private var sustain = 0f

    val isActive: Boolean get() = stage != Stage.IDLE

    fun noteOn(settings: Settings) {
        sustain = settings.sustain.coerceIn(0f, 1f)
        attackStep = step(settings.attack, 1f)
        decayStep = step(settings.decay, 1f - sustain)
        releaseSeconds = settings.release
        stage = Stage.ATTACK
    }

    fun noteOff() {
        if (stage != Stage.IDLE && stage != Stage.RELEASE) {
            releaseStep = step(releaseSeconds, level)
            stage = Stage.RELEASE
        }
    }

    /** Fast fade used when a voice is stolen. */
    fun kill() {
        releaseStep = step(STEAL_SECONDS, level)
        stage = Stage.RELEASE
    }

    fun next(): Float {
        when (stage) {
            Stage.IDLE -> return 0f
            Stage.ATTACK -> {
                level += attackStep
                if (level >= 1f) {
                    level = 1f
                    stage = Stage.DECAY
                }
            }
            Stage.DECAY -> {
                level -= decayStep
                if (level <= sustain) {
                    level = sustain
                    stage = Stage.SUSTAIN
                }
            }
            Stage.SUSTAIN -> if (sustain <= 0f) stage = Stage.IDLE
            Stage.RELEASE -> {
                level -= releaseStep
                if (level <= 0f) {
                    level = 0f
                    stage = Stage.IDLE
                }
            }
        }
        return level
    }

    private fun step(seconds: Float, span: Float): Float {
        val samples = seconds * sampleRate
        return if (samples < 1f) span.coerceAtLeast(MIN_STEP) else (span / samples).coerceAtLeast(MIN_STEP)
    }

    private companion object {
        const val STEAL_SECONDS = 0.005f
        const val MIN_STEP = 1e-7f
    }
}
