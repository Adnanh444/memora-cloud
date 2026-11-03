package com.adnan.memora.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.ui.viewinterop.AndroidView
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import com.adnan.memora.R
import com.adnan.memora.backup.BackupEngine
import com.adnan.memora.cloud.CloudDownloader
import com.adnan.memora.cloud.CloudUri
import com.adnan.memora.telegram.TelegramClient
import com.adnan.memora.data.BackupState
import com.adnan.memora.gallery.BackupPhase
import com.adnan.memora.gallery.GalleryViewModel
import com.adnan.memora.gallery.ItemBackup
import com.adnan.memora.gallery.LocalMedia
import com.adnan.memora.ui.theme.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun ViewerScreen(vm: GalleryViewModel, onBack: () -> Unit) {
    val items by vm.viewerItems.collectAsState()
    val backup by vm.itemBackup.collectAsState()
    val summary by vm.backupSummary.collectAsState()
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val ctx = LocalContext.current
    val pager = rememberPagerState(initialPage = vm.viewerStart.coerceIn(0, items.lastIndex)) { items.size }
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var playingCloud by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { TelegramClient.get(ctx).releaseFullFiles() } }
    var heightPx by remember { mutableFloatStateOf(0f) }
    val deleteAction = rememberDeleteAction(onDone = { vm.refresh() })
    val shift by animateFloatAsState(if (detailsOpen) 1f else 0f, tween(380, easing = FastOutSlowInEasing), label = "shift")
    BackHandler(detailsOpen) { detailsOpen = false }
    LaunchedEffect(pager.currentPage) { zoomed = false }

    val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]
    val isCloud = CloudUri.isCloud(current.uri)
    val onTap: () -> Unit = { if (detailsOpen) detailsOpen = false else chrome = !chrome }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier.fillMaxSize()
                .onSizeChanged { heightPx = it.height.toFloat() }
                .pointerInput(zoomed, detailsOpen) {
                    var acc = 0f
                    detectVerticalDragGestures(
                        onDragStart = { acc = 0f },
                        onDragEnd = {
                            if (!zoomed) {
                                if (detailsOpen) { if (acc > 100f) detailsOpen = false }
                                else if (acc < -100f) detailsOpen = true
                                else if (acc > 220f) onBack()
                            }
                        },
                        onVerticalDrag = { _, dy -> acc += dy },
                    )
                }
        ) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !zoomed && !detailsOpen,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    // when details open: photo shrinks and slides up, then returns when closed
                    translationY = -shift * heightPx * 0.24f
                    val s = 1f - 0.40f * shift
                    scaleX = s; scaleY = s
                },
            ) { page ->
                val m = items[page.coerceIn(0, items.lastIndex)]
                if (m.isVideo) {
                    Box(
                        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
                        Alignment.Center,
                    ) {
                        AsyncImage(model = m.uri, contentDescription = m.name, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize())
                        FilledIconButton(
                            onClick = { if (CloudUri.isCloud(m.uri)) playingCloud = true else playVideo(ctx, m.uri, m.mime) },
                            modifier = Modifier.size(72.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, stringResource(R.string.play_video), Modifier.size(40.dp))
                        }
                    }
                } else {
                    ZoomableImage(
                        model = if (CloudUri.isCloud(m.uri)) CloudUri.full(m.uri) else m.uri,
                        showSpinner = CloudUri.isCloud(m.uri), contentDescription = m.name,
                        onZoomChanged = { zoomed = it }, onTap = onTap,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // top bar
        AnimatedVisibility(
            visible = chrome && !detailsOpen,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { -it },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it },
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
                    .statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
                Spacer(Modifier.weight(1f))
                if (isCloud || summary.phase != BackupPhase.NOT_SET_UP) {
                    val ok = isCloud || backup[current.key]?.state == BackupState.VERIFIED
                    Icon(if (ok) Icons.Filled.CloudDone else Icons.Filled.CloudOff, null, tint = Color.White,
                        modifier = Modifier.padding(end = 16.dp))
                }
            }
        }

        // bottom action bar
        AnimatedVisibility(
            visible = chrome && !detailsOpen,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it },
        ) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 16.dp)
                    .glass(RoundedCornerShape(32.dp)).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (isCloud) BarAction(Icons.Filled.Download, R.string.cl_download) {
                    CloudDownloader.start(ctx, listOf(current))
                    android.widget.Toast.makeText(ctx, R.string.cl_downloading, android.widget.Toast.LENGTH_SHORT).show()
                }
                else BarAction(Icons.Filled.Share, R.string.share) { shareMedia(ctx, listOf(current)) }
                BarAction(Icons.Filled.Info, R.string.details) { detailsOpen = true }
                BarAction(Icons.Filled.Delete, R.string.delete) { confirmDelete = true }
            }
        }

        // details panel (swipe up / Details button)
        AnimatedVisibility(
            visible = detailsOpen,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(200)),
        ) {
            DetailsPanel(
                m = current, backup = backup[current.key], backupOn = summary.phase != BackupPhase.NOT_SET_UP, isCloud = isCloud,
                onClose = { detailsOpen = false },
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.46f),
            )
        }

        if (confirmDelete) {
            if (isCloud) {
                val ids = CloudUri.ids(current.uri)
                CloudDeleteSheet(
                    count = 1,
                    onConfirm = { if (ids != null) scope.launch { BackupEngine.trashCloudMessages(listOf(ids)) } },
                    onDismiss = { confirmDelete = false },
                )
            } else {
                DeleteSheet(
                    count = 1,
                    hasCloud = backup[current.key]?.state == BackupState.VERIFIED,
                    onDeletePhone = { deleteAction(listOf(current)) },
                    onDeleteCloud = { scope.launch { BackupEngine.trashCloudForLocal(ctx, listOf(current.key)) } },
                    onDeleteBoth = {
                        scope.launch {
                            BackupEngine.trashCloudForLocal(ctx, listOf(current.key))
                            deleteAction(listOf(current))
                        }
                    },
                    onDismiss = { confirmDelete = false },
                )
            }
        }

        if (playingCloud && isCloud) CloudVideoPlayer(current) { playingCloud = false }
    }
}

