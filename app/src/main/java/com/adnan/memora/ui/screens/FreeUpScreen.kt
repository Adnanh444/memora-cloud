package com.adnan.memora.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.adnan.memora.R
import com.adnan.memora.backup.BackupDb
import com.adnan.memora.backup.BackupEntry
import com.adnan.memora.data.BackupState
import com.adnan.memora.gallery.GalleryViewModel
import com.adnan.memora.gallery.LocalMedia
import com.adnan.memora.telegram.TelegramClient
import com.adnan.memora.ui.theme.glass
import kotlinx.coroutines.launch

/**
 * Free up space: removes the phone copy of files that are VERIFIED as backed up.
 * Every item is re-checked with the cloud right before deleting. Cloud copies are never touched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeUpScreen(vm: GalleryViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by vm.all.collectAsState()
    val itemBackup by vm.itemBackup.collectAsState()
    val eligible = remember(all, itemBackup) { all.filter { itemBackup[it.key]?.state == BackupState.VERIFIED } }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val chosen = eligible.filter { it.key in selected }
    val chosenBytes = chosen.sumOf { it.size }
    val deleteAction = rememberDeleteAction(onDone = { selected = emptySet(); vm.refresh() })

    if (confirm) MemoraSheet(onDismiss = { confirm = false }) { close ->
        Text(stringResource(R.string.fu_confirm_title, formatSize(chosenBytes)), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        Text(stringResource(R.string.fu_confirm_body, chosen.size), color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp))
        SheetOption(Icons.Filled.DeleteSweep, stringResource(R.string.fu_free, formatSize(chosenBytes)), destructive = true) {
            close {
                scope.launch {
                    checking = true
                    val ok = verifyAll(ctx, chosen)
                    checking = false
                    if (ok.isEmpty()) {
                        Toast.makeText(ctx, R.string.fu_failed, Toast.LENGTH_LONG).show()
                    } else {
                        if (ok.size < chosen.size) {
                            Toast.makeText(ctx, ctx.getString(R.string.fu_skipped, chosen.size - ok.size), Toast.LENGTH_LONG).show()
                        }
                        deleteAction(ok)
                    }
                }
            }
        }
        TextButton(onClick = { close {} }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.cancel))
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.fu_title)) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
        Text(stringResource(R.string.fu_intro), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        if (eligible.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), Alignment.Center) {
                Text(stringResource(R.string.fu_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.items_count, eligible.size) + " · " + formatSize(eligible.sumOf { it.size }),
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { selected = if (selected.size == eligible.size) emptySet() else eligible.map { it.key }.toSet() }) {
                    Text(stringResource(R.string.select_all))
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp), modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(eligible, key = { it.key }) { m ->
                    val on = m.key in selected
                    val shape = RoundedCornerShape(6.dp)
                    Box(Modifier.aspectRatio(1f).clip(shape).clickable { selected = if (on) selected - m.key else selected + m.key }) {
                        AsyncImage(
                            model = m.uri, contentDescription = m.name, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)
                                .then(if (on) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
                        )
                        if (on) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp))
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp)) {
                if (checking) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    Text(stringResource(R.string.fu_verifying), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Button(onClick = { confirm = true }, enabled = chosen.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.fu_free, formatSize(chosenBytes)))
                    }
                }
            }
        }
    }
}

/** Keeps only items that the cloud confirms right now (same file size). Anything unverified is NOT deleted. */
private suspend fun verifyAll(ctx: android.content.Context, items: List<LocalMedia>): List<LocalMedia> {
    val tg = TelegramClient.get(ctx)
    if (!tg.awaitUsable(30_000)) return emptyList()
    val dao = BackupDb.get(ctx).dao()
    val ok = ArrayList<LocalMedia>()
    for (m in items) {
        val e = dao.get(m.key) ?: continue
        if ((e.state != BackupEntry.ST_VERIFIED && e.state != BackupEntry.ST_DUPLICATE) || e.messageId == 0L) continue
        if (tg.verifyMessage(e.chatId, e.messageId, m.size)) ok.add(m)
    }
    return ok
}
