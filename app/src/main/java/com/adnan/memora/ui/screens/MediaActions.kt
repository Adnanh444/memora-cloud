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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.adnan.memora.R
import com.adnan.memora.backup.BackupEngine
import com.adnan.memora.cloud.CloudUri
import com.adnan.memora.data.MediaBin
import com.adnan.memora.gallery.LocalMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/**
 * "Delete from device" — no Android Allow/Deny sheet, ever.
 *
 * The file is moved into Memora's own bin (restorable for 30 days) using plain file access.
 * That only works with "All files access"; without it Android would force its own
 * confirmation dialog, so instead the app explains the setting once and deletes nothing.
 */
@Composable
fun rememberDeleteAction(onDone: () -> Unit): (List<LocalMedia>) -> Unit {
    val ctx = LocalContext.current
    val onDoneL by rememberUpdatedState(onDone)
    val scope = rememberCoroutineScope()
    var askPermission by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    if (askPermission) {
        AlertDialog(
            onDismissRequest = { askPermission = false },
            title = { Text(stringResource(R.string.del_perm_title)) },
            text = { Text(stringResource(R.string.del_perm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    askPermission = false
                    runCatching { ctx.startActivity(MediaBin.settingsIntent(ctx)) }
                }) { Text(stringResource(R.string.del_perm_open)) }
            },
            dismissButton = {
                TextButton(onClick = { askPermission = false }) { Text(stringResource(R.string.del_perm_later)) }
            },
        )
    }

    if (working) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }

    return { items ->
        if (items.isNotEmpty()) {
            // Cloud items belong to the cloud bin (Albums → Cloud bin), never to the phone bin.
            val cloudKeys = items.filter { CloudUri.isCloud(it.uri) }.map { it.key }
            val localItems = items.filterNot { CloudUri.isCloud(it.uri) }

            if (cloudKeys.isNotEmpty()) {
                scope.launch {
                    BackupEngine.trashCloudForLocal(ctx, cloudKeys)
                    if (localItems.isEmpty()) onDoneL()
                }
            }

            if (localItems.isNotEmpty()) {
                if (MediaBin.enabled(ctx)) {
                    working = true
                    scope.launch {
                        val moved = withContext(Dispatchers.IO) { MediaBin.moveToBin(ctx, localItems) }
                        working = false
                        onDoneL()
                        Toast.makeText(ctx, ctx.getString(R.string.mb_moved, moved), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    askPermission = true
                }
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
