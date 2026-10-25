package com.mouthband.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** How the recorded stream is mapped onto the transport (see DECISIONS.md, latency). */
enum class LatencySource {
    /** Not calibrated (or calibration failed): counter mapping with the 150 ms default. */
    FALLBACK,

    /** Calibrated with hardware timestamps of both streams. */
    TIMESTAMP,

    /** Calibrated with the frames-written counter (device without timestamps). */
    COUNTER,
}

data class AudioSettings(
    val latencyMs: Int = DEFAULT_LATENCY_MS,
    val latencySource: LatencySource = LatencySource.FALLBACK,
    val clickOn: Boolean = true,
    val countInBars: Int = 1,
) {
    companion object {
        /** Spec §2.2: fallback round-trip latency when calibration fails or never ran. */
        const val DEFAULT_LATENCY_MS = 150

        /** Anything beyond this is a damaged value, not a phone. */
        const val MAX_LATENCY_MS = 1000
    }
}

/** The most recently kept take, for the latency nudge (spec §2.2). */
data class LastTake(val path: String, val bpm: Int, val loopBars: Int)

data class AppFlags(
    val onboardingDone: Boolean = false,
    val sessionCount: Int = 0,
    val lastInterstitialAt: Long = 0L,
    val teachPromptShown: Boolean = false,
)

/** A damaged settings file is replaced by the defaults instead of crashing the app at start. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** App settings and flags in DataStore (spec §2.2, §1 Settings, §5). */
class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    val audio: Flow<AudioSettings> = store.data.map { p ->
        AudioSettings(
            latencyMs = (p[LATENCY_MS] ?: AudioSettings.DEFAULT_LATENCY_MS).coerceIn(0, AudioSettings.MAX_LATENCY_MS),
            latencySource = p[LATENCY_SOURCE]?.let { name -> LatencySource.entries.firstOrNull { it.name == name } }
                ?: LatencySource.FALLBACK,
            clickOn = p[CLICK_ON] ?: true,
            countInBars = (p[COUNT_IN] ?: 1).coerceIn(1, 2),
        )
    }

    val flags: Flow<AppFlags> = store.data.map { p ->
        AppFlags(
            onboardingDone = p[ONBOARDING_DONE] ?: false,
            sessionCount = p[SESSION_COUNT] ?: 0,
            lastInterstitialAt = p[LAST_INTERSTITIAL] ?: 0L,
            teachPromptShown = p[TEACH_PROMPT] ?: false,
        )
    }

    val lastTake: Flow<LastTake?> = store.data.map { p ->
        val path = p[LAST_TAKE_PATH]
        val bpm = p[LAST_TAKE_BPM]
        val bars = p[LAST_TAKE_BARS]
        if (path != null && bpm != null && bars != null) LastTake(path, bpm, bars) else null
    }

    suspend fun audioNow(): AudioSettings = audio.first()

    suspend fun setLastTake(take: LastTake) {
        store.edit {
            it[LAST_TAKE_PATH] = take.path
            it[LAST_TAKE_BPM] = take.bpm
            it[LAST_TAKE_BARS] = take.loopBars
        }
    }

    suspend fun flagsNow(): AppFlags = flags.first()

    suspend fun setLatency(ms: Int, source: LatencySource) {
        store.edit {
            it[LATENCY_MS] = ms
            it[LATENCY_SOURCE] = source.name
        }
    }

    suspend fun setClickOn(on: Boolean) {
        store.edit { it[CLICK_ON] = on }
    }

    suspend fun setCountInBars(bars: Int) {
        store.edit { it[COUNT_IN] = bars.coerceIn(1, 2) }
    }

    suspend fun setOnboardingDone() {
        store.edit { it[ONBOARDING_DONE] = true }
    }

    suspend fun setTeachPromptShown() {
        store.edit { it[TEACH_PROMPT] = true }
    }

    /** Counts app starts; the interstitial is never shown in the first session (spec §5). */
    suspend fun incrementSessionCount(): Int {
        var count = 0
        store.edit {
            count = (it[SESSION_COUNT] ?: 0) + 1
            it[SESSION_COUNT] = count
        }
        return count
    }

    suspend fun setLastInterstitialAt(time: Long) {
        store.edit { it[LAST_INTERSTITIAL] = time }
    }

    private companion object {
        val LATENCY_MS = intPreferencesKey("latency_ms")
        val LATENCY_SOURCE = stringPreferencesKey("latency_source")
        val CLICK_ON = booleanPreferencesKey("click_on")
        val COUNT_IN = intPreferencesKey("count_in_bars")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val SESSION_COUNT = intPreferencesKey("session_count")
        val LAST_INTERSTITIAL = longPreferencesKey("last_interstitial_at")
        val TEACH_PROMPT = booleanPreferencesKey("teach_prompt_shown")
        val LAST_TAKE_PATH = stringPreferencesKey("last_take_path")
        val LAST_TAKE_BPM = intPreferencesKey("last_take_bpm")
        val LAST_TAKE_BARS = intPreferencesKey("last_take_bars")
    }
}
