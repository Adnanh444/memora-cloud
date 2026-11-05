package com.adnan.memora.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.adnan.memora.R
import com.adnan.memora.backup.BackupConfig
import com.adnan.memora.backup.BackupEngine
import com.adnan.memora.backup.BackupScheduler
import com.adnan.memora.backup.BackupSettings
import com.adnan.memora.data.BackupState
import com.adnan.memora.gallery.BackupPhase
import com.adnan.memora.gallery.GalleryViewModel
import com.adnan.memora.telegram.StorageStatus
import com.adnan.memora.telegram.TelegramClient
import com.adnan.memora.ui.theme.appear
import com.adnan.memora.ui.theme.glass
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(vm: GalleryViewModel, onBack: () -> Unit, onOpenTelegram: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tg = remember { TelegramClient.get(ctx) }
    val tgStatus by tg.storageStatus.collectAsState()
    val summary by BackupEngine.summary.collectAsState()
    val items by BackupEngine.items.collectAsState()
    val folders by vm.folders.collectAsState()
    var cfg by remember { mutableStateOf(BackupSettings.load(ctx)) }
    val ready = tgStatus is StorageStatus.Verified

    fun apply(new: BackupConfig) { cfg = new; BackupEngine.updateConfig(ctx, new) }

    fun enable() {
        val defaults = if (cfg.folders.isNotEmpty()) cfg.folders
        else setOfNotNull(folders.firstOrNull { it.name.equals("Camera", true) }?.name ?: folders.firstOrNull()?.name)
        apply(cfg.copy(enabled = true, paused = false, folders = defaults))
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> enable() }

    var checking by remember { mutableStateOf(false) }
    var confirmRetryHeld by remember { mutableStateOf(false) }
    val doneN = items.values.count { it.state == BackupState.VERIFIED }
    val failedN = (items.values.count { it.state == BackupState.FAILED } - summary.needsConfirmation).coerceAtLeast(0)
    val waitingN = items.values.count { it.state == BackupState.QUEUED || it.state == BackupState.UPLOADING }

    if (confirmRetryHeld) AlertDialog(
        onDismissRequest = { confirmRetryHeld = false },
        title = { Text(stringResource(R.string.rf_retry_held)) },
        text = { Text(stringResource(R.string.rf_retry_held_warning)) },
        confirmButton = { TextButton(onClick = { confirmRetryHeld = false; scope.launch { BackupEngine.retryHeld(ctx) } }) { Text(stringResource(R.string.bk_retry)) } },
        dismissButton = { TextButton(onClick = { confirmRetryHeld = false }) { Text(stringResource(R.string.cancel)) } },
    )
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.bk_title)) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!ready) {
                Column(Modifier.fillMaxWidth().appear().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.bk_need_tg_card), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Button(onClick = onOpenTelegram) { Text(stringResource(R.string.bk_open_tg)) }
                }
            }

            Column(Modifier.fillMaxWidth().appear(60).glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val phaseText = when (summary.phase) {
                    BackupPhase.RUNNING -> stringResource(R.string.bk_running)
                    BackupPhase.PAUSED -> stringResource(R.string.bk_waiting)
                    BackupPhase.IDLE -> stringResource(R.string.backup_all_done)
                    BackupPhase.NOT_SET_UP -> stringResource(R.string.bk_state_off)
                }
                Text(if (summary.attention > 0 && summary.phase != BackupPhase.RUNNING) stringResource(R.string.rf_upload_failed) else phaseText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.bk_counts, doneN, waitingN, failedN), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (summary.phase == BackupPhase.RUNNING) {
                    LinearProgressIndicator(
                        progress = { if (summary.total > 0) summary.done.toFloat() / summary.total else 0f },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                    if (summary.currentName != null) {
                        Text("${summary.currentName} · ${(summary.currentProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                summary.stage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (summary.needsConfirmation > 0) {
                    Text(stringResource(R.string.rf_upload_pending_count, summary.needsConfirmation), color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.rf_upload_uncertain), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(enabled = ready && !checking && summary.phase != BackupPhase.RUNNING, onClick = {
                        checking = true
                        scope.launch {
                            try {
                                val count = BackupEngine.checkUnconfirmed(ctx)
                                android.widget.Toast.makeText(ctx, ctx.getString(R.string.rf_upload_check_result, count), android.widget.Toast.LENGTH_LONG).show()
                            } finally { checking = false }
                        }
                    }) { Text(stringResource(R.string.rf_upload_check)) }
                    TextButton(enabled = ready && !checking && summary.phase != BackupPhase.RUNNING, onClick = { confirmRetryHeld = true }) { Text(stringResource(R.string.rf_retry_held)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            if (cfg.paused) { BackupEngine.resume(ctx); cfg = cfg.copy(paused = false) }
                            BackupEngine.scanAndEnqueue(ctx); BackupScheduler.runNow(ctx)
                        }
                    }, enabled = ready) { Text(stringResource(R.string.bk_now)) }
                    if (failedN > 0) OutlinedButton(onClick = { scope.launch { BackupEngine.retryFailed(ctx) } }, enabled = ready) {
                        Text(stringResource(R.string.bk_retry))
                    }
                }
                if (cfg.paused || summary.phase == BackupPhase.RUNNING || waitingN > 0) {
                    FilledTonalButton(
                        onClick = {
                            if (cfg.paused) { BackupEngine.resume(ctx); cfg = cfg.copy(paused = false) }
                            else { BackupEngine.pause(ctx); cfg = cfg.copy(paused = true) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(if (cfg.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (cfg.paused) R.string.bk_resume else R.string.bk_pause))
                    }
                }
            }

            Column(Modifier.fillMaxWidth().appear(120).glass()) {
                SwitchRow(stringResource(R.string.bk_auto), stringResource(R.string.bk_auto_sub), cfg.enabled, enabled = ready || cfg.enabled) { on ->
                    if (!on) apply(cfg.copy(enabled = false))
                    else if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else enable()
                }
                SwitchRow(stringResource(R.string.bk_wifi), null, cfg.wifiOnly) { apply(cfg.copy(wifiOnly = it)) }
                SwitchRow(stringResource(R.string.bk_charging), null, cfg.chargingOnly) { apply(cfg.copy(chargingOnly = it)) }
                SwitchRow(stringResource(R.string.bk_photos), null, cfg.photos) { apply(cfg.copy(photos = it)) }
                SwitchRow(stringResource(R.string.bk_videos), null, cfg.videos) { apply(cfg.copy(videos = it)) }
            }

            if (folders.isNotEmpty()) {
                Text(stringResource(R.string.bk_folders), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
                Column(Modifier.fillMaxWidth().glass()) {
                    folders.forEach { f ->
                        SwitchRow(f.name, stringResource(R.string.items_count, f.count), f.name in cfg.folders) { on ->
                            apply(cfg.copy(folders = if (on) cfg.folders + f.name else cfg.folders - f.name))
                        }
                    }
                }
            }

            Text(stringResource(R.string.bk_rule_note), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp))
            Text(stringResource(R.string.bk_battery_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
    )
}
