package com.mouthband.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live pitch line's outlier filter: steps stay exact, short spikes go, pauses stay empty. */
class PitchSmootherTest {
    private val nan = Float.NaN

    private fun smooth(vararg input: Float): List<Float> {
        val out = ArrayList<Float>()
        val smoother = PitchSmoother { _, midi -> out.add(midi) }
        input.forEachIndexed { i, v -> smoother.push(i.toLong(), v) }
        return out
    }

    @Test
    fun aSteadyNoteIsUnchanged() {
        assertEquals(listOf(60f, 60f, 60f), smooth(60f, 60f, 60f, 60f, 60f, 60f, 60f))
    }

    @Test
    fun aSingleSpikeIsRemoved() {
        assertEquals(listOf(60f, 60f, 60f), smooth(60f, 60f, 72f, 60f, 60f, 60f, 60f).take(3))
    }

    @Test
    fun aTwoEstimateSpikeIsRemoved() {
        val out = smooth(60f, 60f, 48f, 72f, 60f, 60f, 60f, 60f)
        assertTrue("$out", out.all { it == 60f })
    }

    @Test
    fun aStepToTheNextNoteStaysExactAndIsNotDelayed() {
        // Output i belongs to input i + 2: the step at input index 5 appears at output index 3.
        val out = smooth(60f, 60f, 60f, 60f, 60f, 67f, 67f, 67f, 67f, 67f)
        assertEquals(listOf(60f, 60f, 60f, 67f, 67f, 67f), out)
    }

    @Test
    fun pausesStayEmptyAndAnIsolatedBlipIsShownAsHeard() {
        // Output i belongs to input i + 2: the lone 60 (input 2) is output 0, the pauses around it stay NaN.
        val out = smooth(nan, nan, 60f, nan, nan, nan, nan)
        assertEquals(60f, out[0], 0f)
        assertTrue(out[1].isNaN())
        assertTrue(out[2].isNaN())
    }

    @Test
    fun theLineIsEmittedAtTheCentreOfItsWindow() {
        val centres = ArrayList<Long>()
        val smoother = PitchSmoother { centre, _ -> centres.add(centre) }
        for (i in 0 until 6) smoother.push(100L + i * 10, 60f)
        assertEquals(listOf(120L, 130L), centres)
    }
}
