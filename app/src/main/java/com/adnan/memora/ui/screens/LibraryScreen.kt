package com.adnan.memora.ui.screens

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.adnan.memora.R
import com.adnan.memora.gallery.FolderInfo
import com.adnan.memora.gallery.GalleryViewModel
import com.adnan.memora.ui.theme.ThemeToggle
import com.adnan.memora.ui.theme.GradientTitle
import com.adnan.memora.ui.theme.brandBrush
import com.adnan.memora.ui.theme.glass

@Composable
fun LibraryScreen(
    vm: GalleryViewModel,
    onFolder: (String) -> Unit,
    onBin: () -> Unit,
    onCloudBin: () -> Unit,
    onDuplicates: () -> Unit,
    onFavorites: () -> Unit,
    onArchive: () -> Unit,
    onAlbum: (String) -> Unit,
    onFreeUp: () -> Unit,
) {
    val folders by vm.folders.collectAsState()
    val trash by vm.trash.collectAsState()
    val cloudTrash by vm.cloudTrash.collectAsState()
    val albums by vm.albumSummaries.collectAsState()
    val favs by vm.favoriteMedia.collectAsState()
    val arch by vm.archiveMedia.collectAsState()
    var newAlbum by remember { mutableStateOf(false) }
    if (newAlbum) AlbumNameSheet(
        title = stringResource(R.string.og_new_album), initial = "", action = stringResource(R.string.og_create),
        onConfirm = { vm.createAlbum(it) }, onDismiss = { newAlbum = false },
    )
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GradientTitle(stringResource(R.string.rf_albums), Modifier.weight(1f))
            ThemeToggle()
        }
        PinchGrid("albums", Modifier.weight(1f)) { columns, gesture ->
        LazyVerticalGrid(
            modifier = gesture,
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.og_collections)) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryRow(Icons.Filled.Favorite, stringResource(R.string.og_favorites), stringResource(R.string.items_count, favs.size), onFavorites)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryRow(Icons.Filled.Archive, stringResource(R.string.og_archive), stringResource(R.string.items_count, arch.size), onArchive)
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.og_albums)) }
            item {
                Column(Modifier.clip(RoundedCornerShape(20.dp)).clickable { newAlbum = true }) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        Alignment.Center,
                    ) { Icon(Icons.Filled.Add, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary) }
                    Text(stringResource(R.string.og_new_album), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                }
            }
            items(albums, key = { "album:" + it.id }) { al ->
                Column(Modifier.clip(RoundedCornerShape(20.dp)).clickable { onAlbum(al.id) }) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        Alignment.Center,
                    ) {
                        if (al.cover != null) {
                            AsyncImage(model = al.cover.uri, contentDescription = al.name, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Filled.PhotoAlbum, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(al.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                    Text(stringResource(R.string.items_count, al.count), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.utilities)) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryRow(Icons.Outlined.ContentCopy, stringResource(R.string.dup_title), stringResource(R.string.dup_sub), onDuplicates)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryRow(Icons.Filled.DeleteSweep, stringResource(R.string.fu_title), stringResource(R.string.fu_sub), onFreeUp)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val sub = if (Build.VERSION.SDK_INT >= 30) stringResource(R.string.bin_sub, trash.size)
                else stringResource(R.string.bin_need_r)
                LibraryRow(Icons.Filled.Delete, stringResource(R.string.bin), sub, onBin)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryRow(Icons.Filled.CloudQueue, stringResource(R.string.cl_bin), stringResource(R.string.cl_bin_sub, cloudTrash.size), onCloudBin)
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.folders_section)) }
            items(folders, key = { it.name }) { f -> FolderCard(f) { onFolder(f.name) } }
        }
        }
    }
}

@Composable
private fun LibraryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), Alignment.Center) {
            Icon(icon, null, tint = when (icon) {
                Icons.Filled.Favorite -> MaterialTheme.colorScheme.error
                Icons.Filled.Archive -> MaterialTheme.colorScheme.tertiary
                Icons.Filled.CloudQueue -> MaterialTheme.colorScheme.secondary
                Icons.Filled.Delete -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            })
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}

@Composable
private fun FolderCard(f: FolderInfo, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        AsyncImage(
            model = f.cover.uri, contentDescription = f.name, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        )
        Text(f.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        Text(stringResource(R.string.items_count, f.count), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
fun FolderScreen(vm: GalleryViewModel, name: String, onBack: () -> Unit, onOpenViewer: () -> Unit) {
    val all by vm.all.collectAsState()
    val list = remember(all, name) { all.filter { it.bucket == name } }
    val selecting by vm.selecting.collectAsState()
    DisposableEffect(Unit) { onDispose { vm.clearSelection() } }
    if (list.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Crossfade(selecting, label = "folderHeader") { sel ->
            if (sel) SelectionHeader(vm, list)
            else Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.items_count, list.size), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        MediaGrid(list = list, vm = vm, onOpen = { i -> vm.openViewer(list, i); onOpenViewer() }, modifier = Modifier.fillMaxSize(), gridKey = "albums")
    }
}
