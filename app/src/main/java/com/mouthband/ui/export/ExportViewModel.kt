package com.mouthband.ui.export

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mouthband.AppContainer
import com.mouthband.audio.AudioLog
import com.mouthband.data.SongRepository
import com.mouthband.data.TrackRepository
import com.mouthband.data.reachStep
import com.mouthband.export.ExportRenderer
import com.mouthband.export.ExportResult
import com.mouthband.mix.MixLoader
import com.mouthband.mix.SongMixer
import com.mouthband.mix.SongPlayer
import com.mouthband.model.CoachStep
import com.mouthband.model.ExportFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Where an export is. */
sealed interface ExportStage {
    data class Rendering(val progress: Float) : ExportStage

    data class Done(val result: ExportResult) : ExportStage

    data object Failed : ExportStage

    data object Empty : ExportStage
}

enum class ExportNotice { SAVED, SAVE_FAILED, PLAYBACK_FAILED }

data class ExportState(
    val stage: ExportStage = ExportStage.Rendering(0f),
    val m4a: Boolean = true,
    val playing: Boolean = false,
    val notice: ExportNotice? = null,
) {
    val format: ExportFormat get() = if (m4a) ExportFormat.M4A else ExportFormat.WAV
}

/** Collaborators of the export step, taken from the AppContainer. */
class ExportDeps(container: AppContainer) {
    val repository: SongRepository = container.repository
    val tracks: TrackRepository = container.trackRepository
    val loader: MixLoader = container.newMixLoader()
    val player: SongPlayer = container.newSongPlayer()
    val sampleRate: Int = container.engine.sampleRate
    val exportDir = File(container.appContext.cacheDir, EXPORT_DIR)
    val resolver: ContentResolver = container.appContext.contentResolver

    private companion object {
        /** Matches the FileProvider cache-path in res/xml/file_paths.xml. */
        const val EXPORT_DIR = "exports"
    }
}

/**
 * Coach step 8 (spec §1 Export, §2.9): renders the song to WAV and M4A in the cache with
 * progress and cancel, then plays it, saves through the Storage Access Framework or shares
 * through the FileProvider. Every finished save/share is recorded for the Home card.
 */
class ExportViewModel(private val songId: Long, private val deps: ExportDeps) : ViewModel() {
    private val _state = MutableStateFlow(ExportState())
    val state: StateFlow<ExportState> = _state.asStateFlow()
    private val exportedEvents = Channel<Unit>(Channel.CONFLATED)

    /** Fires after each successful save or share (the shell may show an interstitial). */
    val exported = exportedEvents.receiveAsFlow()
    private var renderJob: Job? = null
    private var playJob: Job? = null
    private var playerLoaded = false
    private var pendingShare: Pair<String, ExportFormat>? = null

    init {
        viewModelScope.launch { deps.repository.reachStep(songId, CoachStep.EXPORT) }
        start()
    }

    fun start() {
        renderJob?.cancel()
        _state.update { it.copy(stage = ExportStage.Rendering(0f), notice = null) }
        renderJob = viewModelScope.launch {
            // Render first: update {} may run its block again, which must never re-render.
            val stage = render()
            _state.update { it.copy(stage = stage) }
        }
    }

