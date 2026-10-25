package com.mouthband.audio

import android.util.Log
import com.mouthband.BuildConfig

/** Logging for control threads only — never call from a render or record loop. */
object AudioLog {
    private const val TAG = "MouthBand"

    fun w(message: String, error: Throwable? = null) {
        Log.w(TAG, message, error)
    }

    /** One-off milestones that are useful in release logs too. */
    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}
