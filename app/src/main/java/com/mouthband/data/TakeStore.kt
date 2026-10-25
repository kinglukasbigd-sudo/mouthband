package com.mouthband.data

import com.mouthband.audio.TakeFile
import com.mouthband.model.TrackType
import java.io.File
import java.io.IOException

/**
 * Raw take files (spec §4): `filesDir/songs/{songId}/{trackType}_{version}.f32`, written
 * atomically (temp file + rename). Deleting a song deletes its folder.
 */
class TakeStore(private val filesDir: File) {
    fun songDir(songId: Long): File = File(filesDir, "songs/$songId")

    fun takeFile(songId: Long, type: TrackType, version: Int): File =
        File(songDir(songId), "${type.name.lowercase()}_$version.f32")

    /** Scratch file for an in-progress recording. */
    fun recordingTemp(songId: Long): File = File(songDir(songId), "recording.tmp")

    @Throws(IOException::class)
    fun write(songId: Long, type: TrackType, version: Int, sampleRate: Int, samples: FloatArray): File {
        val file = takeFile(songId, type, version)
        TakeFile.write(file, sampleRate, samples)
        return file
    }

    @Throws(IOException::class)
    fun read(path: String): TakeFile.Audio = TakeFile.read(File(path))

    fun delete(path: String?) {
        if (path != null) File(path).delete()
    }

    fun deleteSong(songId: Long) {
        songDir(songId).deleteRecursively()
    }

    /**
     * Removes scratch files an interrupted recording or write left behind (`*.tmp`); returns how
     * many. Run at start, when nothing can be recording.
     */
    fun sweepTemporaryFiles(): Int =
        File(filesDir, "songs").walkTopDown().filter { it.isFile && it.extension == "tmp" }.count { it.delete() }

    /** Copies every take of [from] into [to]'s folder; returns old path → new path. */
    @Throws(IOException::class)
    fun copySong(from: Long, to: Long): Map<String, String> {
        val source = songDir(from)
        val target = songDir(to)
        target.mkdirs()
        return source.listFiles { f -> f.extension == "f32" }.orEmpty().associate { file ->
            val copy = File(target, file.name)
            file.copyTo(copy, overwrite = true)
            file.absolutePath to copy.absolutePath
        }
    }
}
