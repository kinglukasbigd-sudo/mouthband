package com.mouthband.ui.export

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.mouthband.R
import com.mouthband.audio.AudioLog

/**
 * The Android share sheet for an exported file (spec §1 Export: share via FileProvider).
 * [onTargetChosen] runs only when the user actually picks an app, reported by the chooser's
 * callback, not when the sheet is dismissed.
 */
@Composable
fun rememberShareSheet(onTargetChosen: () -> Unit): (Uri, String) -> Unit {
    val context = LocalContext.current
    val title = stringResource(R.string.export_share_title)
    val callback by rememberUpdatedState(onTargetChosen)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = callback()
        }
        val filter = IntentFilter(ACTION_TARGET_CHOSEN)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return remember(context, title) {
        { uri, mimeType -> open(context, title, uri, mimeType) }
    }
}

private fun open(context: Context, title: String, uri: Uri, mimeType: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chosen = PendingIntent.getBroadcast(
        context,
        0,
        Intent(ACTION_TARGET_CHOSEN).setPackage(context.packageName),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    try {
        context.startActivity(Intent.createChooser(send, title, chosen.intentSender))
    } catch (e: ActivityNotFoundException) {
        AudioLog.w("no share sheet", e)
    }
}

private const val ACTION_TARGET_CHOSEN = "com.mouthband.action.SHARE_TARGET_CHOSEN"
