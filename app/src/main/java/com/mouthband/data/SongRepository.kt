package com.mouthband.data

import androidx.room.withTransaction
import com.mouthband.data.db.ExportEntity
import com.mouthband.data.db.MouthBandDatabase
import com.mouthband.data.db.TrackEntity
import com.mouthband.model.Arrangement
import com.mouthband.model.CoachStep
import com.mouthband.model.ExportFormat
import com.mouthband.model.Mood
import com.mouthband.model.MusicalKey
import com.mouthband.model.Song
import com.mouthband.model.Track
import com.mouthband.model.TrackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** A song plus what Home needs for its card. */
data class SongOverview(val song: Song, val tracks: List<Track>, val lastExportAt: Long?)

/**
 * The single source of truth for songs and exports (spec §4), exposed as Flows; per-track data
 * lives in [TrackRepository]. Every edit is written immediately (autosave).
 */
class SongRepository(
    private val db: MouthBandDatabase,
    private val takes: TakeStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val songs = db.songs()
    private val tracks = db.tracks()
    private val exports = db.exports()

    fun overviews(): Flow<List<SongOverview>> =
        combine(songs.observeAll(), tracks.observeAll(), exports.observeAll()) { s, t, e ->
            val bySong = t.groupBy { it.songId }
            val lastExport = e.groupBy { it.songId }.mapValues { (_, list) -> list.maxOf { it.createdAt } }
            s.map { song ->
                SongOverview(song.toDomain(), bySong[song.id].orEmpty().map { it.toDomain() }, lastExport[song.id])
            }
        }

    fun song(id: Long): Flow<Song?> = songs.observe(id).map { it?.toDomain() }

    suspend fun getSong(id: Long): Song? = songs.get(id)?.toDomain()

    suspend fun nextDefaultNumber(): Int = songs.count() + 1

    /** Creates a song with the given mood's defaults and all six empty track rows. */
    suspend fun createSong(title: String, mood: Mood, loopBars: Int = Track.LOOP_BARS_SHORT): Long {
        val now = clock()
        val song = Song(0, title, mood.bpm, mood.key, loopBars, mood.kit, now, now, CoachStep.VIBE, Arrangement.Default)
        return db.withTransaction {
            val id = songs.insert(song.toEntity())
            tracks.insertAll(TrackType.entries.map { emptyTrack(id, it, mood) })
            id
        }
    }

    suspend fun updateSong(song: Song) {
        songs.update(song.copy(updatedAt = clock()).toEntity())
    }

    /** Copies the song, its rows and its take files. */
    suspend fun duplicate(songId: Long, title: String): Long? {
        val song = getSong(songId) ?: return null
        val rows = tracks.getForSong(songId)
        val now = clock()
        return db.withTransaction {
            val id = songs.insert(song.copy(id = 0, title = title, createdAt = now, updatedAt = now).toEntity())
            val paths = withContext(Dispatchers.IO) { takes.copySong(songId, id) }
            tracks.insertAll(rows.map { it.copy(id = 0, songId = id, takeFilePath = it.takeFilePath?.let(paths::get)) })
            id
        }
    }

    suspend fun delete(songId: Long) {
        songs.delete(songId)
        withContext(Dispatchers.IO) { takes.deleteSong(songId) }
    }

    suspend fun addExport(songId: Long, uri: String, format: ExportFormat, durationMs: Long) {
        exports.insert(
            ExportEntity(
                songId = songId,
                uri = uri,
                format = format.name,
                durationMs = durationMs,
                createdAt = clock(),
            ),
        )
    }

    private fun emptyTrack(songId: Long, type: TrackType, mood: Mood): TrackEntity = TrackEntity(
        songId = songId,
        type = type.name,
        takeFilePath = null,
        eventsJson = null,
        instrumentId = mood.instrumentFor(type).name,
        quantizeStrength = Track.DEFAULT_QUANTIZE,
        gainDb = 0f,
        pan = 0f,
        muted = false,
        takeVersion = 0,
    )
}

/** Renames a song. */
suspend fun SongRepository.rename(songId: Long, title: String) {
    val song = getSong(songId) ?: return
    updateSong(song.copy(title = title))
}

/** Coach step 1 manual pickers. */
suspend fun SongRepository.setVibe(songId: Long, bpm: Int, key: MusicalKey, loopBars: Int) {
    val song = getSong(songId) ?: return
    updateSong(song.copy(bpm = bpm.coerceIn(Mood.MIN_BPM, Mood.MAX_BPM), key = key, loopBars = loopBars))
}

/** Moves the coach forward; never backwards. */
suspend fun SongRepository.reachStep(songId: Long, step: CoachStep) {
    val song = getSong(songId) ?: return
    if (step.number > song.coachStep.number) updateSong(song.copy(coachStep = step))
}
