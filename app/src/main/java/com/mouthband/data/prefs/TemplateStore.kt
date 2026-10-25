package com.mouthband.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mouthband.audio.AudioLog
import com.mouthband.convert.BeatboxTemplates
import com.mouthband.data.StorageJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException

/** A damaged file loses the taught sounds (the rule fallback takes over) instead of crashing. */
private val Context.templateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "beatbox_templates",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** The user's taught beatbox sounds (spec §1 Teach your sounds), stored per user in DataStore. */
class TemplateStore(context: Context) {
    private val store = context.applicationContext.templateDataStore

    val templates: Flow<BeatboxTemplates?> = store.data.map { prefs ->
        prefs[KEY]?.let { json ->
            try {
                StorageJson.decodeFromString(BeatboxTemplates.serializer(), json)
            } catch (e: SerializationException) {
                AudioLog.w("templates json", e)
                null
            } catch (e: IllegalArgumentException) {
                AudioLog.w("templates json", e)
                null
            }
        }
    }

    suspend fun current(): BeatboxTemplates? = templates.first()

    suspend fun save(templates: BeatboxTemplates) {
        store.edit { it[KEY] = StorageJson.encodeToString(BeatboxTemplates.serializer(), templates) }
    }

    /** "Reset" clears the taught sounds; the rule fallback is used again. */
    suspend fun reset() {
        store.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("templates_json")
    }
}
