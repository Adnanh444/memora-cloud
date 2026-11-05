package com.adnan.memora.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adnan.memora.R
import com.adnan.memora.gallery.CollapseRules
import com.adnan.memora.gallery.CoverageCount
import java.text.NumberFormat

internal class CollapsingHeader(private val threshold: Float) {
    var compact by mutableStateOf(false)
    private var distance = 0f
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y == 0f) return Offset.Zero
            if (available.y * distance < 0) distance = 0f
            distance += available.y
            val next = CollapseRules.next(compact, distance, threshold)
            if (next != compact) { compact = next; distance = 0f }
            return Offset.Zero // Never steal scrolling, pull-to-refresh or pinch.
        }
    }
    fun toggle() { compact = !compact; distance = 0f }
}
@Composable internal fun rememberCollapsingHeader(): CollapsingHeader {
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    return remember(threshold) { CollapsingHeader(threshold) }
}

/** null verified counts mean cloud library totals, NOT a made-up 100% progress bar. */
@Composable
internal fun CoveragePanel(photos: Int, videos: Int, photoVerified: Int?, videoVerified: Int?, compact: Boolean, onToggle: () -> Unit, phoneLibrary: Boolean = photoVerified != null) {
    val cs = MaterialTheme.colorScheme
    val phone = phoneLibrary
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        .background(cs.surfaceContainer.copy(alpha = .8f), RoundedCornerShape(20.dp))
        .border(1.dp, cs.outlineVariant, RoundedCornerShape(20.dp)).animateContentSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (phone) R.string.lp_phone_coverage else R.string.lp_cloud_library),
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(NumberFormat.getIntegerInstance().format(photos + videos), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            IconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
                Icon(if (compact) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                    stringResource(if (compact) R.string.lp_expand else R.string.lp_collapse))
            }
        }
        AnimatedContent(targetState = compact, label = "coverage-layout") { small ->
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CoverageCounter(stringResource(R.string.cat_photos), photos, photoVerified, cs.primary, small, Modifier.weight(1f))
                CoverageCounter(stringResource(R.string.cat_videos), videos, videoVerified, cs.tertiary, small, Modifier.weight(1f))
            }
        }
        if (phone && !compact) Text(stringResource(if (photoVerified != null) R.string.lp_coverage_note else R.string.lp_verify_to_count), style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp))
    }
}

@Composable
private fun CoverageCounter(title: String, total: Int, verified: Int?, tint: Color, compact: Boolean, modifier: Modifier) {
    val number = NumberFormat.getIntegerInstance()
    val ratio = CoverageCount(total, verified ?: 0).fraction
    val animated by animateFloatAsState(ratio, tween(650), label = "verified-coverage")
    val description = if (verified != null) stringResource(R.string.lp_verified_ratio, title, verified, total)
        else "$title: ${number.format(total)}"
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
        if (compact) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = tint)
                Text(if (verified == null) number.format(total) else "${number.format(verified)}/${number.format(total)}", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(6.dp))
            if (verified != null) LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth().height(4.dp), color = tint,
                trackColor = tint.copy(alpha = .12f))
            else Box(Modifier.fillMaxWidth().height(3.dp).background(tint.copy(alpha = .65f), RoundedCornerShape(4.dp)))
        } else {
            Box(Modifier.size(106.dp), contentAlignment = Alignment.Center) {
                if (verified != null) CircularProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxSize(), color = tint,
                    trackColor = tint.copy(alpha = .13f), strokeWidth = 6.dp)
                else Box(Modifier.fillMaxSize().border(5.dp, tint.copy(alpha = .7f), CircleShape))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(number.format(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(title, style = MaterialTheme.typography.labelMedium, color = tint)
                }
            }
            if (verified != null) Text(stringResource(R.string.lp_backed_up, verified), style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
