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
fun PhotosScreen(vm: GalleryViewModel, onOpenViewer: () -> Unit, onSearch: () -> Unit) {
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
            else SearchPill(onSearch = onSearch, onSelect = { vm.enterSelectMode() })
        }
        BackupStrip(summary, onRetry = { vm.retryFailed() })
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
private fun SearchPill(onSearch: () -> Unit, onSelect: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            .glass(RoundedCornerShape(30.dp)).clickable(onClick = onSearch)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = cs.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.search_hint), Modifier.weight(1f), color = cs.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(
            Icons.Outlined.CheckCircle, stringResource(R.string.select_mode), tint = cs.onSurfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onSelect).padding(2.dp),
        )
    }
}

/** Backup status. Shows the real state of the upload queue. */
@Composable
fun BackupStrip(s: BackupSummary, onRetry: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).animateContentSize()) {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (s.phase) {
                BackupPhase.NOT_SET_UP -> {
                    Icon(Icons.Filled.CloudOff, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.backup_off), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
                BackupPhase.RUNNING -> {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.backing_up, s.done, s.total), style = MaterialTheme.typography.labelMedium)
                    if (s.currentName != null) {
                        Text(
                            "  ·  ${s.currentName} ${(s.currentProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                BackupPhase.IDLE -> {
                    if (s.total == 0) {
                        Icon(Icons.Filled.CloudQueue, null, Modifier.size(16.dp), tint = cs.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_ready), style = MaterialTheme.typography.labelMedium)
                    } else if (s.failed > 0) {
                        Icon(Icons.Filled.Error, null, Modifier.size(16.dp), tint = cs.error)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_some_failed, s.failed), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.backup_retry), style = MaterialTheme.typography.labelLarge, color = cs.primary,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onRetry).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    } else {
                        Icon(Icons.Filled.CloudDone, null, Modifier.size(16.dp), tint = cs.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_all_done), style = MaterialTheme.typography.labelMedium)
                    }
                }
                BackupPhase.PAUSED -> {
                    Icon(Icons.Filled.CloudOff, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.backup_paused), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (s.phase == BackupPhase.RUNNING) {
            LinearProgressIndicator(
                progress = { if (s.total > 0) s.done.toFloat() / s.total else 0f },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
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
