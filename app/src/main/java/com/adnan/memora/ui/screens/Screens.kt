package com.adnan.memora.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.adnan.memora.BuildConfig
import com.adnan.memora.Prefs
import com.adnan.memora.R
import com.adnan.memora.ui.theme.GradientTitle
import com.adnan.memora.ui.theme.appear
import com.adnan.memora.ui.theme.brandBrush
import com.adnan.memora.ui.theme.glass

private val LogoBrush = Brush.linearGradient(listOf(Color(0xFF4F8CFF), Color(0xFF6A3FE0)))

@Composable
fun PlaceholderScreen(title: Int, icon: ImageVector) {
    val t = rememberInfiniteTransition(label = "pulse")
    val pulse by t.animateFloat(0.92f, 1.08f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
        GradientTitle(stringResource(title))
        Column(
            Modifier.fillMaxSize().appear(100),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(150.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }
                    .background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), Color.Transparent)), CircleShape),
                Alignment.Center,
            ) {
                Box(Modifier.size(92.dp).glass(CircleShape), Alignment.Center) {
                    Icon(icon, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsScreen(onAbout: () -> Unit, onTelegram: () -> Unit, onBackup: () -> Unit, onDuplicates: () -> Unit) {
    val ctx = LocalContext.current
    val backupLabel = stringResource(if (com.adnan.memora.backup.BackupSettings.load(ctx).enabled) R.string.bk_on else R.string.bk_off)
    val tgAuth by com.adnan.memora.telegram.TelegramClient.get(ctx).auth.collectAsState()
    val tgLabel = when (tgAuth) {
        is com.adnan.memora.telegram.TgAuth.Ready -> stringResource(R.string.tg_connected)
        else -> stringResource(R.string.tg_disconnected)
    }
    var showTheme by remember { mutableStateOf(false) }
    var showLang by remember { mutableStateOf(false) }
    val night = Prefs.themeMode(ctx)
    val langTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')

    val themeModes = listOf(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to stringResource(R.string.theme_system),
        AppCompatDelegate.MODE_NIGHT_NO to stringResource(R.string.theme_light),
        AppCompatDelegate.MODE_NIGHT_YES to stringResource(R.string.theme_dark),
    )
    val langs = listOf("" to stringResource(R.string.lang_system), "en" to "English", "bn" to "বাংলা")
    val themeLabel = themeModes.firstOrNull { it.first == night }?.second ?: ""
    val langLabel = langs.firstOrNull { it.first == langTag }?.second ?: langs[0].second

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GradientTitle(stringResource(R.string.nav_settings), Modifier.padding(4.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.settings_general), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp).appear(60))
        Column(Modifier.fillMaxWidth().appear(120).glass()) {
            SettingRow(Icons.Filled.Cloud, stringResource(R.string.telegram_storage), tgLabel, onTelegram)
            SettingRow(Icons.Filled.CloudUpload, stringResource(R.string.bk_title), backupLabel, onBackup)
            SettingRow(Icons.Filled.ContentCopy, stringResource(R.string.dup_title), stringResource(R.string.dup_sub), onDuplicates)
            SettingRow(Icons.Filled.Palette, stringResource(R.string.appearance), themeLabel) { showTheme = true }
            SettingRow(Icons.Filled.Language, stringResource(R.string.language), langLabel) { showLang = true }
            SettingRow(Icons.Filled.Info, stringResource(R.string.about), null, onAbout)
        }
    }

    if (showTheme) ChoiceSheet(
        title = stringResource(R.string.appearance),
        options = themeModes.map { it.second to (it.first == night) },
        onPick = { Prefs.setThemeMode(ctx, themeModes[it].first); showTheme = false },
        onDismiss = { showTheme = false },
    )
    if (showLang) ChoiceSheet(
        title = stringResource(R.string.language),
        options = langs.map { it.second to (it.first == langTag) },
        onPick = {
            showLang = false
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langs[it].first))
        },
        onDismiss = { showLang = false },
    )
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(Modifier.size(42.dp).clip(CircleShape).background(brandBrush()), Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val t = rememberInfiniteTransition(label = "ring")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "angle")
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.about)) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
            },
        )
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(150.dp).appear(0), Alignment.Center) {
                Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = angle }
                    .background(Brush.sweepGradient(listOf(cs.primary, cs.secondary, cs.tertiary, cs.primary)), CircleShape))
                Box(Modifier.size(142.dp).background(cs.background, CircleShape))
                Box(Modifier.size(116.dp).clip(RoundedCornerShape(34.dp)).background(LogoBrush), Alignment.Center) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(168.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            GradientTitle(stringResource(R.string.app_name), Modifier.appear(120))
            Text(stringResource(R.string.tagline), textAlign = TextAlign.Center,
                color = cs.onSurfaceVariant, modifier = Modifier.appear(200))
            Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), modifier = Modifier.appear(260))
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.appear(320).glass().padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.developed_by), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.copyright), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.open_source), style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}
