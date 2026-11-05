package com.adnan.memora.ui.screens

import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext

internal object GridDensity {
    const val DEFAULT = 2
    fun saved(value: Int): Int = value.coerceIn(1, 8)
    fun effective(value: Int, widthDp: Float): Int = saved(value).coerceAtMost((widthDp / 64f).toInt().coerceIn(1, 8))
    fun step(columns: Int, zoom: Float): Int = when {
        zoom < 0.78f -> saved(columns + 1)
        zoom > 1.28f -> saved(columns - 1)
        else -> saved(columns)
    }
}

/** SharedPreferences is deliberate: the size survives process death, not just screen navigation. */
internal class GridPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("memora_grid_density", Context.MODE_PRIVATE)
    fun read(key: String) = GridDensity.saved(prefs.getInt(key, GridDensity.DEFAULT))
    fun write(key: String, columns: Int) { prefs.edit().putInt(key, GridDensity.saved(columns)).apply() }
}

/** Consume only multi-touch. Single-finger scrolling and the children's tap/long-press handlers stay intact. */
@Composable
fun PinchGrid(key: String, modifier: Modifier = Modifier, content: @Composable (Int, Modifier) -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { GridPreferences(context) }
    var preferred by remember(key) { mutableIntStateOf(prefs.read(key)) }
    BoxWithConstraints(modifier) {
        val columns = GridDensity.effective(preferred, maxWidth.value)
        val current by rememberUpdatedState(columns)
        val gesture = Modifier.pointerInput(key) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var accumulated = 1f
                var changed = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        accumulated *= event.calculateZoom()
                        val next = GridDensity.step(current, accumulated)
                        if (next != current) {
                            preferred = next
                            changed = true
                            accumulated = 1f
                        }
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                // Rotation/layout clamps are not written back unless the user actually pinches.
                if (changed) prefs.write(key, preferred)
            }
        }
        content(columns, gesture)
    }
}
