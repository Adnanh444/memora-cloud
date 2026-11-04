package com.adnan.memora.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adnan.memora.R
import com.adnan.memora.backup.RecState
import com.adnan.memora.backup.RecoveryEngine
import com.adnan.memora.ui.theme.appear
import com.adnan.memora.ui.theme.glass

/** Restore an existing backup after reinstalling the app or changing phones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryScreen(onBack: () -> Unit, onOpenCloud: () -> Unit) {
    val ctx = LocalContext.current
    val state by RecoveryEngine.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.rc_title)) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (val s = state) {
                is RecState.Idle -> {
                    Card(stringResource(R.string.rc_intro)) {
                        Button(onClick = { RecoveryEngine.findBackups(ctx) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.rc_find))
                        }
                    }
                    Text(stringResource(R.string.rc_limits), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                }
                is RecState.Searching -> Busy(stringResource(R.string.rc_searching), null)
                is RecState.NoneFound -> {
                    Card(stringResource(R.string.rc_none)) {
                        Button(onClick = { RecoveryEngine.findBackups(ctx) }) { Text(stringResource(R.string.rc_find)) }
                    }
                    Text(stringResource(R.string.rc_limits), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                }
                is RecState.Found -> {
                    Text(stringResource(R.string.rc_choose), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    s.list.forEach { c ->
                        Card(stringResource(R.string.rc_candidate, c.storageId.take(8))) {
                            Button(onClick = { RecoveryEngine.restore(ctx, c) }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.rc_restore))
                            }
                        }
                    }
                }
                is RecState.Restoring -> Busy(stringResource(R.string.rc_scanning, s.found), null)
                is RecState.Matching -> Busy(
                    stringResource(R.string.rc_matching, s.done, s.total),
                    if (s.total > 0) s.done.toFloat() / s.total else null,
                )
                is RecState.Done -> {
                    Column(Modifier.fillMaxWidth().appear().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.rc_done_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        }
                        Text(stringResource(R.string.rc_done_body, s.recovered, s.matched))
                        Button(onClick = onOpenCloud, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.rc_open_cloud)) }
                    }
                }
                is RecState.Failed -> {
                    Column(Modifier.fillMaxWidth().appear().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.rc_failed, s.message), color = MaterialTheme.colorScheme.error)
                        }
                        OutlinedButton(onClick = { RecoveryEngine.findBackups(ctx) }) { Text(stringResource(R.string.rc_again)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(text: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().appear().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        content()
    }
}

@Composable
private fun Busy(text: String, progress: Float?) {
    Column(Modifier.fillMaxWidth().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp))
        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
    }
}
