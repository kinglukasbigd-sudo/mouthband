package com.mouthband.ads

/** When an interstitial may appear (spec §5). Pure, so it is unit-tested. */
object AdPolicy {
    /** At most one interstitial every 4 minutes. */
    const val INTERSTITIAL_INTERVAL_MS = 4 * 60 * 1000L

    /** The first app session never shows one. */
    const val FIRST_SESSION = 1

    /**
     * True when an interstitial may follow a finished export: not in the first session, at least
     * 4 minutes after the previous one, and only while no audio is recording or playing.
     */
    fun interstitialAllowed(sessionCount: Int, lastShownAt: Long, now: Long, audioIdle: Boolean): Boolean =
        audioIdle &&
            sessionCount > FIRST_SESSION &&
            (lastShownAt == 0L || now - lastShownAt >= INTERSTITIAL_INTERVAL_MS)
}
