package com.mouthband.data

import android.database.SQLException
import androidx.room.withTransaction
import com.mouthband.data.db.MouthBandDatabase
import com.mouthband.model.Instrument
import com.mouthband.model.Mood
import com.mouthband.model.Track
import com.mouthband.model.TrackEvents
import com.mouthband.model.TrackKind
import com.mouthband.model.TrackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Per-track data of a song (spec §4): rows plus take files. Every kept take and every edit is
 * written immediately (autosave), file first, then the row, so process death can never lose a
 * kept take. Each write also bumps the song's `updatedAt`.
 */
class TrackRepository(
    private val db: MouthBandDatabase,
    private val takes: TakeStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val songs = db.songs()
    private val tracks = db.tracks()

    fun observe(songId: Long): Flow<List<Track>> =
        tracks.observeForSong(songId).map { list -> list.map { it.toDomain() } }

    suspend fun getAll(songId: Long): List<Track> = tracks.getForSong(songId).map { it.toDomain() }

    /** Coach step 1: a mood resets tempo, key, kit and the default instruments. */
    suspend fun applyMood(songId: Long, mood: Mood) {
        val song = songs.get(songId)?.toDomain() ?: return
        db.withTransaction {
            songs.update(song.copy(bpm = mood.bpm, key = mood.key, kit = mood.kit, updatedAt = clock()).toEntity())
            tracks.getForSong(songId).forEach { row ->
                val type = TrackType.entries.firstOrNull { it.name == row.type }
                if (type?.kind == TrackKind.PITCHED) {
                    tracks.update(row.copy(instrumentId = mood.instrumentFor(type).name))
                }
            }
        }
    }

    /**
     * Keeps a take: writes the raw loop atomically (when given), then stores the row with the
     * new version, events and settings, then removes the previous version's file.
     */
    @Throws(IOException::class)
    suspend fun keepTake(songId: Long, type: TrackType, take: KeptTake): Track {
        val current = tracks.get(songId, type.name) ?: throw IOException("no track row $type")
        val version = current.takeVersion + 1
        val file = take.rawLoop?.let { raw ->
            withContext(Dispatchers.IO) { takes.write(songId, type, version, take.sampleRate, raw) }
        }
        val updated = current.toDomain().copy(
            takeFilePath = file?.absolutePath,
            events = take.events,
            instrument = take.instrument,
            quantizeStrength = take.quantizeStrength,
            takeVersion = version,
        )
        try {
            db.withTransaction {
                tracks.update(updated.toEntity())
                touch(songId)
            }
        } catch (e: SQLException) {
            // The row is not written: do not leave the new file behind as an orphan.
            withContext(Dispatchers.IO) { takes.delete(file?.absolutePath) }
            throw IOException("could not save $type", e)
        }
        val previous = current.takeFilePath
        if (previous != file?.absolutePath) withContext(Dispatchers.IO) { takes.delete(previous) }
        return updated
    }

    /** Saves an edit to a track (events, sound, timing, gain, pan, mute). */
    suspend fun update(track: Track) {
        db.withTransaction {
            tracks.update(track.toEntity())
            touch(track.songId)
        }
    }

    private suspend fun touch(songId: Long) {
        songs.get(songId)?.let { songs.update(it.copy(updatedAt = clock())) }
    }
}

/** What a kept take contributes: raw audio (always kept, for A/B and vocals) and converted events. */
class KeptTake(
    val rawLoop: FloatArray?,
    val sampleRate: Int,
    val events: TrackEvents?,
    val instrument: Instrument,
    val quantizeStrength: Float,
)
