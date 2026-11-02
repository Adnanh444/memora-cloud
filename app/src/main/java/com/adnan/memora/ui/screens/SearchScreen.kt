package com.adnan.memora.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adnan.memora.R
import com.adnan.memora.gallery.GalleryViewModel
import com.adnan.memora.gallery.SearchEngine
import com.adnan.memora.ui.theme.appear
import com.adnan.memora.ui.theme.glass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: GalleryViewModel, onBack: () -> Unit, onOpenViewer: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val all by vm.all.collectAsState()
    val selecting by vm.selecting.collectAsState()
    val results = remember(all, query) { if (query.isBlank()) emptyList() else SearchEngine.search(all, query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    DisposableEffect(Unit) { onDispose { vm.clearSelection() } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Crossfade(selecting, label = "searchHeader") { sel ->
            if (sel) SelectionHeader(vm, results)
            else Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                TextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_field_hint)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, null) }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f).padding(end = 12.dp).glass(RoundedCornerShape(28.dp)).focusRequester(focus),
                )
            }
        }
        if (query.isBlank()) {
            Column(Modifier.padding(16.dp).appear(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Cat(stringResource(R.string.cat_videos), "videos") { query = it }
                    Cat(stringResource(R.string.cat_photos), "photos") { query = it }
                    Cat(stringResource(R.string.cat_screenshots), "screenshots") { query = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Cat(stringResource(R.string.cat_camera), "camera") { query = it }
                    Cat(stringResource(R.string.cat_today), "today") { query = it }
                    Cat(stringResource(R.string.cat_yesterday), "yesterday") { query = it }
                }
                Text(stringResource(R.string.search_info), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (results.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(stringResource(R.string.search_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(stringResource(R.string.results_count, results.size), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
            MediaGrid(list = results, vm = vm, onOpen = { i -> vm.openViewer(results, i); onOpenViewer() }, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Cat(label: String, query: String, onPick: (String) -> Unit) {
    AssistChip(onClick = { onPick(query) }, label = { Text(label) })
}
