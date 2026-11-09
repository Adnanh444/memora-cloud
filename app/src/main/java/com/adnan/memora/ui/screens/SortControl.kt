package com.adnan.memora.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.adnan.memora.R
import com.adnan.memora.gallery.LocalMedia

/** What the grid is ordered by. One tap on the current choice flips the direction. */
enum class SortField { TAKEN, ADDED, NAME, SIZE }

data class SortMode(val field: SortField = SortField.TAKEN, val asc: Boolean = false) {
    val labelRes: Int
        get() = when (this.field) {
            SortField.TAKEN -> R.string.sort_taken
            SortField.ADDED -> R.string.sort_added
            SortField.NAME -> R.string.sort_name
            SortField.SIZE -> R.string.sort_size
        }
}

/** Default order for a fresh screen: newest first. */
val DefaultSort = SortMode(SortField.TAKEN, asc = false)

fun List<LocalMedia>.sortedFor(mode: SortMode): List<LocalMedia> = when (mode.field) {
    SortField.TAKEN -> if (mode.asc) sortedBy { it.dateTaken } else sortedByDescending { it.dateTaken }
    SortField.ADDED -> if (mode.asc) sortedBy { it.dateModified } else sortedByDescending { it.dateModified }
    SortField.NAME -> if (mode.asc) sortedBy { it.name.lowercase() } else sortedByDescending { it.name.lowercase() }
    SortField.SIZE -> if (mode.asc) sortedBy { it.size } else sortedByDescending { it.size }
}

/** Small icon button that opens the sort menu. */
@Composable
fun SortButton(mode: SortMode, onMode: (SortMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.Sort, stringResource(R.string.sort_title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortField.entries.forEach { field ->
                val current = field == mode.field
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(SortMode(field).labelRes),
                            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    trailingIcon = {
                        if (current) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    onClick = {
                        onMode(if (current) mode.copy(asc = !mode.asc) else SortMode(field))
                        open = false
                    },
                )
            }
        }
    }
}
