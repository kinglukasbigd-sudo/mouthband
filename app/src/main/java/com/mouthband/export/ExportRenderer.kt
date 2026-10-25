package com.mouthband.export

import com.mouthband.mix.SongMixer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/** Finished export files and the song length. */
data class ExportResult(val wav: File, val m4a: File, val durationMs: Long)

/**
 * Offline render of the whole arrangement (spec §2.9) through the very [SongMixer] class that
 * plays it back, plus 2 s of tail for the reverb, resampled to 44.1 kHz and written to WAV and
 * M4A in one pass. Cancelling the coroutine stops the render (partial files are deleted).
 */
class ExportRenderer(private val engineRate: Int) {
    suspend fun render(mixer: SongMixer, wav: File, m4a: File, onProgress: (Float) -> Unit): ExportResult {
        val layout = requireNotNull(mixer.layout) { "mixer has no layout" }
        val total = layout.totalFrames + TAIL_SECONDS * engineRate
        wav.parentFile?.mkdirs()
        var completed = false
        try {
            WavWriter(wav, EXPORT_RATE).use { wavWriter ->
                AacEncoder(m4a, EXPORT_RATE).use { aac ->
                    val resampler = StreamingResampler(engineRate, EXPORT_RATE) { frames, n ->
                        wavWriter.write(frames, n)
                        aac.write(frames, n)
                    }
                    renderInto(mixer, total, resampler, onProgress)
                    resampler.finish()
                    aac.finish()
                }
            }
            completed = true
        } finally {
            if (!completed) {
                wav.delete()
                m4a.delete()
            }
        }
        return ExportResult(wav, m4a, layout.totalFrames * MS_PER_SECOND / engineRate)
    }

    private suspend fun renderInto(
        mixer: SongMixer,
        total: Long,
        resampler: StreamingResampler,
        onProgress: (Float) -> Unit,
    ) {
        mixer.reset()
        val block = FloatArray(BLOCK * 2)
        var frame = 0L
        while (frame < total) {
            currentCoroutineContext().ensureActive()
            val n = minOf(BLOCK.toLong(), total - frame).toInt()
            block.fill(0f, 0, n * 2)
            mixer.render(block, n, frame)
            resampler.push(block, n)
            frame += n
            onProgress(frame.toFloat() / total)
        }
    }

    companion object {
        /** Spec §1 Export: 44.1 kHz stereo. */
        const val EXPORT_RATE = 44_100
        const val TAIL_SECONDS = 2L
        private const val BLOCK = 4096
        private const val MS_PER_SECOND = 1000L

        /** Frames of the rendered song at [rate], tail excluded (used by tests). */
        fun songFrames(mixer: SongMixer): Long = requireNotNull(mixer.layout).totalFrames
    }
}
