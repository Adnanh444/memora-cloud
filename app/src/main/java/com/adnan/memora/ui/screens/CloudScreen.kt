package com.adnan.memora.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adnan.memora.R
import com.adnan.memora.backup.BackupEngine
import com.adnan.memora.cloud.CloudDownloader
import com.adnan.memora.cloud.CloudUri
import com.adnan.memora.cloud.DownloadState
import com.adnan.memora.gallery.GalleryViewModel
import com.adnan.memora.gallery.LocalMedia
import com.adnan.memora.telegram.StorageStatus
import com.adnan.memora.telegram.TelegramClient
import com.adnan.memora.ui.theme.GradientTitle
import com.adnan.memora.ui.theme.glass
import kotlinx.coroutines.launch

/** Everything that is backed up, shown with previews from the cloud. Originals are NOT stored on the phone. */
@Composable
fun CloudScreen(vm: GalleryViewModel, onOpenViewer: () -> Unit, onOpenStorage: () -> Unit) {
    val ctx = LocalContext.current
    val items by vm.cloudMedia.collectAsState()
    val selecting by vm.selecting.collectAsState()
    val dl by CloudDownloader.state.collectAsState()
    val status by remember { TelegramClient.get(ctx) }.storageStatus.collectAsState()
    DisposableEffect(Unit) { onDispose { vm.clearSelection() } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Crossfade(selecting, label = "cloudHeader") { sel ->
            if (sel) CloudSelectionHeader(vm, items)
            else Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)) {
                GradientTitle(stringResource(R.string.nav_cloud))
                Text(stringResource(R.string.items_count, items.size), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        dl?.let { DownloadStrip(it) }
        if (items.isNotEmpty()) {
            MediaGrid(
                list = items, vm = vm,
                onOpen = { i -> vm.openViewer(items, i); onOpenViewer() },
                modifier = Modifier.fillMaxSize(), showBadges = false,
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.CloudQueue, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.cl_empty), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.cl_empty_sub), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (status !is StorageStatus.Verified) {
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onOpenStorage) { Text(stringResource(R.string.cl_connect)) }
                }
            }
        }
    }
}

@Composable
private fun DownloadStrip(s: DownloadState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.cl_dl_progress, s.done + 1, s.total, s.current ?: ""),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1,
            )
            TextButton(onClick = { CloudDownloader.cancel() }) { Text(stringResource(R.string.cancel)) }
        }
        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp))
    }
}

@Composable
fun CloudSelectionHeader(vm: GalleryViewModel, pool: List<LocalMedia>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val selection by vm.selection.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    BackHandler { vm.clearSelection() }
    if (confirm) CloudDeleteSheet(
        count = selection.size,
        onConfirm = {
            val msgs = vm.selectedCloud().mapNotNull { CloudUri.ids(it.uri) }
            scope.launch { BackupEngine.trashCloudMessages(msgs) }
            vm.clearSelection()
        },
        onDismiss = { confirm = false },
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.clearSelection() }) { Icon(Icons.Filled.Close, stringResource(R.string.clear_selection)) }
        Text(
            if (selection.isEmpty()) stringResource(R.string.select_items) else stringResource(R.string.selected_count, selection.size),
            style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { vm.selectAll(pool) }) { Icon(Icons.Filled.SelectAll, stringResource(R.string.select_all)) }
        IconButton(onClick = {
            CloudDownloader.start(ctx, vm.selectedCloud())
            Toast.makeText(ctx, R.string.cl_downloading, Toast.LENGTH_SHORT).show()
            vm.clearSelection()
        }, enabled = selection.isNotEmpty()) { Icon(Icons.Filled.Download, stringResource(R.string.cl_download)) }
        IconButton(onClick = { confirm = true }, enabled = selection.isNotEmpty()) {
            Icon(Icons.Filled.Delete, stringResource(R.string.delete))
        }
    }
}
