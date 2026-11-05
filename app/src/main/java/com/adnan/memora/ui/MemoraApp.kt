package com.adnan.memora.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.adnan.memora.R
import com.adnan.memora.gallery.CollectionKind
import com.adnan.memora.gallery.GalleryViewModel
import com.adnan.memora.ui.screens.*
import com.adnan.memora.ui.theme.AuroraBackground
import com.adnan.memora.ui.theme.LocalCleanStyle
import com.adnan.memora.ui.theme.glass

private enum class Tab(val route: String, val label: Int, val icon: ImageVector) {
    Photos("photos", R.string.rf_gallery, Icons.Outlined.PhotoLibrary),
    Cloud("cloud", R.string.nav_cloud, Icons.Outlined.Cloud),
    Library("library", R.string.rf_albums, Icons.Outlined.PhotoAlbum),
    Settings("settings", R.string.nav_settings, Icons.Outlined.Settings),
}

@Composable
fun MemoraApp() {
    val nav = rememberNavController()
    val gallery: GalleryViewModel = viewModel()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { gallery.refresh() }
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val showBar = Tab.entries.any { it.route == current }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AuroraBackground(Modifier.fillMaxSize())
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                AnimatedVisibility(
                    visible = showBar,
                    enter = slideInVertically(tween(350)) { it } + fadeIn(tween(350)),
                    exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
                ) {
                    FloatingNavBar(current) { t ->
                        gallery.clearSelection()
                        nav.navigate(t.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    }
                }
            },
        ) { pad ->
            NavHost(
                nav, startDestination = Tab.Photos.route, modifier = Modifier.padding(pad),
                enterTransition = { fadeIn(tween(300, delayMillis = 90)) + scaleIn(tween(300, delayMillis = 90), initialScale = 0.94f) },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(300, delayMillis = 90)) + scaleIn(tween(300, delayMillis = 90), initialScale = 0.94f) },
                popExitTransition = { fadeOut(tween(150)) + scaleOut(tween(200), targetScale = 1.05f) },
            ) {
                composable(Tab.Photos.route) {
                    PhotosScreen(
                        gallery,
                        onOpenViewer = { nav.navigate("viewer") },
                        onSearch = { nav.navigate("search/phone") },
                        onBackup = { nav.navigate("backup") },
                        onSettings = {
                            gallery.clearSelection()
                            nav.navigate(Tab.Settings.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                    )
                }
                composable(Tab.Cloud.route) {
                    CloudScreen(gallery, onOpenViewer = { nav.navigate("viewer") }, onOpenStorage = { nav.navigate("telegram") }, onSearch = { nav.navigate("search/cloud") })
                }
                composable(Tab.Library.route) {
                    LibraryScreen(
                        gallery,
                        onFolder = { name -> gallery.openFolderName = name; nav.navigate("folder") },
                        onBin = { nav.navigate("bin") },
                        onCloudBin = { nav.navigate("cloudbin") },
                        onDuplicates = { nav.navigate("duplicates") },
                        onFavorites = { gallery.collectionKind = CollectionKind.FAVORITES; nav.navigate("collection") },
                        onArchive = { gallery.collectionKind = CollectionKind.ARCHIVE; nav.navigate("collection") },
                        onAlbum = { id ->
                            gallery.collectionKind = CollectionKind.ALBUM
                            gallery.collectionAlbumId = id
                            nav.navigate("collection")
                        },
                        onFreeUp = { nav.navigate("freeup") },
                        onSearch = { nav.navigate("search/phone") },
                    )
                }
                composable(Tab.Settings.route) {
                    SettingsScreen(
                        onAbout = { nav.navigate("about") },
                        onTelegram = { nav.navigate("telegram") },
                        onBackup = { nav.navigate("backup") },
                        onDuplicates = { nav.navigate("duplicates") },
                    )
                }
                composable("duplicates") { DuplicatesScreen(gallery, onBack = { nav.popBackStack() }) }
                composable("backup") {
                    BackupScreen(gallery, onBack = { nav.popBackStack() }, onOpenTelegram = { nav.navigate("telegram") })
                }
                composable("telegram") { TelegramScreen(onBack = { nav.popBackStack() }, onRecover = { nav.navigate("recovery") }) }
                composable("recovery") {
                    RecoveryScreen(
                        onBack = { nav.popBackStack() },
                        onOpenCloud = {
                            nav.navigate(Tab.Cloud.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                    )
                }
                composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
                composable("viewer") { ViewerScreen(gallery, onBack = { nav.popBackStack() }) }
                composable("search/{scope}") { searchEntry ->
                    SearchScreen(gallery, onBack = { nav.popBackStack() }, onOpenViewer = { nav.navigate("viewer") }, scope = searchEntry.arguments?.getString("scope") ?: "phone" )
                }
                composable("folder") {
                    FolderScreen(gallery, gallery.openFolderName, onBack = { nav.popBackStack() }, onOpenViewer = { nav.navigate("viewer") }, onSearch = { nav.navigate("search/folder") })
                }
                composable("bin") { BinScreen(gallery, onBack = { nav.popBackStack() }) }
                composable("collection") {
                    CollectionScreen(gallery, onBack = { nav.popBackStack() }, onOpenViewer = { nav.navigate("viewer") }, onSearch = { nav.navigate("search/collection") })
                }
                composable("freeup") { FreeUpScreen(gallery, onBack = { nav.popBackStack() }) }
                composable("cloudbin") { CloudBinScreen(gallery, onBack = { nav.popBackStack() }) }
            }
        }
    }
}

@Composable
private fun FloatingNavBar(current: String?, onSelect: (Tab) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)
        .glass(shape).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Tab.entries.forEach { tab ->
            val selected = current == tab.route
            val accent = when (tab) {
                Tab.Photos -> cs.primary; Tab.Cloud -> cs.secondary
                Tab.Library -> cs.tertiary; Tab.Settings -> cs.onSurface
            }
            val fill by animateColorAsState(if (selected) accent.copy(alpha = .16f) else Color.Transparent, label = "tab-glass")
            val outline by animateColorAsState(if (selected) accent.copy(alpha = .55f) else Color.Transparent, label = "tab-edge")
            Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(fill)
                .border(1.dp, outline, RoundedCornerShape(18.dp))
                .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) })
                .heightIn(min = 58.dp).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(tab.icon, null, tint = if (selected) accent else cs.onSurfaceVariant, modifier = Modifier.size(23.dp))
                Text(stringResource(tab.label), style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) cs.onSurface else cs.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
