package com.adnan.memora.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.adnan.memora.R
import com.adnan.memora.gallery.GalleryViewModel
import com.adnan.memora.gallery.TrashedMedia
import com.adnan.memora.ui.theme.glass
import kotlin.math.ceil

@Composable
fun BinScreen(vm: GalleryViewModel, onBack: () -> Unit) {
    val trash by vm.trash.collectAsState()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmForever by remember { mutableStateOf(false) }
    val bin = rememberBinActions(onDone = { selected = emptySet(); vm.refresh() })
    BackHandler(selected.isNotEmpty()) { selected = emptySet() }
    val chosen = trash.filter { it.media.key in selected }.map { it.media.uri }

    if (confirmForever) MemoraSheet(onDismiss = { confirmForever = false }) { close ->
        Text(stringResource(R.string.forever_title, selected.size), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        Text(stringResource(R.string.forever_body), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp))
        SheetOption(Icons.Filled.DeleteForever, stringResource(R.string.delete_forever), destructive = true) {
            close { bin.deleteForever(chosen) }
        }
        TextButton(onClick = { close {} }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.cancel))
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (selected.isNotEmpty()) selected = emptySet() else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
            Text(
                if (selected.isEmpty()) stringResource(R.string.bin) else stringResource(R.string.selected_count, selected.size),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            )
        }
        Text(stringResource(R.string.bin_info), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (Build.VERSION.SDK_INT < 30) {
                Text(stringResource(R.string.bin_need_r), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp))
            } else if (trash.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Delete, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.bin_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 90.dp, top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(trash, key = { it.media.key }) { t ->
                        BinCell(t, t.media.key in selected) {
                            selected = if (t.media.key in selected) selected - t.media.key else selected + t.media.key
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = selected.isNotEmpty(),
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Row(
                    Modifier.padding(16.dp).glass (RoundedCornerShape(30.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = { bin.restore(chosen) }) {
                        Icon(Icons.Filled.RestoreFromTrash, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.restore))
                    }
                    TextButton(onClick = { confirmForever = true }) {
                        Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun BinCell(t: TrashedMedia, selected: Boolean, onClick: () -> Unit) {
    val onClickL by rememberUpdatedState(onClick)
    val days = if (t.expiresAt > 0) ceil((t.expiresAt - System.currentTimeMillis()) / 86_400_000.0).toInt().coerceAtLeast(0) else 30
    Box(Modifier.aspectRatio(1f).pointerInput(Unit) { detectTapGestures(onTap = { onClickL() }) }) {
        AsyncImage(
            model = t.media.uri, contentDescription = t.media.name, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().padding(if (selected) 10.dp else 0.dp)
                .clip(RoundedCornerShape(if (selected) 14.dp else 8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        )
        Text(
            stringResource(R.string.days_left, days), color = Color.White, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 7.dp, vertical = 2.dp),
        )
        if (selected) {
            Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopStart).padding(9.dp).size(22.dp))
        }
    }
}
