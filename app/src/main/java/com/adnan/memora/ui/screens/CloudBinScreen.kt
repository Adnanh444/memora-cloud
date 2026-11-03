package com.adnan.memora.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adnan.memora.R
import com.adnan.memora.backup.BackupEngine
import com.adnan.memora.cloud.CloudUri
import com.adnan.memora.gallery.GalleryViewModel
import com.adnan.memora.ui.theme.glass
import kotlinx.coroutines.launch

/** Cloud bin: deleted cloud items wait here 30 days (still in storage), then are permanently removed. */
@Composable
fun CloudBinScreen(vm: GalleryViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val trash by vm.cloudTrash.collectAsState()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmForever by remember { mutableStateOf(false) }
    BackHandler(selected.isNotEmpty()) { selected = emptySet() }
    val chosen = trash.filter { it.media.key in selected }.mapNotNull { CloudUri.ids(it.media.uri) }

    if (confirmForever) MemoraSheet(onDismiss = { confirmForever = false }) { close ->
        Text(stringResource(R.string.forever_title, selected.size), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        Text(stringResource(R.string.cl_bin_forever_body), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp))
        SheetOption(Icons.Filled.DeleteForever, stringResource(R.string.delete_forever), destructive = true) {
            close {
                scope.launch { BackupEngine.deleteCloudForever(ctx, chosen); selected = emptySet() }
            }
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
                if (selected.isEmpty()) stringResource(R.string.cl_bin) else stringResource(R.string.selected_count, selected.size),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            )
        }
        Text(stringResource(R.string.cl_bin_info), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (trash.isEmpty()) {
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
            CloudBinBar(
                visible = selected.isNotEmpty(),
                onRestore = { scope.launch { BackupEngine.restoreCloud(chosen); selected = emptySet() } },
                onDelete = { confirmForever = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun CloudBinBar(visible: Boolean, onRestore: () -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    AnimatedVisibility(visible = visible, modifier = modifier) {
        Row(
            Modifier.padding(16.dp).glass(RoundedCornerShape(30.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onRestore) {
                Icon(Icons.Filled.RestoreFromTrash, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.restore))
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
