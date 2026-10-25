package com.mouthband.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    fun observe(id: Long): Flow<SongEntity?>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun get(id: Long): SongEntity?

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun count(): Int

    @Insert
    suspend fun insert(song: SongEntity): Long

    @Update
    suspend fun update(song: SongEntity)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE songId = :songId ORDER BY id")
    fun observeForSong(songId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY songId, id")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE songId = :songId ORDER BY id")
    suspend fun getForSong(songId: Long): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE songId = :songId AND type = :type")
    suspend fun get(songId: Long, type: String): TrackEntity?

    @Insert
    suspend fun insertAll(tracks: List<TrackEntity>)

    @Update
    suspend fun update(track: TrackEntity)
}

@Dao
interface ExportDao {
    @Insert
    suspend fun insert(export: ExportEntity): Long

    @Query("SELECT * FROM exports ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ExportEntity>>
}
