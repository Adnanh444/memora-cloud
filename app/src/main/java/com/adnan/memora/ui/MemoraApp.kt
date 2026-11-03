package com.adnan.memora.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.adnan.memora.R
import com.adnan.memora.gallery.GalleryViewModel
import com.adnan.memora.ui.screens.*
import com.adnan.memora.ui.theme.AuroraBackground
import com.adnan.memora.ui.theme.glass

private enum class Tab(val route: String, val label: Int, val icon: ImageVector) {
    Photos("photos", R.string.nav_photos, Icons.Filled.PhotoLibrary),
    Cloud("cloud", R.string.nav_cloud, Icons.Filled.Cloud),
    People("people", R.string.nav_people, Icons.Filled.People),
    Library("library", R.string.nav_library, Icons.Filled.PhotoAlbum),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings),
}

@Composable
fun MemoraApp() {
    val nav = rememberNavController()
    val gallery: GalleryViewModel = viewModel()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val showBar = Tab.entries.any { it.route == current }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AuroraBackground(Modifier.fillMaxSize())
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                AnimatedVisibility(
                    visible = showBar,
                    enter = slideInVertically(tween(350)) { it } + fadeIn(tween(350)),
                    exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
                ) {
                    FloatingNavBar(current) { t ->
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
                        onSearch = { nav.navigate("search") },
                        onBackup = { nav.navigate("backup") },
                    )
                }
                composable(Tab.Cloud.route) { PlaceholderScreen(R.string.nav_cloud, Icons.Filled.Cloud) }
                composable(Tab.People.route) { PlaceholderScreen(R.string.nav_people, Icons.Filled.People) }
                composable(Tab.Library.route) {
                    LibraryScreen(
                        gallery,
                        onFolder = { name -> gallery.openFolderName = name; nav.navigate("folder") },
                        onBin = { nav.navigate("bin") },
                    )
                }
                composable(Tab.Settings.route) {
                    SettingsScreen(
                        onAbout = { nav.navigate("about") },
                        onTelegram = { nav.navigate("telegram") },
                        onBackup = { nav.navigate("backup") },
                    )
                }
                composable("backup") {
                    BackupScreen(gallery, onBack = { nav.popBackStack() }, onOpenTelegram = { nav.navigate("telegram") })
                }
                composable("telegram") { TelegramScreen(onBack = { nav.popBackStack() }) }
                composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
                composable("viewer") { ViewerScreen(gallery, onBack = { nav.popBackStack() }) }
                composable("search") {
                    SearchScreen(gallery, onBack = { nav.popBackStack() }, onOpenViewer = { nav.navigate("viewer") })
                }
                composable("folder") {
                    FolderScreen(gallery, gallery.openFolderName, onBack = { nav.popBackStack() }, onOpenViewer = { nav.navigate("viewer") })
                }
                composable("bin") { BinScreen(gallery, onBack = { nav.popBackStack() }) }
            }
        }
    }
}

@Composable
private fun FloatingNavBar(current: String?, onSelect: (Tab) -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().glass(shape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)).padding(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { t -> NavItem(t, selected = current == t.route) { onSelect(t) } }
        }
    }
}

@Composable
private fun NavItem(t: Tab, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tint by animateColorAsState(if (selected) cs.onPrimary else cs.onSurfaceVariant, label = "tint")
    val pill = RoundedCornerShape(26.dp)
    val bg = if (selected) Modifier.background(Brush.linearGradient(listOf(cs.primary, cs.secondary)), pill) else Modifier
    Row(
        Modifier.clip(pill).clickable(onClick = onClick).then(bg)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            .padding(horizontal = if (selected) 16.dp else 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(t.icon, stringResource(t.label), tint = tint)
        AnimatedVisibility(selected) {
            Text(stringResource(t.label), color = tint, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.padding(start = 8.dp))
        }
    }
}
