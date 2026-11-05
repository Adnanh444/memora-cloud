package com.adnan.memora.ui.screens

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.adnan.memora.R

@Composable
internal fun rememberCountersVisible(page: String): MutableState<Boolean> {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("memora_counter_display", Context.MODE_PRIVATE) }
    val visible = remember(page) { mutableStateOf(prefs.getBoolean(page, true)) }
    LaunchedEffect(visible.value, page) { prefs.edit().putBoolean(page, visible.value).apply() }
    return visible
}

@Composable
internal fun CounterToggle(visible: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(if (visible) Icons.Filled.DonutLarge else Icons.Filled.VisibilityOff,
            stringResource(if (visible) R.string.mx_hide_counters else R.string.mx_show_counters),
            tint = if (visible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
