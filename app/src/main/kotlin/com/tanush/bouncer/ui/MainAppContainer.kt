package com.tanush.bouncer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.PremiumBottomNavBar
import com.tanush.bouncer.ui.components.navDestinations
import com.tanush.bouncer.ui.navigation.BouncerNavHost
import com.tanush.bouncer.ui.navigation.BouncerScreen
import com.tanush.bouncer.ui.navigation.navigateToTab
import com.tanush.bouncer.ui.screens.*
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import com.tanush.bouncer.viewmodels.NewsViewModel

/**
 * App entry point with navigation handling.
 *
 * This composable manages the root-level navigation state:
 * - Match detail modal (overlays the main container)
 * - AR tracking overlay
 *
 * The MainAppContainer handles all in-app navigation via NavController.
 */
@Composable
fun BouncerAppContent() {
    var currentMatch by remember { mutableStateOf<CricketMatch?>(null) }
    var showArTracking by remember { mutableStateOf(false) }

    BouncerTheme {
        Box(modifier = Modifier.fillMaxSize().background(BlackBackground)) {
            AnimatedContent(
                targetState = currentMatch,
                transitionSpec = {
                    if (targetState != null) {
                        (fadeIn(tween(300)) + slideInVertically(
                            initialOffsetY = { it / 10 },
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        )).togetherWith(
                            fadeOut(tween(200))
                        )
                    } else {
                        fadeIn(tween(200)) togetherWith (
                            fadeOut(tween(300)) + slideOutVertically(
                                targetOffsetY = { it / 10 },
                                animationSpec = tween(400, easing = FastOutSlowInEasing)
                            )
                        )
                    }
                },
                label = "rootNavigation"
            ) { match ->
                if (match != null) {
                    MatchDetailView(
                        match = match,
                        onBackClick = { currentMatch = null },
                        onArClick = { showArTracking = true }
                    )
                } else {
                    MainAppContainer(
                        onMatchClick = { clickedMatch ->
                            currentMatch = clickedMatch
                        },
                        onArTrackingRequest = { showArTracking = true }
                    )
                }
            }

            // AR Overlay Layer
            AnimatedVisibility(
                visible = showArTracking,
                enter = fadeIn(tween(500)) + scaleIn(initialScale = 0.9f),
                exit = fadeOut(tween(400)) + scaleOut(targetScale = 0.9f)
            ) {
                ArBallTrackingScreen(onBackClick = { showArTracking = false })
            }
        }
    }
}

/**
 * Main app container with NavController-based navigation.
 *
 * Uses Jetpack Navigation Compose for type-safe routing with:
 * - BouncerNavHost for screen destinations
 * - PremiumBottomNavBar for primary tab navigation
 * - Proper back stack management
 *
 * @param viewModel ViewModel for live match data
 * @param newsViewModel ViewModel for news data
 * @param onMatchClick Callback when a match is clicked (shows modal detail)
 * @param onArTrackingRequest Callback to show AR tracking overlay
 */
@Composable
fun MainAppContainer(
    viewModel: LiveActivityViewModel = viewModel(),
    newsViewModel: NewsViewModel = viewModel(),
    onMatchClick: (CricketMatch) -> Unit,
    onArTrackingRequest: () -> Unit = {}
) {
    // Navigation controller - survives configuration changes via rememberNavController
    val navController = rememberNavController()

    // Observe current route for bottom nav state
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Determine if viewModel has live matches for badge
    val currentMatches by viewModel.currentMatches.collectAsState()
    val hasLiveMatches = remember(currentMatches) {
        currentMatches.any { it.isLive }
    }

    // Map current route to bottom nav route (for child routes like settings -> more)
    val bottomNavRoute = remember(currentRoute) {
        getBottomNavRouteForCurrentRoute(currentRoute)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BlackBackground,
                        CardDarkBlue.copy(alpha = 0.2f),
                        BlackBackground
                    )
                )
            )
    ) {
        // Content Area — NavHost handles all screen routing
        BouncerNavHost(
            navController = navController,
            liveActivityViewModel = viewModel,
            newsViewModel = newsViewModel,
            onMatchClick = onMatchClick,
            onArTrackingClick = onArTrackingRequest,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp)  // clearance for bottom nav bar
        )

        // ── Bottom Navigation Bar (v3.0) ──
        PremiumBottomNavBar(
            currentRoute = bottomNavRoute,
            onNavigate = { route ->
                navController.navigateToTab(route)
            },
            hasLiveMatches = hasLiveMatches,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Maps the current navigation route to the appropriate bottom nav route.
 *
 * Child routes (settings, about, devtools, news, news_detail) should highlight
 * the "more" tab, while primary routes map directly.
 */
private fun getBottomNavRouteForCurrentRoute(route: String?): String {
    return when {
        route == null -> BouncerScreen.Home.route
        route in listOf(
            BouncerScreen.Home.route,
            BouncerScreen.Matches.route,
            BouncerScreen.Favorites.route,
            BouncerScreen.Stats.route,
            BouncerScreen.More.route
        ) -> route
        // Child routes of "More" tab
        route in listOf(
            BouncerScreen.Settings.route,
            BouncerScreen.Appearance.route,
            BouncerScreen.About.route,
            BouncerScreen.DevTools.route,
            BouncerScreen.News.route,
            BouncerScreen.Achievements.route,
            BouncerScreen.ArTracking.route,
            BouncerScreen.ActivityFeed.route
        ) -> BouncerScreen.More.route
        // News detail routes (pattern: news/{articleId})
        route.startsWith("news/") -> BouncerScreen.More.route
        // Match-related routes
        route.startsWith("match/") || route.startsWith("match-info/") -> BouncerScreen.Matches.route
        // Player and squad routes
        route.startsWith("player/") -> BouncerScreen.Stats.route
        route.startsWith("squad/") -> BouncerScreen.Matches.route
        // Default fallback
        else -> BouncerScreen.Home.route
    }
}
