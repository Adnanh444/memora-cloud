package com.adnan.memora.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.adnan.memora.R
import com.adnan.memora.backup.BackupEngine
import com.adnan.memora.backup.BackupScheduler
import com.adnan.memora.gallery.BackupPhase
import com.adnan.memora.gallery.BackupSummary
import com.adnan.memora.gallery.GalleryViewModel
import com.adnan.memora.ui.theme.appear
import com.adnan.memora.ui.theme.glass

private fun requiredMediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
}

private fun hasMediaAccess(c: Context): Boolean = requiredMediaPermissions().any {
    ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(vm: GalleryViewModel, onOpenViewer: () -> Unit, onSearch: () -> Unit, onBackup: () -> Unit, onSettings: () -> Unit) {
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

    val items by vm.all.collectAsState()
    val loading by vm.loading.collectAsState()
    val selecting by vm.selecting.collectAsState()
    val summary by vm.backupSummary.collectAsState()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Crossfade(selecting, label = "header") { sel ->
            if (sel) SelectionHeader(vm, items)
            else SearchPill(onSearch = onSearch, onSelect = { vm.enterSelectMode() }, onSettings = onSettings)
        }
        BackupStrip(
            summary, onOpen = onBackup,
            onAction = {
                when {
                    summary.paused -> BackupEngine.resume(ctx)
                    summary.phase == BackupPhase.RUNNING -> BackupEngine.pause(ctx)
                    else -> BackupScheduler.runNow(ctx)
                }
            },
        )
        PullToRefreshBox(isRefreshing = loading, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
            if (items.isEmpty()) {
                if (loading) SkeletonGrid()
                else Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(stringResource(R.string.empty_gallery), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                MediaGrid(
                    list = items, vm = vm,
                    onOpen = { i -> vm.openViewer(items, i); onOpenViewer() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun SearchPill(onSearch: () -> Unit, onSelect: () -> Unit, onSettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            .glass(RoundedCornerShape(30.dp)).clickable(onClick = onSearch)
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = cs.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.search_hint), Modifier.weight(1f), color = cs.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(
            Icons.Outlined.CheckCircle, stringResource(R.string.select_mode), tint = cs.onSurfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onSelect).padding(6.dp),
        )
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(cs.primary)
                .clickable(onClick = onSettings),
            Alignment.Center,
        ) { Text("M", color = cs.onPrimary, fontWeight = FontWeight.Bold) }
    }
}

/** Backup status with a Pause / Resume button. Shows real state only. */
@Composable
fun BackupStrip(s: BackupSummary, onOpen: () -> Unit, onAction: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val showAction = s.paused || s.phase == BackupPhase.RUNNING || (s.phase == BackupPhase.PAUSED && s.waiting > 0)
    val showPlay = s.paused || s.phase != BackupPhase.RUNNING
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clip(shape).background(cs.surfaceContainerHigh).border(1.dp, cs.outlineVariant, shape)
                .clickable(onClick = onOpen).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    s.phase == BackupPhase.NOT_SET_UP -> {
                        Icon(Icons.Filled.CloudOff, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.backup_off), style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                    }
                    s.paused -> {
                        Icon(Icons.Filled.Pause, null, Modifier.size(20.dp), tint = cs.tertiary)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.backup_user_paused), style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                    }
                    s.phase == BackupPhase.RUNNING -> {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(stringResource(R.string.backing_up, s.done, s.total), style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                            if (s.currentName != null) {
                                Text(
                                    "${s.currentName} · ${(s.currentProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    s.phase == BackupPhase.IDLE -> {
                        Icon(Icons.Filled.CloudDone, null, Modifier.size(20.dp), tint = cs.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.backup_all_done), style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                    }
                    else -> {
                        Icon(Icons.Filled.CloudOff, null, Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.backup_paused), style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                    }
                }
            }
            if (showAction) {
                FilledTonalIconButton(onClick = onAction) {
                    Icon(
                        if (showPlay) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        stringResource(if (showPlay) R.string.bk_resume else R.string.bk_pause),
                    )
                }
            }
        }
        if (s.phase == BackupPhase.RUNNING && !s.paused) {
            LinearProgressIndicator(
                progress = { if (s.total > 0) s.done.toFloat() / s.total else 0f },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(32.dp).appear(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(104.dp).glass(androidx.compose.foundation.shape.CircleShape), Alignment.Center) {
            Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
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
