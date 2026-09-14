package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Newspaper
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
 * Material 3 bottom navigation bar — 4 tabs (Home, Series, News, More).
 *
 * There is no Matches tab: Home lists every match (All/Live/Upcoming/Recent), and its "All matches"
 * link opens the full list as a pushed screen.
 */

data class BottomNavItemV2(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItemsV2 = listOf(
    BottomNavItemV2("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItemV2("series", "Series", Icons.Filled.DateRange, Icons.Outlined.DateRange),
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

private val MORE_CHILD_ROUTES = setOf(
    "stats", "trophy-room", "stats_overview", "settings",
    "appearance", "about", "devtools", "achievements",
    "ar-tracking", "activity-feed",
)

// Child routes that keep a tab highlighted. Home owns match detail and the full match list it links
// to; Series owns series detail and points tables.
private val TAB_CHILDREN: Map<String, (String) -> Boolean> = mapOf(
    "home" to { route -> route.startsWith("match/") || route == "all_matches" },
    "series" to { route -> route.startsWith("series/") || route.startsWith("points-table/") },
    "news" to { route -> route.startsWith("news/") },
    "more" to { route -> route in MORE_CHILD_ROUTES || route.startsWith("player/") },
)

private fun isTabActive(currentRoute: String?, tabRoute: String): Boolean = when (currentRoute) {
    null -> tabRoute == "home"
    tabRoute -> true
    else -> TAB_CHILDREN[tabRoute]?.invoke(currentRoute) == true
}
