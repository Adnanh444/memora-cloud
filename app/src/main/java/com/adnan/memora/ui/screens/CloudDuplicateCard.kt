package com.adnan.memora.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adnan.memora.R
import com.adnan.memora.backup.CloudDuplicateScanner
import com.adnan.memora.ui.theme.glass

/**
 * One button that answers "is the same photo in my cloud twice?".
 * The comparison happens inside the app (channel captions carry the hash), so nothing has to be matched by eye.
 */
@Composable
fun CloudDuplicateCard() {
    val ctx = LocalContext.current
    val st by CloudDuplicateScanner.state.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    val error = MaterialTheme.colorScheme.error

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).glass().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.cdup_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        when (val s = st) {
            is CloudDuplicateScanner.State.Idle -> {
                Text(stringResource(R.string.cdup_intro), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { CloudDuplicateScanner.scan(ctx) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cdup_scan))
                }
            }
            is CloudDuplicateScanner.State.Scanning -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.cdup_scanning, s.found), style = MaterialTheme.typography.bodyMedium)
            }
            is CloudDuplicateScanner.State.Done -> if (s.groups.isEmpty()) {
                Text(stringResource(R.string.cdup_none), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { CloudDuplicateScanner.scan(ctx) }) { Text(stringResource(R.string.cdup_scan)) }
            } else {
                Text(
                    stringResource(R.string.cdup_result, s.groups.size, s.extras, formatSize(s.bytes)),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                )
                Text(stringResource(R.string.cdup_keep_oldest), style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { confirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = error),
                ) { Text(stringResource(R.string.cdup_remove)) }
                OutlinedButton(onClick = { CloudDuplicateScanner.scan(ctx) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cdup_scan_again))
                }
            }
            is CloudDuplicateScanner.State.Working -> {
                Text(stringResource(R.string.cdup_working, s.done, s.total), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    progress = { if (s.total > 0) s.done.toFloat() / s.total else 0f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
            }
            is CloudDuplicateScanner.State.Removed -> Text(
                stringResource(R.string.cdup_removed, s.removed, s.binned),
                style = MaterialTheme.typography.bodyMedium,
            )
            is CloudDuplicateScanner.State.Failed -> Text(s.message, style = MaterialTheme.typography.bodyMedium, color = error)
        }
    }

    if (confirm) {
        val done = st as? CloudDuplicateScanner.State.Done
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.cdup_confirm_title)) },
            text = { Text(stringResource(R.string.cdup_confirm_body, done?.extras ?: 0)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; CloudDuplicateScanner.removeExtras(ctx) }) {
                    Text(stringResource(R.string.cdup_remove), color = error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
