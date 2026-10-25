package com.mouthband.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Equal-power pan law shared by the coach [LoopPlayer] and the song mixer, so a track sounds
 * the same in a recording step, in Arrange and in the export. Unity gain at the centre.
 */
object PanLaw {
    private const val QUARTER_PI = PI / 4.0
    private const val SQRT2 = 1.4142135623730951
    private const val DB_PER_DECADE = 20.0

    fun dbToLinear(db: Float): Double = 10.0.pow(db / DB_PER_DECADE)

    fun left(gainDb: Float, pan: Float): Float = (dbToLinear(gainDb) * cos(angle(pan)) * SQRT2).toFloat()

    fun right(gainDb: Float, pan: Float): Float = (dbToLinear(gainDb) * sin(angle(pan)) * SQRT2).toFloat()

    private fun angle(pan: Float): Double = (pan.coerceIn(-1f, 1f) + 1f) * QUARTER_PI
}