    private suspend fun render(): ExportStage {
        val song = deps.repository.getSong(songId) ?: return ExportStage.Failed
        val tracks = deps.tracks.getAll(songId)
        val mixer = SongMixer(deps.sampleRate)
        val audible = withContext(Dispatchers.Default) { deps.loader.load(mixer, song, tracks) }
        if (!audible) return ExportStage.Empty
        val base = exportBaseName(song.title)
        var shown = -1
        return try {
            val result = withContext(Dispatchers.IO) {
                deps.exportDir.listFiles()?.forEach { it.delete() }
                ExportRenderer(deps.sampleRate).render(
                    mixer,
                    File(deps.exportDir, "$base.${ExportFormat.WAV.extension}"),
                    File(deps.exportDir, "$base.${ExportFormat.M4A.extension}"),
                ) { progress ->
                    val percent = (progress * PERCENT).toInt()
                    if (percent != shown) {
                        shown = percent
                        _state.update { it.copy(stage = ExportStage.Rendering(progress)) }
                    }
                }
            }
            ExportStage.Done(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            AudioLog.w("export failed", e)
            ExportStage.Failed
        } catch (e: IllegalStateException) {
            AudioLog.w("export codec failed", e)
            ExportStage.Failed
        }
    }

    fun cancel() {
        renderJob?.cancel()
    }

    fun setFormat(m4a: Boolean) = _state.update { it.copy(m4a = m4a, notice = null) }

    /** The file for the selected format, once rendered. */
    fun selectedFile(): File? {
        val done = _state.value.stage as? ExportStage.Done ?: return null
        return if (_state.value.m4a) done.result.m4a else done.result.wav
    }

    /** Suggested name for the save dialog. */
    fun suggestedName(): String = selectedFile()?.name.orEmpty()

    /** Copies the selected file to a document the user picked in the system save dialog. */
    fun saveTo(uri: Uri) {
        val file = selectedFile() ?: return
        val format = _state.value.format
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { copy(file, uri) }
            if (saved) record(uri.toString(), format)
            _state.update { it.copy(notice = if (saved) ExportNotice.SAVED else ExportNotice.SAVE_FAILED) }
        }
    }

    /** The share sheet opened for [uri] (the FileProvider link of the selected format). */
    fun shareStarted(uri: String) {
        pendingShare = uri to _state.value.format
    }

    /** The user picked an app in the share sheet. */
    fun shareChosen() {
        val (uri, format) = pendingShare ?: return
        pendingShare = null
        viewModelScope.launch { record(uri, format) }
    }

    private suspend fun record(uri: String, format: ExportFormat) {
        val done = _state.value.stage as? ExportStage.Done ?: return
        deps.repository.addExport(songId, uri, format, done.result.durationMs)
        exportedEvents.send(Unit)
    }

    private fun copy(file: File, uri: Uri): Boolean = try {
        val out = deps.resolver.openOutputStream(uri, "wt")
        out?.use { stream -> file.inputStream().use { it.copyTo(stream) } }
        out != null
    } catch (e: IOException) {
        AudioLog.w("save failed", e)
        false
    } catch (e: SecurityException) {
        AudioLog.w("save not allowed", e)
        false
    }

    fun togglePlay() {
        if (_state.value.playing) stopPlayback() else play()
    }

    private fun play() {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            if (!playerLoaded) {
                val song = deps.repository.getSong(songId) ?: return@launch
                playerLoaded = deps.player.load(song, deps.tracks.getAll(songId))
            }
            if (!playerLoaded || !deps.player.start(onInterrupt = ::onInterrupted)) {
                _state.update { it.copy(notice = ExportNotice.PLAYBACK_FAILED) }
                return@launch
            }
            _state.update { it.copy(playing = true, notice = null) }
            while (isActive && !deps.player.finished()) delay(TICK_MS)
            stopPlayback()
        }
    }

    fun stopPlayback() {
        playJob?.cancel()
        deps.player.stop()
        _state.update { it.copy(playing = false) }
    }

    private fun onInterrupted() {
        playJob?.cancel()
        _state.update { it.copy(playing = false) }
    }

    override fun onCleared() {
        renderJob?.cancel()
        stopPlayback()
    }

    private companion object {
        const val PERCENT = 100
        const val TICK_MS = 200L
    }
}

/** File-system-safe base name from the song title. */
internal fun exportBaseName(title: String): String {
    val cleaned = title.map { c -> if (c.isLetterOrDigit() || c in SAFE_PUNCTUATION) c else '_' }
        .joinToString("")
        .trim()
        .take(MAX_NAME_CHARS)
    return cleaned.ifEmpty { FALLBACK_NAME }
}

private const val SAFE_PUNCTUATION = " -_"
private const val MAX_NAME_CHARS = 60
private const val FALLBACK_NAME = "MouthBand"
