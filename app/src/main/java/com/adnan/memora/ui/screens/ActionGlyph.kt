package com.adnan.memora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LibraryAddCheck
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.adnan.memora.R

/** Visual 34dp glyph; its owning IconButton retains Android's minimum 48dp hit target. */
@Composable
internal fun ActionGlyph(icon: ImageVector, description: String?) {
    val danger = icon == Icons.Filled.Delete || icon == Icons.Filled.DeleteForever
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val symbol = when (icon) {
        Icons.Filled.Delete -> Icons.Outlined.DeleteOutline
        Icons.Filled.SelectAll -> Icons.Outlined.LibraryAddCheck
        else -> icon
    }
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.size(34.dp).background(color.copy(alpha = .10f), shape).border(1.dp, color.copy(alpha = .30f), shape), Alignment.Center) {
        Icon(symbol, description, tint = color, modifier = Modifier.size(21.dp))
    }
}

@Composable
internal fun SelectionGlyph(allSelected: Boolean) {
    ActionGlyph(if (allSelected) Icons.Filled.Deselect else Icons.Filled.SelectAll,
        stringResource(if (allSelected) R.string.ui_deselect_all else R.string.select_all))
}
