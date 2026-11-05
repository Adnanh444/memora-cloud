package com.adnan.memora.ui.screens

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.core.view.WindowCompat

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
    DisposableEffect(ctx) {
        val window=(ctx as? Activity)?.window
        val controller=window?.let { WindowCompat.getInsetsController(it,it.decorView) }
        val oldStatus=controller?.isAppearanceLightStatusBars
        val oldNavigation=controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars=false;controller?.isAppearanceLightNavigationBars=false
        onDispose {
            oldStatus?.let {controller?.isAppearanceLightStatusBars=it}
            oldNavigation?.let {controller?.isAppearanceLightNavigationBars=it}
        }
    }
    val pager = rememberPagerState(initialPage = vm.viewerStart.coerceIn(0, items.lastIndex)) { items.size }
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var playingVideo by remember { mutableStateOf<LocalMedia?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { TelegramClient.get(ctx).releaseFullFiles() } }
    var heightPx by remember { mutableFloatStateOf(0f) }
    val deleteAction = rememberDeleteAction(onDone = { vm.refresh() })
    val favoriteAction = rememberPhoneFavoriteAction { vm.refresh() }
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
                            onClick = { playingVideo = m },
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

        // One narrow bottom toolbar; no controls obscure the top of the photo.
        AnimatedVisibility(
            visible = chrome && !detailsOpen && playingVideo == null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { it },
            exit = fadeOut(tween(150)),
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp)).background(Color(0xE61A222D)),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                    IconButton(onClick = {
                        if (isCloud) CloudDownloader.start(ctx, listOf(current)) else shareMedia(ctx, listOf(current))
                    }, modifier = Modifier.weight(1f)) {
                        Icon(if (isCloud) Icons.Filled.Download else Icons.Filled.Share,
                            stringResource(if (isCloud) R.string.cl_download else R.string.share))
                    }
                    IconButton(onClick = { detailsOpen = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Info, stringResource(R.string.details))
                    }
                    if (!isCloud) IconButton(onClick = { favoriteAction(listOf(current), !current.isFavorite) },
                        enabled = android.os.Build.VERSION.SDK_INT >= 30, modifier = Modifier.weight(1f)) {
                        Icon(if (current.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            stringResource(if (current.isFavorite) R.string.og_unfavorite else R.string.og_favorite))
                    }
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = Color(0xFFFFB5BE))
                    }
                    OrgMenu(vm, tint = Color.White, pickItems = { listOf(current) }, modifier = Modifier.weight(1f))
                }
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

        playingVideo?.let { video -> MediaVideoPlayer(video) { playingVideo = null } }
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
    var imageFailed by remember(model) { mutableStateOf(false) }
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
            modifier = Modifier.fillMaxSize(), onSuccess = { loaded = true; imageFailed = false }, onError = { imageFailed = true })
        if (showSpinner && !loaded && !imageFailed) CircularProgressIndicator(color = Color.White)
        if (imageFailed) Text(stringResource(R.string.mx_image_error), color = Color.White, modifier = Modifier.padding(24.dp))
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

/** Shared in-app player is also used by cloud-bin previews. */
@Composable
internal fun CloudVideoPlayer(m: LocalMedia, onClose: () -> Unit) = MediaVideoPlayer(m, onClose)
