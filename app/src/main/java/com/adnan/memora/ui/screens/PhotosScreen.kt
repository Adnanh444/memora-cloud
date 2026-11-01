package com.adnan.memora.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.adnan.memora.R
import com.adnan.memora.gallery.GalleryViewModel
import com.adnan.memora.gallery.LocalMedia
import com.adnan.memora.gallery.TypeFilter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private fun requiredMediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasMediaAccess(c: Context): Boolean = requiredMediaPermissions().any {
    ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED
}

private sealed class Entry {
    abstract val key: String
    data class Header(val label: String) : Entry() { override val key: String get() = "h:$label" }
    data class Item(val media: LocalMedia) : Entry() { override val key: String get() = media.key }
}

private fun dayStart(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun buildEntries(items: List<LocalMedia>, todayLabel: String, yesterdayLabel: String): List<Entry> {
    val fmt = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
    val today = dayStart(System.currentTimeMillis())
    val yesterday = dayStart(today - 1)
    val out = ArrayList<Entry>(items.size + 64)
    var lastDay = -1L
    for (m in items) {
        val d = dayStart(m.dateTaken)
        if (d != lastDay) {
            lastDay = d
            out.add(Entry.Header(when (d) { today -> todayLabel; yesterday -> yesterdayLabel; else -> fmt.format(d) }))
        }
        out.add(Entry.Item(m))
    }
    return out
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen(vm: GalleryViewModel, onOpen: (Int) -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasMediaAccess(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasMediaAccess(ctx)
        if (granted) vm.refresh()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = hasMediaAccess(ctx)
        if (granted) vm.refresh()
    }

    if (!granted) {
        PermissionPrompt(
            onGrant = { launcher.launch(requiredMediaPermissions()) },
            onSettings = {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                )
            },
        )
        return
    }

    val items by vm.visible.collectAsState()
    val selection by vm.selection.collectAsState()
    val buckets by vm.buckets.collectAsState()
    val bucket by vm.bucket.collectAsState()
    val type by vm.typeFilter.collectAsState()
    val loading by vm.loading.collectAsState()
    val selecting = selection.isNotEmpty()
    BackHandler(selecting) { vm.clearSelection() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (selecting) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.clearSelection() }) {
                    Icon(Icons.Filled.Close, stringResource(R.string.clear_selection))
                }
                Text(stringResource(R.string.selected_count, selection.size),
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.selectAll() }) {
                    Icon(Icons.Filled.SelectAll, stringResource(R.string.select_all))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nav_photos), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Filled.Refresh, stringResource(R.string.refresh))
                }
            }
        }

        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(type == TypeFilter.ALL, { vm.typeFilter.value = TypeFilter.ALL }, { Text(stringResource(R.string.filter_all)) })
            FilterChip(type == TypeFilter.PHOTOS, { vm.typeFilter.value = TypeFilter.PHOTOS }, { Text(stringResource(R.string.filter_photos)) })
            FilterChip(type == TypeFilter.VIDEOS, { vm.typeFilter.value = TypeFilter.VIDEOS }, { Text(stringResource(R.string.filter_videos)) })
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                AssistChip(
                    onClick = { vm.bucket.value = null },
                    label = { Text(stringResource(R.string.folders_all)) },
                    colors = if (bucket == null) AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer) else AssistChipDefaults.assistChipColors(),
                )
            }
            items(buckets) { b ->
                AssistChip(
                    onClick = { vm.bucket.value = if (bucket == b) null else b },
                    label = { Text(b) },
                    colors = if (bucket == b) AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer) else AssistChipDefaults.assistChipColors(),
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                if (loading) CircularProgressIndicator()
                else Text(stringResource(R.string.empty_gallery), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val todayL = stringResource(R.string.today)
            val yesterdayL = stringResource(R.string.yesterday)
            val entries = remember(items, todayL, yesterdayL) { buildEntries(items, todayL, yesterdayL) }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(
                    count = entries.size,
                    key = { entries[it].key },
                    span = { if (entries[it] is Entry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                ) { i ->
                    when (val e = entries[i]) {
                        is Entry.Header -> Text(
                            e.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 6.dp),
                        )
                        is Entry.Item -> MediaCell(
                            m = e.media,
                            selected = e.media.key in selection,
                            selecting = selecting,
                            onClick = {
                                if (selecting) vm.toggle(e.media.key)
                                else onOpen(items.indexOfFirst { it.key == e.media.key })
                            },
                            onLongClick = { vm.toggle(e.media.key) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaCell(m: LocalMedia, selected: Boolean, selecting: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val pad by animateDpAsState(if (selected) 10.dp else 0.dp, label = "pad")
    Box(Modifier.aspectRatio(1f).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        AsyncImage(
            model = m.uri, contentDescription = m.name, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().padding(pad).clip(RoundedCornerShape(if (selected) 12.dp else 6.dp)),
        )
        if (m.isVideo) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(14.dp), tint = Color.White)
                Text(formatDuration(m.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (selecting) {
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(22.dp),
            )
        }
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), Alignment.Center) {
            Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.perm_body), textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) { Text(stringResource(R.string.perm_grant)) }
        TextButton(onClick = onSettings) { Text(stringResource(R.string.perm_settings)) }
    }
}
