package com.adnan.memora.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.adnan.memora.R
import com.adnan.memora.gallery.GalleryViewModel
import com.adnan.memora.gallery.LocalMedia
import kotlin.math.abs

@Composable
fun ViewerScreen(vm: GalleryViewModel, startIndex: Int, onBack: () -> Unit) {
    val items by vm.visible.collectAsState()
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val ctx = LocalContext.current
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }
    var showInfo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val deleteAction = rememberDeleteAction(onDone = { vm.refresh() })
    val white = IconButtonDefaults.iconButtonColors(contentColor = Color.White)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val m = items[page.coerceIn(0, items.lastIndex)]
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    // depth effect: neighbouring pages shrink and fade while swiping
                    val off = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                    val s = 1f - 0.12f * off
                    scaleX = s; scaleY = s; alpha = 1f - 0.5f * off
                },
                Alignment.Center,
            ) {
                AsyncImage(model = m.uri, contentDescription = m.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize())
                if (m.isVideo) {
                    FilledIconButton(
                        onClick = {
                            val i = Intent(Intent.ACTION_VIEW).setDataAndType(m.uri, m.mime)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            try { ctx.startActivity(i) } catch (_: ActivityNotFoundException) {}
                        },
                        modifier = Modifier.size(72.dp),
                    ) { Icon(Icons.Filled.PlayArrow, stringResource(R.string.play_video), Modifier.size(40.dp)) }
                }
            }
        }

        val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
                .statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, colors = white) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
            Text(current.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { shareMedia(ctx, listOf(current)) }, colors = white) {
                Icon(Icons.Filled.Share, stringResource(R.string.share))
            }
            IconButton(onClick = { confirmDelete = true }, colors = white) {
                Icon(Icons.Filled.Delete, stringResource(R.string.delete))
            }
            IconButton(onClick = { showInfo = !showInfo }, colors = white) {
                Icon(Icons.Filled.Info, stringResource(R.string.details))
            }
        }

        if (confirmDelete) DeleteConfirmDialog(
            count = 1,
            onConfirm = { confirmDelete = false; deleteAction(listOf(current)) },
            onDismiss = { confirmDelete = false },
        )

        AnimatedVisibility(
            visible = showInfo,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(300)) { it } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(220)),
        ) { DetailsCard(current) }
    }
}

@Composable
private fun DetailsCard(m: LocalMedia) {
    Card(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.details), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            Row2(stringResource(R.string.d_name), m.name)
            Row2(stringResource(R.string.d_type), m.mime)
            Row2(stringResource(R.string.d_size), formatSize(m.size))
            if (m.width > 0 && m.height > 0) Row2(stringResource(R.string.d_resolution), "${m.width} × ${m.height}")
            if (m.isVideo) Row2(stringResource(R.string.d_duration), formatDuration(m.durationMs))
            Row2(stringResource(R.string.d_taken), formatDateTime(m.dateTaken))
            Row2(stringResource(R.string.d_modified), formatDateTime(m.dateModified))
            Row2(stringResource(R.string.d_folder), m.bucket)
        }
    }
}

@Composable
private fun Row2(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp),
            style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
