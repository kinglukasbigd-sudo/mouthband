package com.mouthband.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Spec §4 SongEntity. [arrangementJson] holds [com.mouthband.model.Arrangement]. */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val bpm: Int,
    val keyRoot: Int,
    val keyMode: String,
    val loopBars: Int,
    val kitId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val coachStep: Int,
    val arrangementJson: String,
)

/** Spec §4 TrackEntity. [eventsJson] holds [com.mouthband.model.TrackEvents]. */
@Entity(
    tableName = "tracks",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["songId", "type"], unique = true)],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val type: String,
    val takeFilePath: String?,
    val eventsJson: String?,
    val instrumentId: String,
    val quantizeStrength: Float,
    val gainDb: Float,
    val pan: Float,
    val muted: Boolean,
    val takeVersion: Int,
)

/** Spec §4 ExportEntity. */
@Entity(
    tableName = "exports",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("songId")],
)
data class ExportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val uri: String,
    val format: String,
    val durationMs: Long,
    val createdAt: Long,
)
