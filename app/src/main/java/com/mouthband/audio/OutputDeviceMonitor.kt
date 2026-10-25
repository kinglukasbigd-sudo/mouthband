package com.mouthband.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where sound currently goes. */
data class OutputRoute(val bluetooth: Boolean = false, val wiredHeadphones: Boolean = false) {
    /** Calibration measures the phone speaker, so it needs neither. */
    val isSpeaker: Boolean get() = !bluetooth && !wiredHeadphones
}

/**
 * Watches output devices with [AudioDeviceCallback] (spec §2.1): Bluetooth triggers the delay
 * warning; wired headphones show the green chip on recording steps.
 */
class OutputDeviceMonitor(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _route = MutableStateFlow(OutputRoute())
    val route: StateFlow<OutputRoute> = _route.asStateFlow()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    init {
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    private fun refresh() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        _route.value = OutputRoute(
            bluetooth = devices.any { it.type in bluetoothTypes() },
            wiredHeadphones = devices.any { it.type in WIRED_TYPES },
        )
    }

    private fun bluetoothTypes(): Set<Int> = buildSet {
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(AudioDeviceInfo.TYPE_HEARING_AID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(AudioDeviceInfo.TYPE_BLE_BROADCAST)
    }

    private companion object {
        val WIRED_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
    }
}
