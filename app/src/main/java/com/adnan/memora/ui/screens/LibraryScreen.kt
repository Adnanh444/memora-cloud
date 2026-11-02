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
import androidx.compose.material.icons.filled.Delete
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
import com.adnan.memora.ui.theme.GradientTitle
import com.adnan.memora.ui.theme.brandBrush
import com.adnan.memora.ui.theme.glass

@Composable
fun LibraryScreen(vm: GalleryViewModel, onFolder: (String) -> Unit, onBin: () -> Unit) {
    val folders by vm.folders.collectAsState()
    val trash by vm.trash.collectAsState()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        GradientTitle(stringResource(R.string.nav_library), Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.utilities)) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val sub = if (Build.VERSION.SDK_INT >= 30) stringResource(R.string.bin_sub, trash.size)
                else stringResource(R.string.bin_need_r)
                Row(
                    Modifier.fillMaxWidth().glass().clickable(onClick = onBin).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(brandBrush()), Alignment.Center) {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(stringResource(R.string.bin), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.folders_section)) }
            items(folders, key = { it.name }) { f -> FolderCard(f) { onFolder(f.name) } }
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
        MediaGrid(list = list, vm = vm, onOpen = { i -> vm.openViewer(list, i); onOpenViewer() }, modifier = Modifier.fillMaxSize())
    }
}
