package com.adnan.memora.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.adnan.memora.R
import com.adnan.memora.backup.BackupEngine
import com.adnan.memora.telegram.StorageStatus
import com.adnan.memora.telegram.TelegramClient
import com.adnan.memora.data.BackupState
import com.adnan.memora.gallery.BackupPhase
import com.adnan.memora.gallery.GalleryViewModel
import com.adnan.memora.gallery.ItemBackup
import com.adnan.memora.gallery.LocalMedia
import com.adnan.memora.ui.theme.LocalCleanStyle
import com.adnan.memora.ui.theme.brandBrush
import com.adnan.memora.ui.theme.glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private sealed class Entry {
    abstract val key: String
    data class Header(val label: String) : Entry() { override val key: String get() = "h:$label" }
    data class Item(val media: LocalMedia) : Entry() { override val key: String get() = media.key }
}

private class Built(val entries: List<Entry>, val months: List<String>)

private fun dayStart(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun buildEntries(list: List<LocalMedia>, todayLabel: String, yesterdayLabel: String): Built {
    val dayFmt = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
    val monthFmt = SimpleDateFormat("MMM yyyy", Locale.getDefault())
    val today = dayStart(System.currentTimeMillis())
    val yesterday = dayStart(today - 1)
    val entries = ArrayList<Entry>(list.size + 64)
    val months = ArrayList<String>(list.size + 64)
    var lastDay = -1L
    for (m in list) {
        val d = dayStart(m.dateTaken)
        val month = monthFmt.format(m.dateTaken)
        if (d != lastDay) {
            lastDay = d
            entries.add(Entry.Header(when (d) { today -> todayLabel; yesterday -> yesterdayLabel; else -> dayFmt.format(d) }))
            months.add(month)
        }
        entries.add(Entry.Item(m))
        months.add(month)
    }
    return Built(entries, months)
}

/** Date-grouped photo grid with fast-scroll handle, badges and selection. */
@Composable
fun MediaGrid(
    list: List<LocalMedia>, vm: GalleryViewModel, onOpen: (Int) -> Unit, modifier: Modifier = Modifier,
    showBadges: Boolean = true, gridKey: String = "gallery",
) {
    val selection by vm.selection.collectAsState()
    val selecting by vm.selecting.collectAsState()
    val summary by vm.backupSummary.collectAsState()
    val itemBackup by vm.itemBackup.collectAsState()
    val todayL = stringResource(R.string.today)
    val yesterdayL = stringResource(R.string.yesterday)
    val built = remember(list, todayL, yesterdayL) { buildEntries(list, todayL, yesterdayL) }
    val state = androidx.compose.runtime.saveable.rememberSaveable(gridKey, saver = LazyGridState.Saver) { LazyGridState() }
    val badgesOn = showBadges && summary.phase != BackupPhase.NOT_SET_UP
    val gap = 4.dp

    // ---- long-press + drag selection -------------------------------------------------
    // Press one cell and keep dragging: every cell the finger crosses flips in the same
    // direction as the first one. Fast drags are interpolated so nothing is skipped, the
    // list auto-scrolls when the finger reaches an edge, and each flip gives a light tick.
    var dragSelect by remember { mutableStateOf(false) }
    var lastDraggedKey by remember { mutableStateOf<String?>(null) }
    var lastDragPoint by remember { mutableStateOf<Offset?>(null) }
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val edgePx = with(LocalDensity.current) { 90.dp.toPx() }

    fun entryUnder(root: Offset): Entry.Item? {
        val coords = gridCoords?.takeIf { it.isAttached } ?: return null
        val info = state.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            val topLeft = coords.localToRoot(Offset(item.offset.x.toFloat(), item.offset.y.toFloat()))
            root.x >= topLeft.x && root.x < topLeft.x + item.size.width &&
                root.y >= topLeft.y && root.y < topLeft.y + item.size.height
        } ?: return null
        return built.entries.getOrNull(info.index) as? Entry.Item
    }

    /** Flips one cell, but only if it is still on the wrong side of the drag direction. */
    fun flipAt(root: Offset) {
        val entry = entryUnder(root) ?: return
        val key = entry.media.key
        if (key == lastDraggedKey) return
        lastDraggedKey = key
        val isSelected = key in vm.selection.value
        if (dragSelect == isSelected) return
        vm.toggle(key)
        try { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Throwable) {}
    }

    fun cellPitch(): Float {
        val first = state.layoutInfo.visibleItemsInfo.firstOrNull { !built.entries.getOrNull(it.index).isHeader() }
            ?: return 1f
        return minOf(first.size.width, first.size.height).toFloat().coerceAtLeast(1f)
    }

    /** Walks the segment between two finger samples so no cell is jumped over. */
    fun dragAlong(root: Offset) {
        val previous = lastDragPoint
        lastDragPoint = root
        if (previous == null) { flipAt(root); return }
        val delta = root - previous
        val steps = (delta.getDistance() / (cellPitch() * 0.4f)).toInt().coerceIn(1, 32)
        for (i in 1..steps) flipAt(previous + delta * (i / steps.toFloat()))
    }

    /** Keeps scrolling while the finger rests near the top or bottom edge. */
    fun autoScroll(root: Offset) {
        val coords = gridCoords?.takeIf { it.isAttached }
        if (coords == null) { scrollJob?.cancel(); scrollJob = null; return }
        val top = coords.localToRoot(Offset.Zero).y
        val bottom = top + coords.size.height
        val direction = when {
            root.y < top + edgePx -> -1f
            root.y > bottom - edgePx -> 1f
            else -> 0f
        }
        if (direction == 0f) { scrollJob?.cancel(); scrollJob = null; return }
        if (scrollJob?.isActive == true) return
        scrollJob = scope.launch {
            while (isActive) {
                state.scrollBy(direction * 14f)
                delay(16)
                flipAt(root)
            }
        }
    }

    fun endDrag() {
        scrollJob?.cancel(); scrollJob = null
        lastDragPoint = null
        lastDraggedKey = null
    }

    PinchGrid(gridKey, modifier) { columns, gesture ->
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = state,
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize().then(gesture).onGloballyPositioned { gridCoords = it },
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            // fillMaxSize stays: the oversized content keeps the pinch gesture underneath
        ) {
            items(
                count = built.entries.size,
                key = { built.entries[it].key },
                span = { if (built.entries[it] is Entry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
            ) { i ->
                when (val e = built.entries[i]) {
                    is Entry.Header -> Text(
                        e.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 6.dp),
                    )
                    is Entry.Item -> MediaCell(
                        m = e.media,
                        selected = e.media.key in selection,
                        selecting = selecting,
                        badge = if (badgesOn) (itemBackup[e.media.key] ?: ItemBackup(BackupState.NOT_BACKED_UP)) else null,
                        onClick = {
                            if (selecting) vm.toggle(e.media.key)
                            else onOpen(list.indexOfFirst { it.key == e.media.key })
                        },
                        onLongClick = { vm.toggle(e.media.key) },
                        onDragStart = { root ->
                            val key = e.media.key
                            val wasSelected = key in vm.selection.value
                            dragSelect = !wasSelected
                            lastDraggedKey = key
                            lastDragPoint = root
                            vm.toggle(key)
                            try { haptics.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Throwable) {}
                            autoScroll(root)
                        },
                        onDrag = { root -> dragAlong(root); autoScroll(root) },
                        onDragEnd = { endDrag() },
                    )
                }
            }
        }
        FastScroller(state, built.months)
    }
    }
}

