package com.adnan.memora.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import com.adnan.memora.ui.theme.ThemeToggle
import com.adnan.memora.ui.theme.GradientTitle
import com.adnan.memora.ui.theme.appear
import com.adnan.memora.ui.theme.brandBrush
import com.adnan.memora.ui.theme.glass

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

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
    val auth by com.adnan.memora.telegram.TelegramClient.get(ctx).auth.collectAsState()
    val lockEnabled by com.adnan.memora.ui.AppLockSettings.enabled.collectAsState()
    val toggleLock = com.adnan.memora.ui.rememberAppLockToggle()
    val tgLabel = stringResource(if (auth is com.adnan.memora.telegram.TgAuth.Ready) R.string.tg_connected else R.string.tg_disconnected)
    var showLang by remember { mutableStateOf(false) }
    var showCap by remember { mutableStateOf(false) }
    var cloudOnMetered by remember { mutableStateOf(com.adnan.memora.data.NetworkPolicy.cloudOnMetered(ctx)) }
    var capMb by remember { mutableStateOf(com.adnan.memora.data.NetworkPolicy.cacheCapMb(ctx)) }
    var cacheMb by remember { mutableStateOf(com.adnan.memora.data.NetworkPolicy.imageCacheBytes(ctx) / (1024L * 1024L)) }
    val caps = listOf(200, 500, 1000)
    val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
    val langs = listOf("" to stringResource(R.string.lang_system), "en" to "English", "bn" to "বাংলা")
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GradientTitle(stringResource(R.string.nav_settings), Modifier.weight(1f)); ThemeToggle()
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().glass()) {
            SettingRow(Icons.Filled.Cloud, stringResource(R.string.telegram_storage), tgLabel, onTelegram)
            SettingRow(Icons.Filled.CloudUpload, stringResource(R.string.bk_title), null, onBackup)
            SettingRow(Icons.Filled.Lock, stringResource(R.string.lp_app_lock), stringResource(if (lockEnabled) R.string.lp_lock_on else R.string.lp_lock_off), toggleLock)
            SettingRow(
                Icons.Filled.Wifi, stringResource(R.string.set_cloud_mobile),
                stringResource(if (cloudOnMetered) R.string.set_on else R.string.set_off_saves_data),
            ) {
                cloudOnMetered = !cloudOnMetered
                com.adnan.memora.data.NetworkPolicy.setCloudOnMetered(ctx, cloudOnMetered)
            }
            SettingRow(Icons.Filled.SdStorage, stringResource(R.string.set_cache_limit), "$capMb MB") { showCap = true }
            SettingRow(
                Icons.Filled.DeleteSweep, stringResource(R.string.set_clear_cache),
                stringResource(R.string.set_cache_used, cacheMb),
            ) {
                com.adnan.memora.data.NetworkPolicy.clearImageCache(ctx)
                cacheMb = 0
            }
            SettingRow(Icons.Filled.Language, stringResource(R.string.language), langs.firstOrNull { it.first == tag }?.second) { showLang = true }
            SettingRow(Icons.Filled.Info, stringResource(R.string.about), null, onAbout)
        }
    }
    if (showCap) ChoiceSheet(
        title = stringResource(R.string.set_cache_limit),
        options = caps.map { "$it MB" to (capMb == it) },
        onPick = {
            capMb = caps[it]
            com.adnan.memora.data.NetworkPolicy.setCacheCapMb(ctx, capMb)
            // The image loader was built with the old ceiling; reset so the new limit takes effect.
            coil.Coil.reset()
            showCap = false
        },
        onDismiss = { showCap = false },
    )
    if (showLang) ChoiceSheet(
        title = stringResource(R.string.language), options = langs.map { it.second to (it.first == tag) },
        onPick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langs[it].first)); showLang = false },
        onDismiss = { showLang = false },
    )
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), Alignment.Center) {
                Icon(icon, null, tint = when (icon) {
                    Icons.Filled.Cloud -> MaterialTheme.colorScheme.secondary
                    Icons.Filled.Language -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                })
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
