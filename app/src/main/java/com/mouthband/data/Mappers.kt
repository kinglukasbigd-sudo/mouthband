package com.mouthband.data

import com.mouthband.audio.AudioLog
import com.mouthband.data.db.ExportEntity
import com.mouthband.data.db.SongEntity
import com.mouthband.data.db.TrackEntity
import com.mouthband.model.Arrangement
import com.mouthband.model.CoachStep
import com.mouthband.model.DrumKit
import com.mouthband.model.ExportFormat
import com.mouthband.model.ExportRecord
import com.mouthband.model.Instrument
import com.mouthband.model.KeyMode
import com.mouthband.model.MusicalKey
import com.mouthband.model.Song
import com.mouthband.model.Track
import com.mouthband.model.TrackEvents
import com.mouthband.model.TrackType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** JSON used for the arrangement and event columns. Unknown keys are tolerated for forward compatibility. */
val StorageJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun SongEntity.toDomain(): Song = Song(
    id = id,
    title = title,
    bpm = bpm,
    key = MusicalKey(keyRoot, enumOr(keyMode, KeyMode.MAJOR)),
    loopBars = loopBars,
    kit = enumOr(kitId, DrumKit.BOOM_BAP),
    createdAt = createdAt,
    updatedAt = updatedAt,
    coachStep = CoachStep.fromNumber(coachStep),
    arrangement = decodeArrangement(arrangementJson),
)

fun Song.toEntity(): SongEntity = SongEntity(
    id = id,
    title = title,
    bpm = bpm,
    keyRoot = key.root,
    keyMode = key.mode.name,
    loopBars = loopBars,
    kitId = kit.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    coachStep = coachStep.number,
    arrangementJson = StorageJson.encodeToString(Arrangement.serializer(), arrangement),
)

fun TrackEntity.toDomain(): Track = Track(
    id = id,
    songId = songId,
    type = enumOr(type, TrackType.BEAT),
    takeFilePath = takeFilePath,
    events = eventsJson?.let(::decodeEvents),
    instrument = enumOr(instrumentId, Instrument.VOICE),
    quantizeStrength = quantizeStrength,
    gainDb = gainDb,
    pan = pan,
    muted = muted,
    takeVersion = takeVersion,
)

fun Track.toEntity(): TrackEntity = TrackEntity(
    id = id,
    songId = songId,
    type = type.name,
    takeFilePath = takeFilePath,
    eventsJson = events?.let { StorageJson.encodeToString(TrackEvents.serializer(), it) },
    instrumentId = instrument.name,
    quantizeStrength = quantizeStrength,
    gainDb = gainDb,
    pan = pan,
    muted = muted,
    takeVersion = takeVersion,
)

fun ExportEntity.toDomain(): ExportRecord = ExportRecord(
    id = id,
    songId = songId,
    uri = uri,
    format = enumOr(format, ExportFormat.M4A),
    durationMs = durationMs,
    createdAt = createdAt,
)

private fun decodeArrangement(json: String): Arrangement = try {
    StorageJson.decodeFromString(Arrangement.serializer(), json)
} catch (e: SerializationException) {
    AudioLog.w("arrangement json", e)
    Arrangement.Default
} catch (e: IllegalArgumentException) {
    AudioLog.w("arrangement json", e)
    Arrangement.Default
}

private fun decodeEvents(json: String): TrackEvents? = try {
    StorageJson.decodeFromString(TrackEvents.serializer(), json)
} catch (e: SerializationException) {
    AudioLog.w("events json", e)
    null
} catch (e: IllegalArgumentException) {
    AudioLog.w("events json", e)
    null
}

private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback
