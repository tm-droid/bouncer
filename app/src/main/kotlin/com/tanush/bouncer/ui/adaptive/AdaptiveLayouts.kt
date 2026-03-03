package com.tanush.bouncer.ui.adaptive

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.BlackBackground
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import io.sentry.Sentry

/**
 * Adaptive Layouts for Bouncer Cricket App
 * 
 * Provides responsive layouts for phones, tablets, and foldables
 * following Material 3 adaptive design guidelines.
 */

// ==========================================
// Window Size Class Support
// ==========================================

/**
 * Simplified window size classification for Bouncer layouts
 */
enum class BouncerWindowSizeClass {
    /** Phones in portrait mode (< 600dp width) */
    Compact,
    /** Phones in landscape, small tablets, foldables (600-840dp width) */
    Medium,
    /** Large tablets, desktops (> 840dp width) */
    Expanded
}

/**
 * Provides additional context about the window configuration
 */
data class WindowSizeInfo(
    val widthClass: BouncerWindowSizeClass,
    val heightClass: BouncerWindowSizeClass,
    val windowWidthDp: Dp,
    val windowHeightDp: Dp,
    val isLandscape: Boolean,
    val isTablet: Boolean
) {
    val shouldShowNavRail: Boolean get() = widthClass != BouncerWindowSizeClass.Compact
    val shouldShowBottomNav: Boolean get() = widthClass == BouncerWindowSizeClass.Compact
    val shouldUseTwoPane: Boolean get() = widthClass == BouncerWindowSizeClass.Expanded
    val columnCount: Int get() = when (widthClass) {
        BouncerWindowSizeClass.Compact -> 1
        BouncerWindowSizeClass.Medium -> 2
        BouncerWindowSizeClass.Expanded -> 3
    }
}

/**
 * Remember the current window size class with proper lifecycle awareness
 */
@Composable
fun rememberWindowSizeClass(): WindowSizeInfo {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    val windowWidthDp = configuration.screenWidthDp.dp
    val windowHeightDp = configuration.screenHeightDp.dp
    
    val widthClass = when {
        windowWidthDp < 600.dp -> BouncerWindowSizeClass.Compact
        windowWidthDp < 840.dp -> BouncerWindowSizeClass.Medium
        else -> BouncerWindowSizeClass.Expanded
    }
    
    val heightClass = when {
        windowHeightDp < 480.dp -> BouncerWindowSizeClass.Compact
        windowHeightDp < 900.dp -> BouncerWindowSizeClass.Medium
        else -> BouncerWindowSizeClass.Expanded
    }
    
    return remember(configuration) {
        WindowSizeInfo(
            widthClass = widthClass,
            heightClass = heightClass,
            windowWidthDp = windowWidthDp,
            windowHeightDp = windowHeightDp,
            isLandscape = windowWidthDp > windowHeightDp,
            isTablet = windowWidthDp >= 600.dp && windowHeightDp >= 480.dp
        )
    }
}

/**
 * Get Material 3 WindowSizeClass from Activity context
 * Requires Activity context to properly calculate
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberMaterial3WindowSizeClass(): WindowSizeClass? {
    val context = LocalContext.current
    val activity = context as? Activity
    return if (activity != null) {
        calculateWindowSizeClass(activity)
    } else {
        null
    }
}

// ==========================================
// Adaptive Scaffold with Responsive Navigation
// ==========================================

/**
 * Navigation item for adaptive navigation
 */
data class AdaptiveNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badge: String? = null
)

