package com.adnan.memora.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adnan.memora.R
import com.adnan.memora.gallery.CollectionKind
import com.adnan.memora.gallery.GalleryViewModel

/** Favorites, Archive, or one album. Works with phone and cloud items together. */
@Composable
fun CollectionScreen(vm: GalleryViewModel, onBack: () -> Unit, onOpenViewer: () -> Unit) {
    val kind = vm.collectionKind
    val albumId = vm.collectionAlbumId
    val flow = remember(kind, albumId) {
        when (kind) {
            CollectionKind.FAVORITES -> vm.favoriteMedia
            CollectionKind.ARCHIVE -> vm.archiveMedia
            CollectionKind.ALBUM -> vm.albumMedia(albumId)
        }
    }
    val items by flow.collectAsState()
    val albums by vm.albums.collectAsState()
    val selecting by vm.selecting.collectAsState()
    val albumName = albums.firstOrNull { it.id == albumId }?.name ?: ""
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { vm.clearSelection() } }

    if (kind == CollectionKind.ALBUM && albumName.isEmpty() && albums.isNotEmpty()) {
        LaunchedEffect(Unit) { onBack() }   // the album was deleted
    }
    if (rename) AlbumNameSheet(
        title = stringResource(R.string.og_rename), initial = albumName, action = stringResource(R.string.og_save),
        onConfirm = { vm.renameAlbum(albumId, it) }, onDismiss = { rename = false },
    )
    if (confirmDelete) MemoraSheet(onDismiss = { confirmDelete = false }) { close ->
        Text(stringResource(R.string.og_delete_album), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        Text(stringResource(R.string.og_delete_album_body), color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp))
        SheetOption(Icons.Filled.Delete, stringResource(R.string.og_delete_album), destructive = true) {
            close { vm.deleteAlbum(albumId) }
        }
        TextButton(onClick = { close {} }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.cancel))
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Crossfade(selecting, label = "collHeader") { sel ->
            if (sel) CollectionSelectionHeader(vm, items, kind, albumId)
            else Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Column(Modifier.weight(1f)) {
                    Text(
                        when (kind) {
                            CollectionKind.FAVORITES -> stringResource(R.string.og_favorites)
                            CollectionKind.ARCHIVE -> stringResource(R.string.og_archive)
                            CollectionKind.ALBUM -> albumName
                        },
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(stringResource(R.string.items_count, items.size), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (kind == CollectionKind.ALBUM) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.og_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.og_rename)) }, onClick = { menu = false; rename = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.og_delete_album)) }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                }
            }
        }
        if (kind == CollectionKind.FAVORITES) {
            Text(stringResource(R.string.rf_favorites_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        if (kind == CollectionKind.ARCHIVE) {
            Text(stringResource(R.string.og_archive_hint), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                Text(stringResource(R.string.og_empty_collection), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            MediaGrid(
                list = items, vm = vm,
                onOpen = { i -> vm.openViewer(items, i); onOpenViewer() },
                modifier = Modifier.fillMaxSize(), showBadges = false, gridKey = "albums",
            )
        }
    }
}

@Composable
private fun CollectionSelectionHeader(vm: GalleryViewModel, pool: List<com.adnan.memora.gallery.LocalMedia>, kind: CollectionKind, albumId: String) {
    val favoriteAction = rememberPhoneFavoriteAction { vm.refresh() }
    val selection by vm.selection.collectAsState()
    BackHandler { vm.clearSelection() }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.clearSelection() }) { Icon(Icons.Filled.Close, stringResource(R.string.clear_selection)) }
        Text(
            if (selection.isEmpty()) stringResource(R.string.select_items) else stringResource(R.string.selected_count, selection.size),
            style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { vm.selectAll(pool) }) { Icon(Icons.Filled.SelectAll, stringResource(R.string.select_all)) }
        TextButton(
            enabled = selection.isNotEmpty(),
            onClick = {
                val picked = vm.selectedAny()
                when (kind) {
                    CollectionKind.FAVORITES -> favoriteAction(picked, false)
                    CollectionKind.ARCHIVE -> vm.setArchived(picked, false)
                    CollectionKind.ALBUM -> vm.removeFromAlbum(albumId, picked)
                }
                vm.clearSelection()
            },
        ) {
            Text(
                stringResource(
                    when (kind) {
                        CollectionKind.FAVORITES -> R.string.og_unfavorite
                        CollectionKind.ARCHIVE -> R.string.og_unarchive
                        CollectionKind.ALBUM -> R.string.og_remove_from_album
                    }
                )
            )
        }
    }
}
