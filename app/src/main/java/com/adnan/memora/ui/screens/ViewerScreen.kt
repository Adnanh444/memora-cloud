package com.adnan.memora.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.adnan.memora.R
import com.adnan.memora.gallery.GalleryViewModel
import com.adnan.memora.gallery.LocalMedia

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
    val white = IconButtonDefaults.iconButtonColors(contentColor = Color.White)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val m = items[page.coerceIn(0, items.lastIndex)]
            Box(Modifier.fillMaxSize(), Alignment.Center) {
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
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp)
                .background(Color.Black.copy(alpha = 0.35f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, colors = white) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
            Text(current.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { showInfo = !showInfo }, colors = white) {
                Icon(Icons.Filled.Info, stringResource(R.string.details))
            }
        }

        if (showInfo) {
            DetailsCard(current, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun DetailsCard(m: LocalMedia, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.details), style = MaterialTheme.typography.titleMedium)
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
