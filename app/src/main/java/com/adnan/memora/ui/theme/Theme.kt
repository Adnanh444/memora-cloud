package com.adnan.memora.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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

private val CleanLight = lightColorScheme(
    primary = Color(0xFF1A73E8), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD), onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF3C4043), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EAED), onSecondaryContainer = Color(0xFF1F1F1F),
    tertiary = Color(0xFF188038), onTertiary = Color.White,
    background = Color.White, onBackground = Color(0xFF1F1F1F),
    surface = Color.White, onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFE8EAED), onSurfaceVariant = Color(0xFF444746),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F9FA),
    surfaceContainer = Color(0xFFF1F3F4), surfaceContainerHigh = Color(0xFFE8EAED),
    surfaceContainerHighest = Color(0xFFDADCE0),
    outline = Color(0xFF747775), outlineVariant = Color(0xFFC4C7C5),
    error = Color(0xFFD93025), errorContainer = Color(0xFFFAD2CF), onErrorContainer = Color(0xFF410002),
)

private val CleanDark = darkColorScheme(
    primary = Color(0xFF8AB4F8), onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0), onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFC4C7C5), onSecondary = Color(0xFF1F1F1F),
    secondaryContainer = Color(0xFF3C4043), onSecondaryContainer = Color(0xFFE3E3E3),
    tertiary = Color(0xFF81C995), onTertiary = Color(0xFF0B3818),
    background = Color(0xFF131314), onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF131314), onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF444746), onSurfaceVariant = Color(0xFFC4C7C5),
    surfaceContainerLowest = Color(0xFF0E0E0F), surfaceContainerLow = Color(0xFF1B1B1C),
    surfaceContainer = Color(0xFF1E1F20), surfaceContainerHigh = Color(0xFF282A2C),
    surfaceContainerHighest = Color(0xFF333537),
    outline = Color(0xFF8E918F), outlineVariant = Color(0xFF444746),
    error = Color(0xFFF28B82), errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFFAD2CF),
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
    val ctx = LocalContext.current
    val clean = remember { com.adnan.memora.Prefs.cleanStyle(ctx) }
    val scheme = if (clean) (if (dark) CleanDark else CleanLight) else (if (dark) Dark else Light)
    CompositionLocalProvider(LocalCleanStyle provides clean) {
        MaterialTheme(colorScheme = scheme, shapes = MemoraShapes, typography = MemoraTypography, content = content)
    }
}