@Composable
private fun BoxScope.FastScroller(state: LazyGridState, months: List<String>) {
    val total = months.size
    if (total < 60) return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }
    val scrolling = state.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) visible = true else { delay(1500); visible = false }
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(220), label = "fsAlpha")
    val thumbH = 48.dp
    val thumbPx = with(density) { thumbH.toPx() }
    val travel = (heightPx - thumbPx).coerceAtLeast(1f)
    val fraction = if (dragging) dragFraction
    else (state.firstVisibleItemIndex.toFloat() / (total - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
    val idx = (fraction * (total - 1)).roundToInt().coerceIn(0, total - 1)

    Box(Modifier.matchParentSize().onSizeChanged { heightPx = it.height }) {
        if (alpha > 0.02f) {
            Row(
                Modifier.align(Alignment.TopEnd)
                    .offset { IntOffset(0, (fraction * travel).roundToInt()) }
                    .graphicsLayer { this.alpha = alpha },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dragging) {
                    Box(Modifier.glass(RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(months[idx], style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    Modifier.size(width = 34.dp, height = thumbH)
                        .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                        .background(brandBrush())
                        .pointerInput(total, heightPx) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragging = true
                                    dragFraction = state.firstVisibleItemIndex.toFloat() / (total - 1).coerceAtLeast(1)
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                                onVerticalDrag = { change, dy ->
                                    change.consume()
                                    dragFraction = (dragFraction + dy / travel).coerceIn(0f, 1f)
                                    val target = (dragFraction * (total - 1)).roundToInt()
                                    scope.launch { state.scrollToItem(target) }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.UnfoldMore, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun MediaCell(
    m: LocalMedia, selected: Boolean, selecting: Boolean, badge: ItemBackup?,
    onClick: () -> Unit, onLongClick: () -> Unit,
    onDragStart: ((Offset) -> Unit)? = null,
    onDrag: ((Offset) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(380), label = "cellAlpha")
    val enter by animateFloatAsState(if (shown) 1f else 0.86f, tween(380, easing = FastOutSlowInEasing), label = "cellEnter")
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        if (pressed) 0.93f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "cellPress",
    )
    val pad by animateDpAsState(if (selected) 10.dp else 0.dp, label = "pad")
    val tick by animateFloatAsState(
        if (selected) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "tick",
    )
    val onClickL by rememberUpdatedState(onClick)
    val onLongL by rememberUpdatedState(onLongClick)
    val onDragStartL by rememberUpdatedState(onDragStart)
    val onDragL by rememberUpdatedState(onDrag)
    val onDragEndL by rememberUpdatedState(onDragEnd)
    val selectedNow by rememberUpdatedState(selected)
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val clean = LocalCleanStyle.current
    val shape = RoundedCornerShape(if (selected) 14.dp else 6.dp)
    val brand = brandBrush()

    Box(
        Modifier.aspectRatio(1f)
            .graphicsLayer { this.alpha = alpha; val sc = enter * press; scaleX = sc; scaleY = sc }
            .onGloballyPositioned { coords = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onClickL() },
                )
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        pressed = false
                        onDragStartL?.invoke(coords?.localToRoot(position) ?: position)
                    },
                    onDrag = { change, _ ->
                        onDragL?.invoke(coords?.localToRoot(change.position) ?: change.position)
                    },
                    onDragEnd = { onDragEndL?.invoke() },
                    onDragCancel = { onDragEndL?.invoke() },
                )
            }
    ) {
        AsyncImage(
            model = m.uri, contentDescription = m.name, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().padding(pad).clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .then(if (selected) Modifier.border(2.dp, brand, shape) else Modifier),
        )
        if (m.isVideo) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(7.dp).clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(14.dp), tint = Color.White)
                Text(formatDuration(m.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (badge != null && !selecting) {
            StatusBadge(badge, Modifier.align(Alignment.TopEnd).padding(7.dp))
        }
        if (selecting) {
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(9.dp).size(22.dp)
                    .graphicsLayer { val k = 0.8f + 0.4f * tick; scaleX = k; scaleY = k },
            )
        }
    }
}

private fun Any?.isHeader(): Boolean = this is Entry.Header

/** Tiny cloud status on a thumbnail: uploading (with progress), backed up, not backed up, failed. */
@Composable
private fun StatusBadge(b: ItemBackup, modifier: Modifier) {
    Box(modifier.size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.7f)), Alignment.Center) {
        when (b.state) {
            BackupState.UPLOADING -> CircularProgressIndicator(
                progress = { b.progress }, modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                color = Color.White, trackColor = Color.White.copy(alpha = 0.3f),
            )
            BackupState.QUEUED -> Icon(Icons.Filled.Schedule, null, Modifier.size(12.dp), tint = Color.White)
            BackupState.VERIFIED, BackupState.CLOUD_ONLY, BackupState.DOWNLOADED ->
                Icon(Icons.Filled.CloudDone, null, Modifier.size(12.dp), tint = Color.White)
            BackupState.FAILED -> Icon(Icons.Filled.Error, null, Modifier.size(12.dp), tint = Color(0xFFFF8A80))
            else -> Icon(Icons.Filled.CloudOff, null, Modifier.size(12.dp), tint = Color.White)
        }
    }
}

@Composable
fun SkeletonGrid() {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "x")
    val base = MaterialTheme.colorScheme.surfaceContainer
    val hi = MaterialTheme.colorScheme.surfaceContainerHighest
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp), userScrollEnabled = false,
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(30) {
            Box(
                Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(
                    Brush.linearGradient(
                        listOf(base, hi, base),
                        start = Offset(x * 700f - 250f, 0f), end = Offset(x * 700f + 250f, 350f),
                    )
                )
            )
        }
    }
}

