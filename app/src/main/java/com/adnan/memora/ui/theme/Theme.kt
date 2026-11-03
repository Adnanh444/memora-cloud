package com.adnan.memora.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Futuristic "neon on deep space" palette (dark) and a crisp holographic-white palette (light).
private val Dark = darkColorScheme(
    primary = Color(0xFF6FDDFF), onPrimary = Color(0xFF00212B),
    primaryContainer = Color(0xFF0E4A60), onPrimaryContainer = Color(0xFFC9F1FF),
    secondary = Color(0xFFB7A2FF), onSecondary = Color(0xFF1D0B55),
    secondaryContainer = Color(0xFF3A2D78), onSecondaryContainer = Color(0xFFEDE6FF),
    tertiary = Color(0xFFFF8AD6), onTertiary = Color(0xFF3A0027),
    background = Color(0xFF060914), onBackground = Color(0xFFF4F6FF),
    surface = Color(0xFF0E1426), onSurface = Color(0xFFF4F6FF),
    surfaceVariant = Color(0xFF1D2850), onSurfaceVariant = Color(0xFFC5CEEA),
    surfaceContainerLowest = Color(0xFF050813), surfaceContainerLow = Color(0xFF111A35),
    surfaceContainer = Color(0xFF161F3E), surfaceContainerHigh = Color(0xFF1D2850),
    surfaceContainerHighest = Color(0xFF27345F),
    outline = Color(0xFF7E8BB8), outlineVariant = Color(0xFF3A4672),
)

private val Light = lightColorScheme(
    primary = Color(0xFF2F6BFF), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF), onPrimaryContainer = Color(0xFF001B4D),
    secondary = Color(0xFF7A4DFF), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E0FF), onSecondaryContainer = Color(0xFF260068),
    tertiary = Color(0xFFD6338F), onTertiary = Color.White,
    background = Color(0xFFF4F7FF), onBackground = Color(0xFF10142A),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF10142A),
    surfaceVariant = Color(0xFFE3E8FF), onSurfaceVariant = Color(0xFF3F486B),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEEF2FF),
    surfaceContainer = Color(0xFFE8EDFF), surfaceContainerHigh = Color(0xFFE0E6FF),
    surfaceContainerHighest = Color(0xFFD8DFFA),
    outline = Color(0xFF6F789E), outlineVariant = Color(0xFFB9C3E8),
)

private val base = Typography()
// Slightly larger text everywhere for better readability.
private val MemoraTypography = Typography(
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp),
    bodySmall = base.bodySmall.copy(fontSize = 13.sp),
    labelLarge = base.labelLarge.copy(fontSize = 15.sp),
    labelMedium = base.labelMedium.copy(fontSize = 13.sp),
    labelSmall = base.labelSmall.copy(fontSize = 12.sp),
    titleSmall = base.titleSmall.copy(fontSize = 15.sp),
)

private val MemoraShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun MemoraTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, shapes = MemoraShapes, typography = MemoraTypography, content = content)
}
