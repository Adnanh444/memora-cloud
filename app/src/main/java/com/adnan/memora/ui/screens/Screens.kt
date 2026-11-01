package com.adnan.memora.ui.screens

import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.adnan.memora.BuildConfig
import com.adnan.memora.Prefs
import com.adnan.memora.R

private val LogoBrush = Brush.linearGradient(listOf(Color(0xFF4F8CFF), Color(0xFF6A3FE0)))

@Composable
fun PlaceholderScreen(title: Int, icon: ImageVector) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                Alignment.Center,
            ) { Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsScreen(onAbout: () -> Unit) {
    val ctx = LocalContext.current
    var showTheme by remember { mutableStateOf(false) }
    var showLang by remember { mutableStateOf(false) }
    val night = Prefs.themeMode(ctx)
    val langTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()

    val themeModes = listOf(
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to stringResource(R.string.theme_system),
        AppCompatDelegate.MODE_NIGHT_NO to stringResource(R.string.theme_light),
        AppCompatDelegate.MODE_NIGHT_YES to stringResource(R.string.theme_dark),
    )
    val langs = listOf(
        "" to stringResource(R.string.lang_system),
        "en" to "English",
        "bn" to "বাংলা",
    )
    val themeLabel = themeModes.firstOrNull { it.first == night }?.second ?: ""
    val langLabel = langs.firstOrNull { it.first == langTag.substringBefore('-') }?.second ?: langs[0].second

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(4.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.settings_general), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            SettingRow(Icons.Filled.Palette, stringResource(R.string.appearance), themeLabel) { showTheme = true }
            SettingRow(Icons.Filled.Language, stringResource(R.string.language), langLabel) { showLang = true }
            SettingRow(Icons.Filled.Info, stringResource(R.string.about), null, onAbout)
        }
    }

    if (showTheme) ChoiceDialog(
        title = stringResource(R.string.appearance),
        options = themeModes.map { it.second to (it.first == night) },
        onPick = { Prefs.setThemeMode(ctx, themeModes[it].first); showTheme = false },
        onDismiss = { showTheme = false },
    )
    if (showLang) ChoiceDialog(
        title = stringResource(R.string.language),
        options = langs.map { it.second to (it.first == langTag.substringBefore('-')) },
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
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                Alignment.Center,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, Boolean>>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, (label, selected) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(i) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopAppBar(
            title = { Text(stringResource(R.string.about)) },
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
            Box(Modifier.size(104.dp).clip(RoundedCornerShape(30.dp)).background(LogoBrush), Alignment.Center) {
                Icon(Icons.Filled.Cloud, null, Modifier.size(60.dp), tint = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.tagline), textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.version, BuildConfig.VERSION_NAME))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.developed_by), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.copyright), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.open_source), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
