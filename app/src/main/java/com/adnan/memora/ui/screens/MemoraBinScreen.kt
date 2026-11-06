package com.adnan.memora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.adnan.memora.R
import com.adnan.memora.data.MediaBin
import java.io.File

/**
 * Memora's bin: what a delete moves out of the gallery, restorable for 30 days.
 * No Android dialog anywhere — the file was already moved by the time this screen opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoraBinScreen(vm: com.adnan.memora.gallery.GalleryViewModel, onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(MediaBin.items(ctx)) }
    var pending by remember { mutableStateOf<MediaBin.Item?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val size = remember(items) { items.sumOf { it.size } }

    fun reload() { items = MediaBin.items(ctx) }

    LaunchedEffect(Unit) { MediaBin.purge(ctx); reload() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.mb_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.mb_count, items.size, formatSize(size)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (items.isNotEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }) { Text(stringResource(R.string.mb_empty_action)) }
                    }
                }
            }
        },
    ) { pad ->
        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.DeleteForever, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.mb_empty), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.mb_empty_sub),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { pending = item }) {
                        AsyncImage(
                            model = File(MediaBin.dir(ctx), item.id),
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (item.isVideo) {
                            Icon(
                                Icons.Filled.PlayArrow, null,
                                Modifier.align(Alignment.Center).size(30.dp), tint = Color.White,
                            )
                        }
                        Text(
                            stringResource(R.string.mb_days_left, item.daysLeft),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                                .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }

    pending?.let { item ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(item.name, maxLines = 1) },
            text = {
                Text(stringResource(R.string.mb_days_left, item.daysLeft) + " · " + formatSize(item.size))
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = MediaBin.restore(ctx, item)
                    pending = null
                    reload()
                    note = ctx.getString(if (ok) R.string.mb_restored else R.string.mb_restore_failed)
                    if (ok) vm.refresh()
                }) { Text(stringResource(R.string.mb_restore)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    MediaBin.deleteForever(ctx, item)
                    pending = null
                    reload()
                }) { Text(stringResource(R.string.mb_delete_forever)) }
            },
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.mb_empty_action)) },
            text = { Text(stringResource(R.string.mb_empty_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    MediaBin.empty(ctx); confirmEmpty = false; reload()
                }) { Text(stringResource(R.string.mb_delete_forever)) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    note?.let { msg ->
        LaunchedEffect(msg) {
            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
            note = null
        }
    }
}
