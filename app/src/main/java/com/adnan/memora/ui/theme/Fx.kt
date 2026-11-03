package com.adnan.memora.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Neon gradient used for titles, borders and highlights. */
@Composable
fun brandBrush(): Brush {
    val c = MaterialTheme.colorScheme
    return Brush.linearGradient(listOf(c.primary, c.secondary, c.tertiary))
}

/** Slowly drifting "aurora" glow behind the whole app. Drawn in one layer, cheap on mid-range phones. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(1f, 0f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val cs = MaterialTheme.colorScheme
    val k = if (cs.background.luminance() < 0.5f) 0.10f else 0.08f
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.radialGradient(listOf(cs.primary.copy(alpha = k), Color.Transparent),
            center = Offset(w * (0.15f + 0.6f * a), h * 0.10f), radius = w * 0.95f))
        drawRect(Brush.radialGradient(listOf(cs.secondary.copy(alpha = k), Color.Transparent),
            center = Offset(w * (0.90f - 0.6f * b), h * (0.35f + 0.2f * a)), radius = w * 0.95f))
        drawRect(Brush.radialGradient(listOf(cs.tertiary.copy(alpha = k * 0.6f), Color.Transparent),
            center = Offset(w * (0.30f + 0.4f * b), h * 0.95f), radius = w * 0.85f))
    }
}

/** Frosted-glass surface with a neon gradient hairline border. */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(26.dp)): Modifier {
    val c = MaterialTheme.colorScheme
    return this
        .clip(shape)
        .background(c.surfaceContainerHigh.copy(alpha = 0.95f))
        .border(
            1.dp,
            Brush.linearGradient(listOf(c.primary.copy(alpha = 0.75f), c.secondary.copy(alpha = 0.45f), c.tertiary.copy(alpha = 0.55f))),
            shape,
        )
}

/** Fades and slides content up when it first appears. Use [delayMs] to stagger several items. */
fun Modifier.appear(delayMs: Int = 0): Modifier = composed {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(500), label = "appearA")
    val y by animateFloatAsState(if (shown) 0f else 48f, tween(500, easing = FastOutSlowInEasing), label = "appearY")
    graphicsLayer { alpha = a; translationY = y }
}

@Composable
fun GradientTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
    )
}
