package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Material 3 bottom navigation bar — 4 tabs (Home, Matches, News, More).
 */

data class BottomNavItemV2(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItemsV2 = listOf(
    BottomNavItemV2("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItemV2("all_matches", "Matches", Icons.Filled.SportsCricket, Icons.Outlined.SportsCricket),
    BottomNavItemV2("news", "News", Icons.Filled.Newspaper, Icons.Outlined.Newspaper),
    BottomNavItemV2("more", "More", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz),
)

@Composable
fun BottomNavBarV2(
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors

    NavigationBar(
        modifier = modifier,
        containerColor = colors.surface,
        contentColor = colors.onSurface,
        windowInsets = WindowInsets.navigationBars,
    ) {
        bottomNavItemsV2.forEach { item ->
            val isSelected = isTabActive(currentRoute, item.route)

            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(item.route) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(text = item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.primaryText,
                    selectedTextColor = colors.primaryText,
                    unselectedIconColor = colors.textSecondary,
                    unselectedTextColor = colors.textSecondary,
                    // No pill behind the selected item: gold ink and the filled icon already mark it.
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}

private fun isTabActive(currentRoute: String?, tabRoute: String): Boolean {
    if (currentRoute == null) return tabRoute == "home"
    if (currentRoute == tabRoute) return true
    // "More" tab should stay active for its child routes
    if (tabRoute == "more") {
        return currentRoute in setOf(
            "stats", "trophy-room", "stats_overview", "settings",
            "appearance", "about", "devtools", "achievements",
            "ar-tracking", "activity-feed",
        ) || currentRoute.startsWith("player/")
    }
    // "Matches" tab for match detail routes
    if (tabRoute == "all_matches") {
        return currentRoute.startsWith("match/")
    }
    // "News" tab for news detail
    if (tabRoute == "news") {
        return currentRoute.startsWith("news/")
    }
    return false
}