/**
 * Adaptive scaffold that automatically switches between:
 * - Bottom navigation on phones (Compact)
 * - Navigation rail on tablets (Medium/Expanded)
 * - Permanent navigation drawer on large screens (Expanded)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveScaffold(
    modifier: Modifier = Modifier,
    windowSizeInfo: WindowSizeInfo = rememberWindowSizeClass(),
    navigationItems: List<AdaptiveNavItem>,
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    topBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    containerColor: Color = BlackBackground,
    content: @Composable (PaddingValues) -> Unit
) {
    when (windowSizeInfo.widthClass) {
        BouncerWindowSizeClass.Compact -> {
            // Phone layout with bottom navigation
            Scaffold(
                modifier = modifier,
                topBar = topBar,
                bottomBar = {
                    BouncerBottomNavigation(
                        items = navigationItems,
                        selectedRoute = selectedRoute,
                        onNavigate = onNavigate
                    )
                },
                floatingActionButton = floatingActionButton,
                containerColor = containerColor,
                content = content
            )
        }
        
        BouncerWindowSizeClass.Medium -> {
            // Tablet/foldable layout with navigation rail
            Row(modifier = modifier.fillMaxSize()) {
                BouncerNavigationRail(
                    items = navigationItems,
                    selectedRoute = selectedRoute,
                    onNavigate = onNavigate
                )
                Scaffold(
                    topBar = topBar,
                    floatingActionButton = floatingActionButton,
                    containerColor = containerColor,
                    content = content
                )
            }
        }
        
        BouncerWindowSizeClass.Expanded -> {
            // Large tablet/desktop layout with permanent drawer
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        modifier = Modifier.width(280.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Bouncer",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        navigationItems.forEach { item ->
                            NavigationDrawerItem(
                                icon = {
                                    Icon(
                                        if (selectedRoute == item.route) item.selectedIcon else item.icon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                selected = selectedRoute == item.route,
                                onClick = { onNavigate(item.route) },
                                modifier = Modifier.padding(horizontal = 12.dp),
                                badge = item.badge?.let { { Text(it) } }
                            )
                        }
                    }
                },
                modifier = modifier
            ) {
                Scaffold(
                    topBar = topBar,
                    floatingActionButton = floatingActionButton,
                    containerColor = containerColor,
                    content = content
                )
            }
        }
    }
}

@Composable
private fun BouncerBottomNavigation(
    items: List<AdaptiveNavItem>,
    selectedRoute: String,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    BadgedBox(
                        badge = {
                            item.badge?.let { badge ->
                                Badge { Text(badge) }
                            }
                        }
                    ) {
                        Icon(
                            if (selectedRoute == item.route) item.selectedIcon else item.icon,
                            contentDescription = item.label
                        )
                    }
                },
                label = { Text(item.label) },
                selected = selectedRoute == item.route,
                onClick = { onNavigate(item.route) }
            )
        }
    }
}

@Composable
private fun BouncerNavigationRail(
    items: List<AdaptiveNavItem>,
    selectedRoute: String,
    onNavigate: (String) -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Spacer(Modifier.height(16.dp))
        items.forEach { item ->
            NavigationRailItem(
                icon = {
                    BadgedBox(
                        badge = {
                            item.badge?.let { badge ->
                                Badge { Text(badge) }
                            }
                        }
                    ) {
                        Icon(
                            if (selectedRoute == item.route) item.selectedIcon else item.icon,
                            contentDescription = item.label
                        )
                    }
                },
                label = { Text(item.label) },
                selected = selectedRoute == item.route,
                onClick = { onNavigate(item.route) }
            )
        }
    }
}

// ==========================================
// Two-Pane Layout for Tablets
// ==========================================

/**
 * Two-pane layout for tablets and large screens
 * First pane (list) takes 40-50% width, second pane (detail) takes the rest
 */
@Composable
fun TwoPaneLayout(
    modifier: Modifier = Modifier,
    windowSizeInfo: WindowSizeInfo = rememberWindowSizeClass(),
    firstPaneWeight: Float = 0.4f,
    dividerColor: Color = MaterialTheme.colorScheme.outlineVariant,
    showDivider: Boolean = true,
    firstPane: @Composable () -> Unit,
    secondPane: @Composable () -> Unit
) {
    if (windowSizeInfo.shouldUseTwoPane) {
        Row(
            modifier = modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(firstPaneWeight)
                    .fillMaxHeight()
            ) {
                firstPane()
            }
            
            if (showDivider) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(dividerColor)
                )
            }
            
            Box(
                modifier = Modifier
                    .weight(1f - firstPaneWeight)
                    .fillMaxHeight()
            ) {
                secondPane()
            }
        }
    } else {
        // On smaller screens, show first pane only
        // Navigation to second pane should be handled by the caller
        Box(modifier = modifier.fillMaxSize()) {
            firstPane()
        }
    }
}

