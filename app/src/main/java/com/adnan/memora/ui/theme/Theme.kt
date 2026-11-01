package com.adnan.memora.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Futuristic "neon on deep space" palette (dark) and a crisp holographic-white palette (light).
private val Dark = darkColorScheme(
    primary = Color(0xFF58D6FF), onPrimary = Color(0xFF00212B),
    primaryContainer = Color(0xFF0B3B4D), onPrimaryContainer = Color(0xFFB8EBFF),
    secondary = Color(0xFFA58BFF), onSecondary = Color(0xFF1D0B55),
    secondaryContainer = Color(0xFF35286E), onSecondaryContainer = Color(0xFFE4DCFF),
    tertiary = Color(0xFFFF7BD0), onTertiary = Color(0xFF3A0027),
    background = Color(0xFF070B16), onBackground = Color(0xFFE6ECFF),
    surface = Color(0xFF0D1324), onSurface = Color(0xFFE6ECFF),
    surfaceVariant = Color(0xFF1B2547), onSurfaceVariant = Color(0xFF9AA6C8),
    surfaceContainerLowest = Color(0xFF050813), surfaceContainerLow = Color(0xFF0F1630),
    surfaceContainer = Color(0xFF141C3A), surfaceContainerHigh = Color(0xFF1B2547),
    surfaceContainerHighest = Color(0xFF232E56),
    outline = Color(0xFF4A5680), outlineVariant = Color(0xFF2A3358),
)

private val Light = lightColorScheme(
    primary = Color(0xFF2F6BFF), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF), onPrimaryContainer = Color(0xFF001B4D),
    secondary = Color(0xFF7A4DFF), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E0FF), onSecondaryContainer = Color(0xFF260068),
    tertiary = Color(0xFFD6338F), onTertiary = Color.White,
    background = Color(0xFFF4F7FF), onBackground = Color(0xFF10142A),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF10142A),
    surfaceVariant = Color(0xFFE3E8FF), onSurfaceVariant = Color(0xFF50587A),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEEF2FF),
    surfaceContainer = Color(0xFFE8EDFF), surfaceContainerHigh = Color(0xFFE0E6FF),
    surfaceContainerHighest = Color(0xFFD8DFFA),
    outline = Color(0xFF8A93B8), outlineVariant = Color(0xFFCBD3F0),
)

private val MemoraShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun MemoraTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, shapes = MemoraShapes, content = content)
}
