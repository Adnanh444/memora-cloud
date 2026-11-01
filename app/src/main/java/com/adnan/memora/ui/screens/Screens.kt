package com.adnan.memora.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.adnan.memora.BuildConfig
import com.adnan.memora.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(title: Int) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopAppBar(title = { Text(stringResource(title)) })
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onAbout: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopAppBar(title = { Text(stringResource(R.string.nav_settings)) })
        ListItem(
            headlineContent = { Text(stringResource(R.string.about)) },
            leadingContent = { Icon(Icons.Filled.Info, null) },
            modifier = Modifier.fillMaxWidth().let { it },
            trailingContent = { TextButton(onClick = onAbout) { Text(">") } },
        )
    }
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
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.tagline), textAlign = TextAlign.Center)
            Text(stringResource(R.string.version, BuildConfig.VERSION_NAME))
            Text(stringResource(R.string.developed_by))
            Text(stringResource(R.string.copyright), style = MaterialTheme.typography.bodySmall)
        }
    }
}
