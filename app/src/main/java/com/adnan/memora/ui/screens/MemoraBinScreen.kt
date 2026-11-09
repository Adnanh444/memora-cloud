package com.adnan.memora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.adnan.memora.gallery.LocalMedia
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Sort
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

private enum class BinSort(val label: Int) {
    NEWEST(com.adnan.memora.R.string.sort_added),
    OLDEST(com.adnan.memora.R.string.sort_taken),
    NAME(com.adnan.memora.R.string.sort_name),
    SIZE(com.adnan.memora.R.string.sort_size),
}

/**
 * Memora's bin: what a delete moves out of the gallery, restorable for 30 days.
 * No Android dialog anywhere — the file was already moved by the time this screen opens.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MemoraBinScreen(vm: com.adnan.memora.gallery.GalleryViewModel, onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(MediaBin.items(ctx)) }
    var sort by remember { mutableStateOf(BinSort.NEWEST) }
    var sortOpen by remember { mutableStateOf(false) }
    val shown = remember(items, sort) {
        when (sort) {
            BinSort.NEWEST -> items.sortedByDescending { it.deletedAt }
            BinSort.OLDEST -> items.sortedBy { it.deletedAt }
            BinSort.NAME -> items.sortedBy { it.name.lowercase() }
            BinSort.SIZE -> items.sortedByDescending { it.size }
        }
    }
    var pending by remember { mutableStateOf<MediaBin.Item?>(null) }
    var preview by remember { mutableStateOf<MediaBin.Item?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
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
                            if (selecting) stringResource(R.string.selected_count, selected.size)
                            else stringResource(R.string.mb_count, items.size, formatSize(size)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (selecting) {
                        // Selection actions, same shape as the cloud bin
                        IconButton(onClick = {
                            items.filter { it.id in selected }.forEach { MediaBin.restore(ctx, it) }
                            selected = emptySet(); reload(); vm.refresh()
                        }) { Icon(Icons.Filled.Restore, stringResource(R.string.mb_restore)) }
                        IconButton(onClick = { confirmBulkDelete = true }) {
                            Icon(Icons.Filled.DeleteForever, stringResource(R.string.mb_delete_forever), tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) }
                    } else if (items.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { sortOpen = true }) {
                                Icon(Icons.Filled.Sort, stringResource(R.string.sort_title))
                            }
                            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                                BinSort.entries.forEach { s2 ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(s2.label)) },
                                        trailingIcon = { if (s2 == sort) Icon(Icons.Filled.Check, null) },
                                        onClick = { sort = s2; sortOpen = false },
                                    )
                                }
                            }
                        }
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
                items(shown, key = { it.id }) { item ->
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .combinedClickable(
                            onClick = {
                                if (selecting) selected = if (item.id in selected) selected - item.id else selected + item.id
                                else preview = item
                            },
                            onLongClick = { selected = selected + item.id },
                        )) {
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
                        if (item.id in selected) {
                            Box(
                                Modifier.matchParentSize()
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                            )
                            Icon(
                                Icons.Filled.CheckCircle, null,
                                Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
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

    preview?.let { item ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { preview = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                if (item.isVideo) {
                    MediaVideoPlayer(
                        media = LocalMedia(
                            uri = android.net.Uri.fromFile(File(MediaBin.dir(ctx), item.id)),
                            name = item.name, mime = item.mime, size = item.size, dateTaken = item.deletedAt,
                            dateModified = item.deletedAt, width = 0, height = 0, durationMs = 0L,
                            isVideo = true, bucket = item.bucket,
                        ),
                        onClose = { preview = null },
                    )
                } else {
                    AsyncImage(
                        model = File(MediaBin.dir(ctx), item.id), contentDescription = item.name,
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(12.dp),
                    )
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(onClick = {
                        val ok = MediaBin.restore(ctx, item)
                        preview = null; reload(); if (ok) vm.refresh()
                    }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.mb_restore)) }
                    OutlinedButton(onClick = { pending = item; preview = null }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.mb_delete_forever))
                    }
                    IconButton(onClick = {
                        val idx = shown.indexOfFirst { it.id == item.id }
                        if (idx > 0) preview = shown[idx - 1]
                    }, enabled = shown.indexOfFirst { it.id == item.id } > 0) {
                        Icon(Icons.Filled.ChevronLeft, stringResource(R.string.back), tint = Color.White)
                    }
                    IconButton(onClick = {
                        val idx = shown.indexOfFirst { it.id == item.id }
                        if (idx in 0 until shown.lastIndex) preview = shown[idx + 1]
                    }, enabled = shown.indexOfFirst { it.id == item.id } in 0 until shown.lastIndex) {
                        Icon(Icons.Filled.ChevronRight, null, tint = Color.White)
                    }
                    IconButton(onClick = { preview = null }) { Icon(Icons.Filled.Close, stringResource(R.string.cancel), tint = Color.White) }
                }
            }
        }
    }

    if (confirmBulkDelete) {
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = { Text(stringResource(R.string.mb_delete_forever)) },
            text = { Text(stringResource(R.string.selected_count, selected.size)) },
            confirmButton = {
                TextButton(onClick = {
                    items.filter { it.id in selected }.forEach { MediaBin.deleteForever(ctx, it) }
                    confirmBulkDelete = false; selected = emptySet(); reload()
                }) { Text(stringResource(R.string.mb_delete_forever), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmBulkDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
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
