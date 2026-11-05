package com.adnan.memora.ui.screens

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.adnan.memora.telegram.TelegramClient
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.adnan.memora.R
import com.adnan.memora.cloud.CloudUri
import com.adnan.memora.gallery.LocalMedia

/** Isolated bin viewer. It does not change the main viewer list or restore files just to preview them. */
@Composable
fun BinPreview(media: LocalMedia, onDismiss: () -> Unit, onRestore: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(media.key) { onDispose { if (CloudUri.isCloud(media.uri)) TelegramClient.get(context).releaseFullFiles() } }
    var playing by remember(media.key) { mutableStateOf(false) }
    var failed by remember(media.key) { mutableStateOf(false) }
    var loading by remember(media.key) { mutableStateOf(true) }
    var retry by remember(media.key) { mutableIntStateOf(0) }
    var scale by remember(media.key) { mutableFloatStateOf(1f) }
    var offset by remember(media.key) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale <= 1f) Offset.Zero else offset + pan
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF030B14), contentColor = Color.White) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.back)) }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ui_bin_preview), style = MaterialTheme.typography.labelMedium, color = Color(0xFF39E6BD))
                        Text(media.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    if (playing && media.isVideo) {
                        if (CloudUri.isCloud(media.uri)) {
                            CloudVideoPlayer(media) { playing = false }
                        } else {
                            AndroidView(
                                factory = { context ->
                                    VideoView(context).apply {
                                        val controller = MediaController(context)
                                        controller.setAnchorView(this)
                                        setMediaController(controller)
                                        setVideoURI(media.uri)
                                        setOnPreparedListener { start() }
                                        setOnErrorListener { _, _, _ -> failed = true; playing = false; true }
                                    }
                                },
                                onRelease = { it.stopPlayback() },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        key(media.key, retry) {
                            AsyncImage(
                                model = if (CloudUri.isCloud(media.uri) && !media.isVideo) CloudUri.full(media.uri) else media.uri,
                                contentDescription = media.name, contentScale = ContentScale.Fit,
                                onSuccess = { loading = false; failed = false },
                                onError = { loading = false; failed = true },
                                modifier = Modifier.fillMaxSize().transformable(transform).graphicsLayer {
                                    scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                                },
                            )
                        }
                        if (loading) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF39E6BD))
                            Text(stringResource(R.string.ui_preview_loading), Modifier.padding(top = 12.dp))
                        }
                        if (failed) Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.ImageNotSupported, null, Modifier.size(44.dp), tint = Color(0xFF39E6BD))
                            Text(stringResource(R.string.ui_preview_unavailable), Modifier.padding(vertical = 14.dp))
                            TextButton(onClick = { failed = false; loading = true; retry++ }) { Text(stringResource(R.string.ui_retry_preview)) }
                        }
                        if (media.isVideo && !playing) FilledTonalButton(onClick = { failed = false; loading = false; playing = true }) {
                            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ui_play))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().background(Color(0xFF102337)).padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(formatSize(media.size), style = MaterialTheme.typography.labelLarge)
                        Text(media.mime, style = MaterialTheme.typography.bodySmall, color = Color(0xFFA8BDCE), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(onClick = onRestore) { Icon(Icons.Filled.RestoreFromTrash, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.restore)) }
                }
            }
        }
    }
}
