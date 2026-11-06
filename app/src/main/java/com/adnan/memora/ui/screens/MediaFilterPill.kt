package com.adnan.memora.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.adnan.memora.R
import com.adnan.memora.gallery.LocalMedia
import com.adnan.memora.ui.theme.glass

/** All / Photos / Videos, shared by the Gallery, Cloud and Search grids. */
enum class MediaFilter(val icon: ImageVector, val label: Int) {
    ALL(Icons.Outlined.Apps, R.string.flt_all),
    PHOTOS(Icons.Outlined.Photo, R.string.flt_photos),
    VIDEOS(Icons.Outlined.Videocam, R.string.flt_videos),
}

fun applyMediaFilter(list: List<LocalMedia>, filter: MediaFilter): List<LocalMedia> = when (filter) {
    MediaFilter.ALL -> list
    MediaFilter.PHOTOS -> list.filter { !it.isVideo }
    MediaFilter.VIDEOS -> list.filter { it.isVideo }
}

/**
 * Small floating pill with three icon buttons, centred at the bottom of the grid.
 * Photo tabs keep the gold accent and video tabs the blue one, like the counters.
 */
@Composable
fun MediaFilterPill(current: MediaFilter, onPick: (MediaFilter) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .glass(shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaFilter.entries.forEach { entry ->
            val selected = entry == current
            val accent = when (entry) {
                MediaFilter.ALL -> cs.tertiary
                MediaFilter.PHOTOS -> cs.primary
                MediaFilter.VIDEOS -> cs.secondary
            }
            val fill by animateColorAsState(if (selected) accent.copy(alpha = 0.20f) else Color.Transparent, label = "filterFill")
            val edge by animateColorAsState(if (selected) accent.copy(alpha = 0.60f) else Color.Transparent, label = "filterEdge")
            Box(
                Modifier
                    .clip(shape)
                    .background(fill)
                    .border(1.dp, edge, shape)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onPick(entry) })
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    entry.icon,
                    contentDescription = stringResource(entry.label),
                    tint = if (selected) accent else cs.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
