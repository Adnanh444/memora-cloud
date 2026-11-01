package com.adnan.memora.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.adnan.memora.R
import com.adnan.memora.gallery.GalleryViewModel
import com.adnan.memora.ui.screens.*

private enum class Tab(val route: String, val label: Int, val icon: ImageVector) {
    Photos("photos", R.string.nav_photos, Icons.Filled.PhotoLibrary),
    Cloud("cloud", R.string.nav_cloud, Icons.Filled.Cloud),
    People("people", R.string.nav_people, Icons.Filled.People),
    Albums("albums", R.string.nav_albums, Icons.Filled.PhotoAlbum),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings),
}

@Composable
fun MemoraApp() {
    val nav = rememberNavController()
    val gallery: GalleryViewModel = viewModel()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    Scaffold(
        bottomBar = {
            if (Tab.entries.any { it.route == current }) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = current == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, null) },
                            label = { Text(stringResource(t.label)) },
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = Tab.Photos.route, modifier = Modifier.padding(pad)) {
            composable(Tab.Photos.route) {
                PhotosScreen(gallery, onOpen = { nav.navigate("viewer/$it") })
            }
            composable(Tab.Cloud.route) { PlaceholderScreen(R.string.nav_cloud, Icons.Filled.Cloud) }
            composable(Tab.People.route) { PlaceholderScreen(R.string.nav_people, Icons.Filled.People) }
            composable(Tab.Albums.route) { PlaceholderScreen(R.string.nav_albums, Icons.Filled.PhotoAlbum) }
            composable(Tab.Settings.route) { SettingsScreen(onAbout = { nav.navigate("about") }) }
            composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
            composable(
                "viewer/{index}",
                arguments = listOf(navArgument("index") { type = NavType.IntType }),
            ) { e ->
                ViewerScreen(gallery, e.arguments?.getInt("index") ?: 0, onBack = { nav.popBackStack() })
            }
        }
    }
}