/** Header shown while selecting: count, select all, share, delete (with the where-to-delete sheet). */
@Composable
fun SelectionHeader(vm: GalleryViewModel, pool: List<LocalMedia>) {
    val ctx = LocalContext.current
    val selection by vm.selection.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val deleteAction = rememberDeleteAction(onDone = { vm.clearSelection(); vm.refresh() })
    BackHandler { vm.clearSelection() }
    val itemBackup by vm.itemBackup.collectAsState()
    val hasCloud = selection.any { itemBackup[it]?.state == BackupState.VERIFIED }
    if (confirm) DeleteSheet(
        count = selection.size,
        hasCloud = hasCloud,
        onDeletePhone = { deleteAction(vm.selectedMedia()) },
        onDeleteCloud = {
            val keys = vm.selectedMedia().map { it.key }
            scope.launch { BackupEngine.trashCloudForLocal(ctx, keys) }
            vm.clearSelection()
        },
        onDeleteBoth = {
            val picked = vm.selectedMedia()
            scope.launch {
                BackupEngine.trashCloudForLocal(ctx, picked.map { it.key })
                deleteAction(picked)
            }
        },
        onDismiss = { confirm = false },
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.clearSelection() }) {
            Icon(Icons.Filled.Close, stringResource(R.string.clear_selection))
        }
        SelectionCount(selection.size, Modifier.weight(1f))
        IconButton(onClick = { if (pool.isNotEmpty() && selection.containsAll(pool.map { it.key })) vm.selection.value = emptySet() else vm.selectAll(pool) }) { SelectionGlyph(pool.isNotEmpty() && selection.containsAll(pool.map { it.key })) }
        IconButton(onClick = {
            if (TelegramClient.get(ctx).storageStatus.value !is StorageStatus.Verified) {
                Toast.makeText(ctx, R.string.bk_need_tg, Toast.LENGTH_LONG).show()
            } else {
                val picked = vm.selectedMedia()
                scope.launch {
                    val (added, skipped) = BackupEngine.enqueueManual(ctx, picked)
                    val msg = if (skipped > 0) ctx.getString(R.string.bk_queued_skipped, added, skipped)
                    else ctx.getString(R.string.bk_queued, added)
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                }
                vm.clearSelection()
            }
        }, enabled = selection.isNotEmpty()) {
            Icon(Icons.Filled.CloudUpload, stringResource(R.string.bk_now))
        }
        IconButton(onClick = { shareMedia(ctx, vm.selectedMedia()) }, enabled = selection.isNotEmpty()) {
            Icon(Icons.Filled.Share, stringResource(R.string.share))
        }
        IconButton(onClick = { confirm = true }, enabled = selection.isNotEmpty()) {
            ActionGlyph(Icons.Filled.Delete, stringResource(R.string.delete))
        }
        OrgMenu(vm, enabled = selection.isNotEmpty(), onDone = { vm.clearSelection() }, pickItems = { vm.selectedAny() })
    }
}
