package com.mouthband.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.content.ContextCompat

/**
 * Owns audio focus and the "becoming noisy" receiver for whoever is playing or recording
 * (spec §2.1 Audio focus). Losing focus, unplugging headphones or the app going to the
 * background calls the active owner's interrupt callback once, then releases everything.
 */
class AudioSession(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var onInterrupt: (() -> Unit)? = null
    private var receiverRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) interrupt()
        }
    }

    /** Requests focus for a new owner. Returns false when another app keeps focus. */
    @Synchronized
    fun begin(onInterrupt: () -> Unit): Boolean {
        endLocked(notify = true)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { change ->
                val lost = change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                if (lost) interrupt()
            }
            .build()
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        focusRequest = request
        this.onInterrupt = onInterrupt
        ContextCompat.registerReceiver(
            appContext,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
        return true
    }

    /** The owner finished normally. */
    @Synchronized
    fun end() {
        endLocked(notify = false)
    }

    /** Focus loss, noisy route or app stop: stop the owner. */
    fun interrupt() {
        val callback = synchronized(this) {
            val cb = onInterrupt
            endLocked(notify = false)
            cb
        }
        callback?.invoke()
    }

    private fun endLocked(notify: Boolean) {
        val previous = onInterrupt
        onInterrupt = null
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        if (receiverRegistered) {
            appContext.unregisterReceiver(noisyReceiver)
            receiverRegistered = false
        }
        if (notify) previous?.invoke()
    }
}