// ==========================================
// List-Detail Layout (Master-Detail Pattern)
// ==========================================

/**
 * State holder for List-Detail navigation
 */
@Stable
class ListDetailState<T>(
    initialSelectedItem: T? = null
) {
    var selectedItem by mutableStateOf(initialSelectedItem)
        private set
    
    fun selectItem(item: T?) {
        try {
            selectedItem = item
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
    
    fun clearSelection() {
        try {
            selectedItem = null
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    }
}

@Composable
fun <T> rememberListDetailState(initialSelectedItem: T? = null): ListDetailState<T> {
    return remember { ListDetailState(initialSelectedItem) }
}

/**
 * List-Detail layout with automatic adaptation for screen size
 * - On phones: Shows list or detail based on selection (with back navigation)
 * - On tablets: Shows both list and detail side by side
 */
@Composable
fun <T> ListDetailLayout(
    modifier: Modifier = Modifier,
    state: ListDetailState<T>,
    windowSizeInfo: WindowSizeInfo = rememberWindowSizeClass(),
    listPaneWeight: Float = 0.4f,
    listContent: @Composable (onItemSelected: (T) -> Unit) -> Unit,
    detailContent: @Composable (item: T?, onBack: () -> Unit) -> Unit,
    emptyDetailContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Select an item",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
) {
    val isExpanded = windowSizeInfo.shouldUseTwoPane
    
    if (isExpanded) {
        // Two-pane layout for tablets
        TwoPaneLayout(
            modifier = modifier,
            windowSizeInfo = windowSizeInfo,
            firstPaneWeight = listPaneWeight,
            firstPane = {
                listContent { item ->
                    state.selectItem(item)
                }
            },
            secondPane = {
                AnimatedContent(
                    targetState = state.selectedItem,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)) togetherWith
                            fadeOut(animationSpec = tween(200))
                    },
                    label = "DetailContent"
                ) { selectedItem ->
                    if (selectedItem != null) {
                        detailContent(selectedItem) { /* No-op on tablets */ }
                    } else {
                        emptyDetailContent()
                    }
                }
            }
        )
    } else {
        // Single-pane layout for phones
        AnimatedContent(
            targetState = state.selectedItem,
            transitionSpec = {
                if (targetState != null) {
                    // Navigate to detail
                    slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
                } else {
                    // Navigate back to list
                    slideInHorizontally { -it } + fadeIn() togetherWith
                        slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "ListDetailTransition"
        ) { selectedItem ->
            if (selectedItem != null) {
                detailContent(selectedItem) { state.clearSelection() }
            } else {
                listContent { item ->
                    state.selectItem(item)
                }
            }
        }
    }
}

// ==========================================
// Responsive Grid
// ==========================================

/**
 * Responsive grid that adapts column count based on screen size
 */
@Composable
fun <T> ResponsiveGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    windowSizeInfo: WindowSizeInfo = rememberWindowSizeClass(),
    minColumnWidth: Dp = 320.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp),
    itemContent: @Composable (T) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minColumnWidth),
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        items(items) { item ->
            itemContent(item)
        }
    }
}

/**
 * Responsive grid with fixed column count based on window size class
 */
@Composable
fun <T> ResponsiveFixedGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    windowSizeInfo: WindowSizeInfo = rememberWindowSizeClass(),
    compactColumns: Int = 1,
    mediumColumns: Int = 2,
    expandedColumns: Int = 3,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp),
    itemContent: @Composable (T) -> Unit
) {
    val columnCount = when (windowSizeInfo.widthClass) {
        BouncerWindowSizeClass.Compact -> compactColumns
        BouncerWindowSizeClass.Medium -> mediumColumns
        BouncerWindowSizeClass.Expanded -> expandedColumns
    }
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        items(items) { item ->
            itemContent(item)
        }
    }
}

// ==========================================
// Adaptive Padding
// ==========================================

/**
 * Provides adaptive padding values based on screen size
 */
object AdaptivePadding {
    
