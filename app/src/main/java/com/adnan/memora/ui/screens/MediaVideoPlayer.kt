package com.adnan.memora.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.adnan.memora.R
import com.adnan.memora.cloud.CloudUri
import com.adnan.memora.gallery.LocalMedia
import com.adnan.memora.telegram.TelegramClient
import java.io.File
import kotlinx.coroutines.delay

/** Device codecs, no transcoding. Cloud videos are downloaded to temporary private storage first. */
@Composable
internal fun MediaVideoPlayer(media: LocalMedia, onClose: () -> Unit) {
    val context = LocalContext.current
    val cloud = CloudUri.isCloud(media.uri)
    var attempt by remember(media.key) { mutableIntStateOf(0) }
    var source by remember(media.key) { mutableStateOf<Uri?>(if (cloud) null else media.uri) }
    var downloadProgress by remember(media.key) { mutableFloatStateOf(0f) }
    var failed by remember(media.key) { mutableStateOf(false) }
    var position by rememberSaveable(media.key) { mutableLongStateOf(0L) }
    BackHandler { onClose() }
    LaunchedEffect(media.key, attempt) {
        failed = false
        if (cloud) {
            val ids = CloudUri.ids(media.uri)
            val path = ids?.let { TelegramClient.get(context).cloudFullPath(it.first, it.second) { p -> downloadProgress = p } }
            source = path?.let { Uri.fromFile(File(it)) }
            if (source == null) failed = true
        }
    }
    // The viewer already owns the temporary-file cleanup; do not delete a file while its player is alive.
    val player = remember(media.key) { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { failed = true }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release(); if (cloud) TelegramClient.get(context).releaseFullFiles() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }
    LaunchedEffect(source, attempt) {
        source?.let { player.setMediaItem(MediaItem.fromUri(it)); player.seekTo(position); player.prepare(); player.playWhenReady = true }
    }
    LaunchedEffect(player) { while (true) { position = player.currentPosition.coerceAtLeast(0); delay(500) } }
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
            if (source != null && !failed) AndroidView(
                factory = { c -> PlayerView(c).apply { this.player = player; useController = true; keepScreenOn = true } },
                update = { it.player = player }, modifier = Modifier.fillMaxSize(),
            )
            else Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (failed) {
                    Text(stringResource(R.string.mx_video_error), color = Color.White)
                    TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.mx_retry), color = Color.White) }
                } else {
                    CircularProgressIndicator(progress = { downloadProgress }, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.cl_preparing_video, (downloadProgress * 100).toInt()), color = Color.White)
                }
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).background(Color(0xFF151B24)), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.back), tint = Color.White) }
            Text(media.name, color = Color.White, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}
