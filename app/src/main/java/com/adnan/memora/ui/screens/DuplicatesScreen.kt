package com.adnan.memora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.adnan.memora.R
import com.adnan.memora.backup.DupState
import com.adnan.memora.backup.DuplicateScanner
import com.adnan.memora.data.BackupState
import com.adnan.memora.gallery.GalleryViewModel
import com.adnan.memora.gallery.LocalMedia
import com.adnan.memora.ui.theme.glass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(vm: GalleryViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val all by vm.all.collectAsState()
    val state by DuplicateScanner.state.collectAsState()
    val itemBackup by vm.itemBackup.collectAsState()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf(false) }
    val deleteAction = rememberDeleteAction(onDone = {
        DuplicateScanner.prune(selected)
        selected = emptySet()
        vm.refresh()
    })

    if (confirm) DeleteSheet(
        count = selected.size,
        onDeletePhone = { deleteAction(all.filter { it.key in selected }) },
        onDismiss = { confirm = false },
    )

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.dup_title)) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            },
        )
        when (val s = state) {
            is DupState.Idle -> Column(
                Modifier.fillMaxWidth().padding(16.dp).glass().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.dup_intro), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { DuplicateScanner.start(ctx, all) }, enabled = all.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dup_scan))
                }
            }
            is DupState.Scanning -> Column(
                Modifier.fillMaxWidth().padding(16.dp).glass().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.dup_scanning, s.done, s.total), style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(
                    progress = { if (s.total > 0) s.done.toFloat() / s.total else 0f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
                OutlinedButton(onClick = { DuplicateScanner.cancel() }) { Text(stringResource(R.string.dup_cancel)) }
            }
            is DupState.Done -> {
                val groups = s.groups
                val blocked = groups.any { g -> g.items.isNotEmpty() && g.items.all { it.key in selected } }
                if (groups.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp).glass().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.dup_none), style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { DuplicateScanner.cancel(); DuplicateScanner.start(ctx, all) }) {
                            Text(stringResource(R.string.dup_scan))
                        }
                    }
                } else {
                    Text(stringResource(R.string.dup_hint), style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(groups, key = { it.hash }) { g ->
                            Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    stringResource(R.string.dup_group, g.items.size, formatSize(g.items[0].size)),
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                                )
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    g.items.forEach { m ->
                                        DupItem(
                                            m = m, selected = m.key in selected,
                                            inCloud = itemBackup[m.key]?.state == BackupState.VERIFIED,
                                        ) { selected = if (m.key in selected) selected - m.key else selected + m.key }
                                    }
                                }
                            }
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (blocked) Text(stringResource(R.string.dup_keep_one), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                selected = groups.flatMap { g -> g.items.drop(1) }.map { it.key }.toSet()
                            }) { Text(stringResource(R.string.dup_select_extra)) }
                            Button(
                                onClick = { confirm = true },
                                enabled = selected.isNotEmpty() && !blocked,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            ) { Text(stringResource(R.string.dup_delete_selected, selected.size)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DupItem(m: LocalMedia, selected: Boolean, inCloud: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.width(116.dp)) {
        Box(Modifier.size(116.dp).clip(shape).clickable(onClick = onClick)) {
            AsyncImage(
                model = m.uri, contentDescription = m.name, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)
                    .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.error, shape) else Modifier),
            )
            if (selected) {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(24.dp))
            }
            if (inCloud) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f)).padding(4.dp)) {
                    Icon(Icons.Filled.CloudDone, stringResource(R.string.dup_in_cloud), tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(m.bucket, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp))
        Text(m.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
