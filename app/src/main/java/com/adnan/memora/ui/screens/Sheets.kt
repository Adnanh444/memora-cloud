package com.adnan.memora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adnan.memora.R
import kotlinx.coroutines.launch

/** Modern bottom sheet. Tap outside or swipe down to dismiss. [content] gets a `close { action }` helper. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoraSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(close: (() -> Unit) -> Unit) -> Unit,
) {
    val state = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val close: (() -> Unit) -> Unit = { action ->
        scope.launch { state.hide(); action(); onDismiss() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) { content(this, close) }
    }
}

@Composable
fun SheetOption(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    ListItem(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f },
        colors = ListItemDefaults.colors(containerColor = if (destructive) cs.errorContainer.copy(alpha = 0.35f) else cs.surfaceContainerHigh),
        leadingContent = {
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(if (destructive) cs.errorContainer else cs.primaryContainer),
                Alignment.Center,
            ) {
                Icon(icon, null, tint = if (destructive) cs.onErrorContainer else cs.onPrimaryContainer)
            }
        },
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = subtitle?.let { { Text(it) } },
    )
}

/** Delete: choose where to delete from. Cloud choices are active when the item is backed up. */
@Composable
fun DeleteSheet(
    count: Int,
    onDeletePhone: () -> Unit,
    onDismiss: () -> Unit,
    hasCloud: Boolean = false,
    onDeleteCloud: () -> Unit = {},
    onDeleteBoth: () -> Unit = {},
) {
    val trash = android.os.Build.VERSION.SDK_INT >= 30
    MemoraSheet(onDismiss) { close ->
        Text(stringResource(if (trash) R.string.rf_move_title else R.string.delete_title, count), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        Text(stringResource(R.string.delete_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp))
        SheetOption(
            Icons.Filled.PhoneAndroid, stringResource(if (trash) R.string.rf_move_phone else R.string.delete_from_phone),
            stringResource(if (trash) R.string.delete_phone_sub_trash else R.string.delete_phone_sub_perm),
            destructive = true,
        ) { close(onDeletePhone) }
        SheetOption(
            Icons.Filled.Cloud, stringResource(R.string.rf_move_cloud),
            stringResource(if (hasCloud) R.string.delete_cloud_sub else R.string.not_backed_up_item),
            enabled = hasCloud,
        ) { close(onDeleteCloud) }
        SheetOption(
            Icons.Filled.DeleteSweep, stringResource(if (trash) R.string.rf_move_both else R.string.delete_from_both),
            stringResource(if (hasCloud) R.string.delete_both_sub else R.string.not_backed_up_item),
            enabled = hasCloud, destructive = true,
        ) { close(onDeleteBoth) }
        TextButton(onClick = { close {} }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.cancel))
        }
    }
}

/** Delete from the Cloud tab: items go to the cloud bin and are removed from storage after 30 days. */
@Composable
fun CloudDeleteSheet(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    MemoraSheet(onDismiss) { close ->
        Text(stringResource(R.string.rf_move_title, count), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        SheetOption(
            Icons.Filled.Cloud, stringResource(R.string.rf_move_cloud),
            stringResource(R.string.delete_cloud_sub), destructive = true,
        ) { close(onConfirm) }
        TextButton(onClick = { close {} }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
fun ChoiceSheet(
    title: String,
    options: List<Pair<String, Boolean>>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    MemoraSheet(onDismiss) { close ->
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        options.forEachIndexed { i, (label, selected) ->
            ListItem(
                modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(18.dp))
                    .clickable { close { onPick(i) } },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(label) },
                trailingContent = {
                    if (selected) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                },
            )
        }
    }
}
