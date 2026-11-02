package com.adnan.memora.ui.screens

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.adnan.memora.R
import com.adnan.memora.gallery.LocalMedia

/** Opens the Android share sheet for one or more local photos/videos. */
fun shareMedia(ctx: Context, items: List<LocalMedia>) {
    if (items.isEmpty()) return
    val intent = if (items.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = items[0].mime
            putExtra(Intent.EXTRA_STREAM, items[0].uri)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = when {
                items.all { it.isVideo } -> "video/*"
                items.none { it.isVideo } -> "image/*"
                else -> "*/*"
            }
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(items.map { it.uri }))
        }
    }
    val clip = ClipData.newRawUri(null, items[0].uri)
    items.drop(1).forEach { clip.addItem(ClipData.Item(it.uri)) }
    intent.clipData = clip
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try { ctx.startActivity(Intent.createChooser(intent, null)) } catch (_: Exception) {}
}

private class DeleteState { var pending: List<Uri> = emptyList(); var run: (List<Uri>) -> Unit = {} }

/**
 * "Delete from device". Only ever touches the local file.
 * Android 11+: moves to the phone's trash through the system confirmation dialog (recoverable 30 days).
 * Android 8-10: deletes directly (asking Android for permission where required).
 */
@Composable
fun rememberDeleteAction(onDone: () -> Unit): (List<LocalMedia>) -> Unit {
    val ctx = LocalContext.current
    val onDoneL by rememberUpdatedState(onDone)
    val state = remember { DeleteState() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            if (Build.VERSION.SDK_INT < 30 && state.pending.isNotEmpty()) state.run(state.pending) else onDoneL()
        }
    }
    state.run = { uris ->
        var index = 0
        var finished = true
        for (u in uris) {
            try {
                ctx.contentResolver.delete(u, null, null)
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                    state.pending = uris.drop(index)
                    launcher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                    finished = false
                    break
                }
                Toast.makeText(ctx, R.string.delete_failed, Toast.LENGTH_SHORT).show()
            }
            index++
        }
        if (finished) { state.pending = emptyList(); onDoneL() }
    }
    return { items ->
        val uris = items.map { it.uri }
        if (uris.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= 30) {
                val pi = MediaStore.createTrashRequest(ctx.contentResolver, uris, true)
                launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } else {
                state.run(uris)
            }
        }
    }
}

class BinActions(val restore: (List<Uri>) -> Unit, val deleteForever: (List<Uri>) -> Unit)

/** Restore from / permanently delete from the phone's bin (Android 11+). Android shows its own confirmation. */
@Composable
fun rememberBinActions(onDone: () -> Unit): BinActions {
    val ctx = LocalContext.current
    val onDoneL by rememberUpdatedState(onDone)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) onDoneL()
    }
    return remember(launcher) {
        BinActions(
            restore = { uris ->
                if (Build.VERSION.SDK_INT >= 30 && uris.isNotEmpty()) {
                    val pi = MediaStore.createTrashRequest(ctx.contentResolver, uris, false)
                    launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                }
            },
            deleteForever = { uris ->
                if (Build.VERSION.SDK_INT >= 30 && uris.isNotEmpty()) {
                    val pi = MediaStore.createDeleteRequest(ctx.contentResolver, uris)
                    launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                }
            },
        )
    }
}
