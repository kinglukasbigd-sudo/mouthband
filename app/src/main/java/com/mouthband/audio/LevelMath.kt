package com.mouthband.audio

import kotlin.math.log10
import kotlin.math.max

/** Coarse verdict for the mic meter (spec §1: Too quiet / Good / Too loud). */
enum class MicVerdict { TOO_QUIET, GOOD, TOO_LOUD }

/** One meter reading. [holdDb] is the recent loudest RMS, used for the verdict. */
data class MicLevel(val rmsDb: Float, val peakDb: Float, val holdDb: Float) {
    val meterFraction: Float get() = LevelMath.meterFraction(rmsDb)
    val verdict: MicVerdict get() = LevelMath.verdict(holdDb, peakDb)

    companion object {
        val Silent = MicLevel(LevelMath.FLOOR_DB, LevelMath.FLOOR_DB, LevelMath.FLOOR_DB)
    }
}

object LevelMath {
    const val FLOOR_DB = -90f
    private const val METER_RANGE_DB = 60f
    private const val TOO_QUIET_DB = -40f
    private const val TOO_LOUD_RMS_DB = -6f
    private const val CLIP_PEAK_DB = -0.5f
    private const val DB_PER_DECADE = 20f
    private const val MIN_AMPLITUDE = 1e-9f

    fun amplitudeToDb(amplitude: Float): Float = DB_PER_DECADE * log10(max(amplitude, MIN_AMPLITUDE))

    /** Maps -60..0 dBFS to 0..1 of the 20-segment meter. */
    fun meterFraction(db: Float): Float = ((db + METER_RANGE_DB) / METER_RANGE_DB).coerceIn(0f, 1f)

    fun verdict(holdDb: Float, peakDb: Float): MicVerdict = when {
        peakDb >= CLIP_PEAK_DB || holdDb >= TOO_LOUD_RMS_DB -> MicVerdict.TOO_LOUD
        holdDb < TOO_QUIET_DB -> MicVerdict.TOO_QUIET
        else -> MicVerdict.GOOD
    }
}
