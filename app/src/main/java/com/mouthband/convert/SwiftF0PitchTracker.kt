package com.mouthband.convert

import ai.onnxruntime.NodeInfo
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.res.AssetManager
import com.mouthband.BuildConfig
import com.mouthband.audio.AudioLog
import java.io.Closeable
import java.io.IOException
import java.nio.FloatBuffer
import kotlin.math.abs

/**
 * SwiftF0 (MIT) via ONNX Runtime — the primary pitch tracker (spec §2.5). Input and output
 * names and shapes are read from the shipped model at load time (and logged once in debug), so
 * the wrapper adapts: the audio input is the rank-2 float tensor, optional scalar inputs receive
 * fmin/fmax, outputs are matched by name ("pitch", "confidence") with positional fallback.
 */
class SwiftF0PitchTracker private constructor(
    private val environment: OrtEnvironment,
    private val session: OrtSession,
) : PitchTracker, Closeable {
    override val name: String = "SwiftF0"
    override val voicedThreshold: Float = CONFIDENCE_THRESHOLD

    private val audioInput: String
    private val scalarInputs: List<String>
    private val pitchOutput: String
    private val confidenceOutput: String

    init {
        val inputs = session.inputInfo
        audioInput = inputs.entries.firstOrNull { (_, node) -> (node.info as? TensorInfo)?.shape?.size == 2 }?.key
            ?: inputs.keys.first()
        scalarInputs = inputs.keys.filter { it != audioInput }
        val outputs = session.outputNames.toList()
        pitchOutput = outputs.firstOrNull { it.contains("pitch", ignoreCase = true) } ?: outputs[0]
        confidenceOutput = outputs.firstOrNull { it.contains("conf", ignoreCase = true) }
            ?: outputs.getOrElse(1) { outputs[0] }
        if (BuildConfig.DEBUG) {
            AudioLog.d("SwiftF0 inputs: ${describe(inputs)}; outputs: ${describe(session.outputInfo)}")
        }
    }

    override fun track(audio16k: FloatArray): PitchTrack {
        val frames = maxOf(1, audio16k.size / PitchTracker.HOP)
        val tensors = ArrayList<OnnxTensor>()
        try {
            val audio = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(audio16k),
                longArrayOf(1, audio16k.size.toLong()),
            )
            tensors += audio
            val feeds = HashMap<String, OnnxTensor>()
            feeds[audioInput] = audio
            scalarInputs.forEachIndexed { i, name ->
                val isMax = name.contains("max", ignoreCase = true) || i == 1
                val value = if (isMax) PitchTracker.MAX_HZ else PitchTracker.MIN_HZ
                val tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(floatArrayOf(value)), longArrayOf())
                tensors += tensor
                feeds[name] = tensor
            }
            session.run(feeds).use { result ->
                val pitch = flatten(result.get(pitchOutput).orElse(null)?.value)
                val confidence = flatten(result.get(confidenceOutput).orElse(null)?.value)
                return align(audio16k, pitch, confidence, frames)
            }
        } finally {
            tensors.forEach { it.close() }
        }
    }

    /** Copies the model's frames, zeroing silent hops as SwiftF0 itself does. */
    private fun align(audio: FloatArray, pitch: FloatArray, confidence: FloatArray, frames: Int): PitchTrack {
        val hz = FloatArray(frames)
        val conf = FloatArray(frames)
        for (t in 0 until minOf(frames, pitch.size, confidence.size)) {
            var peak = 0f
            val start = t * PitchTracker.HOP
            for (i in start until minOf(start + PitchTracker.HOP, audio.size)) peak = maxOf(peak, abs(audio[i]))
            hz[t] = pitch[t]
            conf[t] = if (peak < PitchTracker.SILENCE_PEAK) 0f else confidence[t]
        }
        return PitchTrack(hz, conf)
    }

    private fun describe(nodes: Map<String, NodeInfo>): String = nodes.entries.joinToString { (name, node) ->
        val info = node.info as? TensorInfo
        "$name${info?.shape?.contentToString()}:${info?.type}"
    }

    /** The model's outputs as float frames, whatever their element type (pitch ships as DOUBLE). */
    private fun flatten(value: Any?): FloatArray = when (value) {
        is FloatArray -> value
        is DoubleArray -> FloatArray(value.size) { value[it].toFloat() }
        is Array<*> -> value.flatMap { flatten(it).asList() }.toFloatArray()
        else -> FloatArray(0)
    }

    override fun close() {
        session.close()
    }

    companion object {
        const val ASSET = "swiftf0.onnx"

        /** Spec §2.5: confidence threshold 0.9. */
        const val CONFIDENCE_THRESHOLD = 0.9f
        private const val INTRA_OP_THREADS = 2

        /** Loads the bundled model, or returns null so the caller falls back to YIN. */
        fun load(assets: AssetManager): SwiftF0PitchTracker? = try {
            val bytes = assets.open(ASSET).use { it.readBytes() }
            val environment = OrtEnvironment.getEnvironment()
            // No usage events to Microsoft: the app's only network use is ads and consent.
            environment.setTelemetry(false)
            val session = OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(INTRA_OP_THREADS)
                environment.createSession(bytes, options)
            }
            SwiftF0PitchTracker(environment, session)
        } catch (e: IOException) {
            AudioLog.w("SwiftF0 model missing, using YIN", e)
            null
        } catch (e: OrtException) {
            AudioLog.w("SwiftF0 failed to load, using YIN", e)
            null
        } catch (e: UnsatisfiedLinkError) {
            AudioLog.w("ONNX Runtime unavailable, using YIN", e)
            null
        }
    }
}
