package com.mouthband.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdPolicyTest {
    private val now = 10_000_000L
    private val fourMinutes = AdPolicy.INTERSTITIAL_INTERVAL_MS

    @Test
    fun neverInTheFirstSession() {
        assertFalse(AdPolicy.interstitialAllowed(sessionCount = 1, lastShownAt = 0L, now = now, audioIdle = true))
        assertTrue(AdPolicy.interstitialAllowed(sessionCount = 2, lastShownAt = 0L, now = now, audioIdle = true))
    }

    @Test
    fun atMostOnceEveryFourMinutes() {
        assertFalse(AdPolicy.interstitialAllowed(3, lastShownAt = now - fourMinutes + 1, now = now, audioIdle = true))
        assertTrue(AdPolicy.interstitialAllowed(3, lastShownAt = now - fourMinutes, now = now, audioIdle = true))
        assertEquals(240_000L, fourMinutes)
    }

    @Test
    fun neverWhileAudioIsRecordingOrPlaying() {
        assertFalse(AdPolicy.interstitialAllowed(5, lastShownAt = 0L, now = now, audioIdle = false))
    }
}
