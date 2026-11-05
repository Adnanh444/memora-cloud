package com.adnan.memora.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.adnan.memora.R

private val Day = lightColorScheme(
    primary = Color(0xFF245DC1), onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE9FF), onPrimaryContainer = Color(0xFF153A79),
    secondary = Color(0xFF167568), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9F3EC), onSecondaryContainer = Color(0xFF095246),
    tertiary = Color(0xFF8058A8), onTertiary = Color.White,
    background = Color(0xFFF9FAFC), onBackground = Color(0xFF182331),
    surface = Color.White, onSurface = Color(0xFF182331),
    surfaceVariant = Color(0xFFE7EDF5), onSurfaceVariant = Color(0xFF48566A),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F5FA),
    surfaceContainer = Color(0xFFEDF1F7), surfaceContainerHigh = Color(0xFFE4EAF3),
    surfaceContainerHighest = Color(0xFFD9E2EE), outline = Color(0xFF73829A), outlineVariant = Color(0xFFCBD5E3),
    error = Color(0xFFB32637), errorContainer = Color(0xFFFFDDE1), onErrorContainer = Color(0xFF66001A),
)
private val Night = darkColorScheme(
    primary = Color(0xFFAFCBFF), onPrimary = Color(0xFF062B65),
    primaryContainer = Color(0xFF294D80), onPrimaryContainer = Color(0xFFE6EFFF),
    secondary = Color(0xFF91DCC8), onSecondary = Color(0xFF003C30),
    secondaryContainer = Color(0xFF254D45), onSecondaryContainer = Color(0xFFCEFFF1),
    tertiary = Color(0xFFD7BBFF), onTertiary = Color(0xFF442462),
    background = Color(0xFF10151D), onBackground = Color(0xFFF3F6FC),
    surface = Color(0xFF181F29), onSurface = Color(0xFFF3F6FC),
    surfaceVariant = Color(0xFF354153), onSurfaceVariant = Color(0xFFD1DCEC),
    surfaceContainerLowest = Color(0xFF0C1118), surfaceContainerLow = Color(0xFF1C2531),
    surfaceContainer = Color(0xFF243040), surfaceContainerHigh = Color(0xFF2E3C50),
    surfaceContainerHighest = Color(0xFF3A4B62), outline = Color(0xFF9BACBF), outlineVariant = Color(0xFF596B82),
    error = Color(0xFFFFB5BE), errorContainer = Color(0xFF692735), onErrorContainer = Color(0xFFFFE5E9),
)
private class Appearance(val dark: Boolean, val toggle: () -> Unit)
private val LocalAppearance = staticCompositionLocalOf { Appearance(false) {} }
private val face = FontFamily(Font(R.font.memora_regular, FontWeight.Normal), Font(R.font.memora_semibold, FontWeight.SemiBold))
private val base = Typography()
private val fonts = Typography(
    headlineLarge = base.headlineLarge.copy(fontFamily = face),
    headlineSmall = base.headlineSmall.copy(fontFamily = face),
    titleSmall = base.titleSmall.copy(fontFamily = face),
    bodySmall = base.bodySmall.copy(fontFamily = face),
    labelMedium = base.labelMedium.copy(fontFamily = face),
    labelSmall = base.labelSmall.copy(fontFamily = face),
    headlineMedium = base.headlineMedium.copy(fontFamily = face, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = face, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = base.titleMedium.copy(fontFamily = face, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = face, fontSize = 16.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = face, fontSize = 14.sp),
    labelLarge = base.labelLarge.copy(fontFamily = face, fontSize = 14.sp),
)

@Composable
fun MemoraTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("memora_clear_appearance", Context.MODE_PRIVATE) }
    var dark by remember { mutableStateOf(preferences.getBoolean("dark", false)) }
    val appearance = Appearance(dark) { dark = !dark; preferences.edit().putBoolean("dark", dark).apply() }
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalCleanStyle provides true, LocalAppearance provides appearance) {
        MaterialTheme(colorScheme = if (dark) Night else Day,
            typography = fonts, shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)), content = content)
    }
}

@Composable
fun ThemeToggle() {
    val appearance = LocalAppearance.current
    FilledTonalIconButton(onClick = appearance.toggle) {
        Icon(if (appearance.dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
            stringResource(if (appearance.dark) R.string.rf_light else R.string.rf_dark))
    }
}
