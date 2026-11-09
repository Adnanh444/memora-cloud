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
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.adnan.memora.R
import com.adnan.memora.backup.BackupEngine
import com.adnan.memora.cloud.CloudDownloader
import com.adnan.memora.cloud.CloudUri
import com.adnan.memora.cloud.DownloadState
import com.adnan.memora.gallery.GalleryViewModel
import com.adnan.memora.gallery.LocalMedia
import com.adnan.memora.telegram.StorageStatus
import com.adnan.memora.telegram.TelegramClient
import com.adnan.memora.ui.theme.ThemeToggle
import com.adnan.memora.ui.theme.GradientTitle
import com.adnan.memora.ui.theme.glass
import kotlinx.coroutines.launch

/** Everything that is backed up, shown with previews from the cloud. Originals are NOT stored on the phone. */
@Composable
fun CloudScreen(vm: GalleryViewModel, onOpenViewer: () -> Unit, onOpenStorage: () -> Unit, onSearch: () -> Unit = {}) {
    val ctx = LocalContext.current
    val rawCloud by vm.cloudFeed.collectAsState()
    val mediaFilter by vm.mediaFilter.collectAsState()
    var sortField by rememberSaveable { mutableStateOf(SortField.TAKEN.name) }
    var sortAsc by rememberSaveable { mutableStateOf(false) }
    val sortMode = SortMode(SortField.valueOf(sortField), sortAsc)
    val items = remember(rawCloud, mediaFilter, sortMode) { applyMediaFilter(rawCloud, mediaFilter).sortedFor(sortMode) }
    // Folders come from each file's own phone folder (stored in the caption), so they survive a reinstall.
    var manual by remember { mutableStateOf(com.adnan.memora.data.FolderPrefs.manual(ctx)) }
    val folders = remember(items, manual) {
        (items.map { it.bucket }.filter { it.isNotBlank() } + manual).distinct().sorted()
    }
    var newFolder by remember { mutableStateOf(false) }
    var folderFilter by rememberSaveable { mutableStateOf("") }
    val shown = remember(items, folderFilter) {
        if (folderFilter.isEmpty()) items else items.filter { it.bucket == folderFilter }
    }
    val selecting by vm.selecting.collectAsState()
    val cloudAll by vm.cloudMedia.collectAsState()
    val dl by CloudDownloader.state.collectAsState()
    val status by remember { TelegramClient.get(ctx) }.storageStatus.collectAsState()
    DisposableEffect(Unit) { onDispose { vm.clearSelection() } }

    if (newFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolder = false },
            title = { Text(stringResource(R.string.folder_new)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(R.string.folder_name)) })
            },
            confirmButton = {
                TextButton(onClick = {
                    com.adnan.memora.data.FolderPrefs.add(ctx, name)
                    manual = com.adnan.memora.data.FolderPrefs.manual(ctx)
                    folderFilter = name.trim().replace('/', '-')
                    newFolder = false
                }) { Text(stringResource(R.string.folder_create)) }
            },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    val header = rememberCollapsingHeader()
    var countersVisible by rememberCountersVisible("cloud")
    Column(Modifier.fillMaxSize().statusBarsPadding().nestedScroll(header.connection)) {
        Crossfade(selecting, label = "cloudHeader") { sel ->
            if (sel) CloudSelectionHeader(vm, shown)
            else Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GradientTitle(stringResource(R.string.nav_cloud), Modifier.weight(1f)); IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, stringResource(R.string.lp_search_cloud)) }; CounterToggle(countersVisible) { countersVisible = !countersVisible }; SortButton(sortMode) { sortField = it.field.name; sortAsc = it.asc }; ThemeToggle()
                }

            }
        }
        if (!selecting && countersVisible && status is StorageStatus.Verified) CoveragePanel(cloudAll.count { !it.isVideo }, cloudAll.count { it.isVideo }, null, null, header.compact, header::toggle)
        if (status !is StorageStatus.Verified) Text(stringResource(R.string.lp_verify_to_count), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        dl?.let { DownloadStrip(it) }
        if (!selecting) {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = folderFilter.isEmpty(),
                        onClick = { folderFilter = "" },
                        label = { Text(stringResource(R.string.folder_all)) },
                    )
                }
                item {
                    FilterChip(
                        selected = false,
                        onClick = { newFolder = true },
                        label = { Text(stringResource(R.string.folder_new)) },
                        leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) },
                    )
                }
                items(folders, key = { it }) { f ->
                    FilterChip(
                        selected = folderFilter == f,
                        onClick = { folderFilter = if (folderFilter == f) "" else f },
                        label = { Text(f) },
                    )
                }
            }
        }
        if (shown.isEmpty() && items.isNotEmpty()) {
            Text(
                stringResource(R.string.folder_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
        if (shown.isNotEmpty()) {
            MediaGrid(
                list = shown, vm = vm,
                onOpen = { i -> vm.openViewer(shown, i); onOpenViewer() },
                modifier = Modifier.fillMaxSize(), showBadges = false, gridKey = "cloud",
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
        SelectionCount(selection.size, Modifier.weight(1f))
        IconButton(onClick = { if (pool.isNotEmpty() && selection.containsAll(pool.map { it.key })) vm.selection.value = emptySet() else vm.selectAll(pool) }) { SelectionGlyph(pool.isNotEmpty() && selection.containsAll(pool.map { it.key })) }
        IconButton(onClick = {
            CloudDownloader.start(ctx, vm.selectedCloud())
            Toast.makeText(ctx, R.string.cl_downloading, Toast.LENGTH_SHORT).show()
            vm.clearSelection()
        }, enabled = selection.isNotEmpty()) { Icon(Icons.Filled.Download, stringResource(R.string.cl_download)) }
        var moving by remember { mutableStateOf(false) }
        if (moving) {
            val names = (pool.map { it.bucket } + com.adnan.memora.data.FolderPrefs.manual(ctx) + "Unfiled")
                .filter { it.isNotBlank() }.distinct().sorted()
            AlertDialog(
                onDismissRequest = { moving = false },
                title = { Text(stringResource(R.string.folder_move)) },
                text = {
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        names.forEach { n ->
                            Text(
                                n, modifier = Modifier.fillMaxWidth().clickable {
                                    val keys = vm.selectedCloud().map { it.key }
                                    scope.launch {
                                        BackupEngine.setFolder(ctx, keys, if (n == "Unfiled") null else n)
                                        vm.clearSelection(); vm.refresh()
                                    }
                                    moving = false
                                }.padding(vertical = 12.dp),
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { moving = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        IconButton(onClick = { moving = true }, enabled = selection.isNotEmpty()) {
            Icon(Icons.Filled.DriveFileMove, stringResource(R.string.folder_move))
        }
        IconButton(onClick = { confirm = true }, enabled = selection.isNotEmpty()) {
            ActionGlyph(Icons.Filled.Delete, stringResource(R.string.delete))
        }
        OrgMenu(vm, enabled = selection.isNotEmpty(), onDone = { vm.clearSelection() }, pickItems = { vm.selectedAny() })
    }
}
