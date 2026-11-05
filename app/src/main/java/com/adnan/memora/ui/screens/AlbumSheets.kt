package com.adnan.memora.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.adnan.memora.gallery.GalleryViewModel
import com.adnan.memora.gallery.LocalMedia
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adnan.memora.R
import com.adnan.memora.org.AlbumEntity

/** Pick an album or create a new one. */
@Composable
fun AddToAlbumSheet(
    albums: List<AlbumEntity>,
    onPick: (String) -> Unit,
    onNew: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(albums.isEmpty()) }
    var name by remember { mutableStateOf("") }
    MemoraSheet(onDismiss) { close ->
        Text(stringResource(R.string.og_add_to_album), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        if (creating) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(R.string.og_album_name)) }, modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { close { onNew(name) } }, enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.og_create)) }
            }
        } else {
            SheetOption(Icons.Filled.Add, stringResource(R.string.og_new_album)) { creating = true }
        }
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            albums.forEach { a -> SheetOption(Icons.Filled.PhotoAlbum, a.name) { close { onPick(a.id) } } }
        }
        TextButton(onClick = { close {} }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.cancel))
        }
    }
}

/** Ask for a name (new album or rename). */
@Composable
fun AlbumNameSheet(title: String, initial: String, action: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    MemoraSheet(onDismiss) { close ->
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text(stringResource(R.string.og_album_name)) }, modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { close { onConfirm(name) } }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(action)
            }
        }
        TextButton(onClick = { close {} }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.cancel))
        }
    }
}

/** The ⋮ button: Favorite, Archive, Add to album. Works for phone and cloud items. */
@Composable
fun OrgMenu(
    vm: GalleryViewModel, enabled: Boolean = true, tint: Color = Color.Unspecified,
    onDone: () -> Unit = {}, pickItems: () -> List<LocalMedia>, modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    val favoriteAction = rememberPhoneFavoriteAction { vm.refresh() }
    val albums by vm.albums.collectAsState()
    Box(modifier) {
        IconButton(onClick = { menu = true }, enabled = enabled) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.og_more), tint = tint)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.og_favorite)) },
                enabled = android.os.Build.VERSION.SDK_INT >= 30 && pickItems().any { PhoneFavoriteRules.isLocal(it.uri.scheme) },
                leadingIcon = { Icon(Icons.Filled.FavoriteBorder, null) },
                onClick = { menu = false; favoriteAction(pickItems(), true); onDone() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.og_archive_action)) },
                leadingIcon = { Icon(Icons.Filled.Archive, null) },
                onClick = { menu = false; vm.setArchived(pickItems(), true); onDone() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.og_add_to_album)) },
                leadingIcon = { Icon(Icons.Filled.Add, null) },
                onClick = { menu = false; sheet = true },
            )
        }
    }
    if (sheet) AddToAlbumSheet(
        albums = albums,
        onPick = { id -> vm.addToAlbum(pickItems(), id); onDone() },
        onNew = { n -> vm.createAlbum(n, pickItems()); onDone() },
        onDismiss = { sheet = false },
    )
}
