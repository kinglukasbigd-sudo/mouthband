package com.mouthband.ui.common

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mouthband.ui.coach.MicAccess

/** Microphone permission state plus the action that asks for it (spec §6: asked at first need). */
class MicPermissionState(
    val access: MicAccess,
    val request: () -> Unit,
    val openSettings: () -> Unit,
)

fun Context.hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@Composable
fun rememberMicPermission(onResult: (Boolean) -> Unit = {}): MicPermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasMicPermission()) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        askedOnce = true
        val activity = context.findActivity()
        permanentlyDenied = !ok && activity != null &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        onResult(ok)
    }
    // Re-check when returning from system settings.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = context.hasMicPermission()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val access = when {
        granted -> MicAccess.GRANTED
        askedOnce && permanentlyDenied -> MicAccess.DENIED
        else -> MicAccess.NEEDS_REQUEST
    }
    return MicPermissionState(
        access = access,
        request = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
        openSettings = {
            val uri = Uri.fromParts("package", context.packageName, null)
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        },
    )
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