    @Composable
    fun horizontal(windowSizeInfo: WindowSizeInfo = rememberWindowSizeClass()): Dp {
        return when (windowSizeInfo.widthClass) {
            BouncerWindowSizeClass.Compact -> 16.dp
            BouncerWindowSizeClass.Medium -> 24.dp
            BouncerWindowSizeClass.Expanded -> 32.dp
        }
    }
    
    @Composable
    fun vertical(windowSizeInfo: WindowSizeInfo = rememberWindowSizeClass()): Dp {
        return when (windowSizeInfo.heightClass) {
            BouncerWindowSizeClass.Compact -> 8.dp
            BouncerWindowSizeClass.Medium -> 16.dp
            BouncerWindowSizeClass.Expanded -> 24.dp
        }
    }
    
    @Composable
    fun all(windowSizeInfo: WindowSizeInfo = rememberWindowSizeClass()): PaddingValues {
        return PaddingValues(
            horizontal = horizontal(windowSizeInfo),
            vertical = vertical(windowSizeInfo)
        )
    }
    
    @Composable
    fun contentPadding(windowSizeInfo: WindowSizeInfo = rememberWindowSizeClass()): PaddingValues {
        return when (windowSizeInfo.widthClass) {
            BouncerWindowSizeClass.Compact -> PaddingValues(16.dp)
            BouncerWindowSizeClass.Medium -> PaddingValues(24.dp)
            BouncerWindowSizeClass.Expanded -> PaddingValues(
                horizontal = 48.dp,
                vertical = 24.dp
            )
        }
    }
    
    /**
     * Edge-to-edge content padding that respects system bars
     */
    @Composable
    fun safeDrawingPadding(windowSizeInfo: WindowSizeInfo = rememberWindowSizeClass()): PaddingValues {
        val basePadding = contentPadding(windowSizeInfo)
        return PaddingValues(
            start = basePadding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            top = basePadding.calculateTopPadding(),
            end = basePadding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            bottom = basePadding.calculateBottomPadding()
        )
    }
}

/**
 * Modifier extension for applying adaptive padding
 */
fun Modifier.adaptivePadding(
    windowSizeInfo: WindowSizeInfo,
    horizontal: Boolean = true,
    vertical: Boolean = true
): Modifier {
    val horizontalPadding = if (horizontal) {
        try {
            when (windowSizeInfo.widthClass) {
                BouncerWindowSizeClass.Compact -> 16.dp
                BouncerWindowSizeClass.Medium -> 24.dp
                BouncerWindowSizeClass.Expanded -> 32.dp
            }
        } catch (e: Exception) {
            Sentry.captureException(e)
            throw e
        }
    } else 0.dp
    
    val verticalPadding = if (vertical) {
        when (windowSizeInfo.heightClass) {
            BouncerWindowSizeClass.Compact -> 8.dp
            BouncerWindowSizeClass.Medium -> 16.dp
            BouncerWindowSizeClass.Expanded -> 24.dp
        }
    } else 0.dp
    
    return this.padding(horizontal = horizontalPadding, vertical = verticalPadding)
}

// ==========================================
// Adaptive Content Width
// ==========================================

/**
 * Constrains content width on large screens for better readability
 */
@Composable
fun AdaptiveContentWidth(
    modifier: Modifier = Modifier,
    windowSizeInfo: WindowSizeInfo = rememberWindowSizeClass(),
    maxWidth: Dp = 1200.dp,
    centerContent: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (centerContent) Alignment.TopCenter else Alignment.TopStart
    ) {
        Box(
            modifier = Modifier.widthIn(max = maxWidth)
        ) {
            content()
        }
    }
}

// ==========================================
// Breakpoint-based Composable Switching
// ==========================================

/**
 * Shows different content based on window size class
 */
@Composable
fun AdaptiveContent(
    windowSizeInfo: WindowSizeInfo = rememberWindowSizeClass(),
    compactContent: @Composable () -> Unit,
    mediumContent: @Composable () -> Unit = compactContent,
    expandedContent: @Composable () -> Unit = mediumContent
) {
    when (windowSizeInfo.widthClass) {
        BouncerWindowSizeClass.Compact -> compactContent()
        BouncerWindowSizeClass.Medium -> mediumContent()
        BouncerWindowSizeClass.Expanded -> expandedContent()
    }
}