private fun playVideo(ctx: Context, uri: Uri, mime: String) {
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try { ctx.startActivity(i) } catch (_: ActivityNotFoundException) {}
}

@Composable
private fun BarAction(icon: ImageVector, label: Int, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, stringResource(label))
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
    }
}

/** Pinch to zoom, drag to pan when zoomed, double-tap to zoom in/out. Does not steal swipes when not zoomed. */
@Composable
private fun ZoomableImage(
    model: Any?, contentDescription: String?,
    onZoomChanged: (Boolean) -> Unit, onTap: () -> Unit, modifier: Modifier = Modifier,
    showSpinner: Boolean = false,
) {
    var loaded by remember(model) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val onZ by rememberUpdatedState(onZoomChanged)
    val onT by rememberUpdatedState(onTap)

    fun clamp(o: Offset, s: Float): Offset {
        val mx = box.width * (s - 1f) / 2f
        val my = box.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    Box(
        modifier
            .onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onT() },
                    onDoubleTap = { tap ->
                        scope.launch {
                            val from = scale
                            val fromO = offset
                            val to = if (from > 1.05f) 1f else 2.5f
                            val c = Offset(box.width / 2f, box.height / 2f)
                            val toO = if (to == 1f) Offset.Zero else clamp((tap - c) * (1f - to), to)
                            animate(0f, 1f, animationSpec = tween(280)) { t, _ ->
                                scale = from + (to - from) * t
                                offset = fromO + (toO - fromO) * t
                            }
                            onZ(to > 1f)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        val zoom = ev.calculateZoom()
                        val pan = ev.calculatePan()
                        if (ev.changes.size > 1 || scale > 1.01f) {
                            val ns = (scale * zoom).coerceIn(1f, 6f)
                            scale = ns
                            offset = if (ns <= 1.01f) Offset.Zero else clamp(offset + pan, ns)
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                            onZ(ns > 1.01f)
                        }
                    } while (ev.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(model = model, contentDescription = contentDescription, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(), onSuccess = { loaded = true })
        if (showSpinner && !loaded) CircularProgressIndicator(color = Color.White)
    }
}

private class ExifInfo(val camera: String?, val settings: String?)

private fun readExif(ctx: Context, uri: Uri): ExifInfo? = try {
    ctx.contentResolver.openInputStream(uri)?.use { s ->
        val e = ExifInterface(s)
        val make = e.getAttribute(ExifInterface.TAG_MAKE)?.trim()
        val model = e.getAttribute(ExifInterface.TAG_MODEL)?.trim()
        val camera = when {
            model.isNullOrBlank() -> make
            make.isNullOrBlank() || model.startsWith(make, ignoreCase = true) -> model
            else -> "$make $model"
        }
        val f = e.getAttribute(ExifInterface.TAG_F_NUMBER)?.toDoubleOrNull()
        val exp = e.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull()
        val iso = e.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
        val parts = listOfNotNull(
            f?.let { "f/" + (if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()) },
            exp?.let { if (it in 0.0..0.999 && it > 0) "1/${(1.0 / it).roundToInt()}s" else "${it}s" },
            iso?.let { "ISO $it" },
        )
        if (camera.isNullOrBlank() && parts.isEmpty()) null
        else ExifInfo(camera, parts.joinToString(" · ").ifBlank { null })
    }
} catch (_: Exception) { null }

@Composable
private fun DetailsPanel(
    m: LocalMedia, backup: ItemBackup?, backupOn: Boolean, isCloud: Boolean, onClose: () -> Unit, modifier: Modifier,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val onCloseL by rememberUpdatedState(onClose)
    val exif by produceState<ExifInfo?>(null, m.uri) {
        value = if (m.isVideo) null else withContext(Dispatchers.IO) { readExif(ctx, m.uri) }
    }
    Column(
        modifier.clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(cs.surfaceContainer)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Box(
            Modifier.fillMaxWidth()
                .pointerInput(Unit) {
                    var acc = 0f
                    detectVerticalDragGestures(
                        onDragStart = { acc = 0f },
                        onDragEnd = { if (acc > 40f) onCloseL() },
                        onVerticalDrag = { _, dy -> acc += dy },
                    )
                }
                .padding(vertical = 14.dp),
            Alignment.Center,
        ) {
            Box(Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape).background(cs.outlineVariant))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            DetailRow(Icons.Filled.CalendarToday, formatDateLong(m.dateTaken), formatTime(m.dateTaken))
            val res = if (m.width > 0 && m.height > 0) "${m.width} × ${m.height}" else null
            val mp = if (!m.isVideo && m.width > 0 && m.height > 0)
                String.format(java.util.Locale.US, "%.1f MP", m.width.toLong() * m.height / 1_000_000.0) else null
            val fileSub = listOfNotNull(
                mp, res, if (m.isVideo) formatDuration(m.durationMs) else null, formatSize(m.size),
            ).joinToString(" · ")
            DetailRow(if (m.isVideo) Icons.Filled.Videocam else Icons.Filled.Image, m.name, fileSub)
            exif?.let { x ->
                DetailRow(Icons.Filled.CameraAlt, x.camera ?: stringResource(R.string.d_camera), x.settings)
            }
            DetailRow(Icons.Filled.Folder, m.bucket, stringResource(R.string.d_folder))
            val backupTitle = when {
                isCloud -> stringResource(R.string.backup_ok)
                !backupOn -> stringResource(R.string.backup_none)
                backup?.state == BackupState.VERIFIED -> stringResource(if (backup.duplicate) R.string.backup_duplicate else R.string.backup_ok)
                backup?.state == BackupState.UPLOADING -> stringResource(R.string.backup_uploading, ((backup.progress) * 100).toInt())
                else -> stringResource(R.string.backup_none)
            }
            DetailRow(
                if (isCloud || backup?.state == BackupState.VERIFIED) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                backupTitle, stringResource(R.string.d_backup),
            )
            DetailRow(Icons.Filled.Update, formatDateTime(m.dateModified), stringResource(R.string.d_modified))
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, title: String, subtitle: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(20.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Plays a cloud video inside the app. The video is fetched into a TEMPORARY app-private cache,
 * played, and removed again when you leave. Nothing is saved to the gallery unless you tap Download.
 */
@Composable
private fun CloudVideoPlayer(m: LocalMedia, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var path by remember(m.key) { mutableStateOf<String?>(null) }
    var progress by remember(m.key) { mutableFloatStateOf(0f) }
    var failed by remember(m.key) { mutableStateOf(false) }
    BackHandler { onClose() }
    LaunchedEffect(m.key) {
        val ids = CloudUri.ids(m.uri)
        path = if (ids == null) null else TelegramClient.get(ctx).cloudFullPath(ids.first, ids.second) { progress = it }
        if (path == null) failed = true
    }
    DisposableEffect(m.key) { onDispose { TelegramClient.get(ctx).releaseFullFiles() } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val p = path
        if (p != null) {
            AndroidView(
                factory = { c ->
                    VideoView(c).apply {
                        val mc = MediaController(c)
                        mc.setAnchorView(this)
                        setMediaController(mc)
                        setVideoPath(p)
                        setOnPreparedListener { start() }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                if (failed) {
                    Text(stringResource(R.string.cl_video_failed), color = Color.White)
                } else {
                    CircularProgressIndicator(progress = { progress }, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.cl_preparing_video, (progress * 100).toInt()), color = Color.White)
                }
            }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.statusBarsPadding().padding(4.dp).align(Alignment.TopStart),
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
    }
}
