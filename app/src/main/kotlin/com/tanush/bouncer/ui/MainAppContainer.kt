package com.tanush.bouncer.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.ui.components.v2.BottomNavBarV2
import com.tanush.bouncer.ui.navigation.BouncerNavHost
import com.tanush.bouncer.ui.navigation.BouncerScreen
import com.tanush.bouncer.ui.navigation.navigateForward
import com.tanush.bouncer.ui.navigation.navigateToTab
import com.tanush.bouncer.ui.screens.*
import com.tanush.bouncer.ui.theme.BouncerMotion
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.ui.theme.v2.ThemeModeV2
import com.tanush.bouncer.utils.PreferencesHelper
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
fun BouncerAppContent(
    externalDeepLinkUri: String? = null
) {
    var currentMatch by remember { mutableStateOf<CricketMatch?>(null) }
    var showArTracking by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var themeMode by remember { mutableStateOf(ThemeModeV2.Dark) }

    LaunchedEffect(Unit) {
        BouncerMotion.applyReducedMotionPreference(
            PreferencesHelper.getBoolean(
                context,
                PreferencesHelper.KEY_REDUCED_MOTION,
                false
            )
        )
    }

    BouncerThemeV2(themeMode = themeMode) {
        BackHandler(enabled = currentMatch != null) { currentMatch = null }
        BackHandler(enabled = showArTracking) { showArTracking = false }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BouncerThemeV2.colors.background)
        ) {
            SharedTransitionLayout {
                AnimatedContent(
                    targetState = currentMatch,
                    transitionSpec = {
                        if (targetState != null) {
                            BouncerMotion.Transitions.verticalEnter(offsetDivisor = 10)
                                .togetherWith(fadeOut(BouncerMotion.Specs.FadeOut))
                        } else {
                            fadeIn(BouncerMotion.Specs.FadeIn) togetherWith
                                BouncerMotion.Transitions.verticalExit(offsetDivisor = 10)
                        }
                    },
                    label = "rootNavigation"
                ) { match ->
                    if (match != null) {
                        Box(
                            modifier = Modifier
                                .sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "match_container_${match.id}"),
                                    animatedVisibilityScope = this@AnimatedContent
                                )
                                .graphicsLayer { clip = true }
                        ) {
                            MatchDetailViewV2(
                                match = match,
                                onBackClick = { currentMatch = null }
                            )
                        }
                    } else {
                        MainAppContainer(
                            onMatchClick = { clickedMatch ->
                                currentMatch = clickedMatch
                            },
                            onArTrackingRequest = { showArTracking = true },
                            externalDeepLinkUri = externalDeepLinkUri,
                            themeMode = themeMode,
                            onThemeModeChanged = { themeMode = it }
                        )
                    }
                }
            }

            // AR Overlay Layer
            AnimatedVisibility(
                visible = showArTracking,
                enter = BouncerMotion.Transitions.scaleFadeIn(initialScale = 0.9f),
                exit = BouncerMotion.Transitions.scaleFadeOut(targetScale = 0.9f)
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
    onArTrackingRequest: () -> Unit = {},
    externalDeepLinkUri: String? = null,
    themeMode: ThemeModeV2 = ThemeModeV2.Dark,
    onThemeModeChanged: (ThemeModeV2) -> Unit = {}
) {
    // Navigation controller - survives configuration changes via rememberNavController
    val navController = rememberNavController()
    var consumedDeepLink by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val isOnboardingComplete = remember {
        PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_ONBOARDING_COMPLETE, false)
            || FavouriteTeamsManager.favourites.value.isNotEmpty()
    }

    // Observe current route for bottom nav state
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Determine if viewModel has live matches for badge
    val currentMatches by viewModel.currentMatches.collectAsStateWithLifecycle()

    LaunchedEffect(externalDeepLinkUri, currentMatches) {
        val incomingLink = externalDeepLinkUri?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (consumedDeepLink == incomingLink) return@LaunchedEffect

        val consumed = handleExternalDeepLink(
            deepLinkUri = incomingLink,
            navController = navController,
            availableMatches = currentMatches,
            onMatchClick = onMatchClick
        )
        if (consumed) {
            consumedDeepLink = incomingLink
        }
    }

    // Map current route to bottom nav route (for child routes like settings -> more)
    val bottomNavRoute = remember(currentRoute) {
        getBottomNavRouteForCurrentRoute(currentRoute)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BouncerThemeV2.colors.background)
    ) {
        // Content Area — NavHost handles all screen routing
        BouncerNavHost(
            navController = navController,
            liveActivityViewModel = viewModel,
            newsViewModel = newsViewModel,
            onMatchClick = onMatchClick,
            onArTrackingClick = onArTrackingRequest,
            isOnboardingComplete = isOnboardingComplete,
            themeMode = themeMode,
            onThemeModeChanged = onThemeModeChanged,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp)
        )

        // Bottom Navigation Bar (V2)
        BottomNavBarV2(
            currentRoute = bottomNavRoute,
            onTabSelected = { route ->
                navController.navigateToTab(route)
            },
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
    return BouncerScreen.getParentTab(route)
}

private fun handleExternalDeepLink(
    deepLinkUri: String,
    navController: NavHostController,
    availableMatches: List<CricketMatch>,
    onMatchClick: (CricketMatch) -> Unit
): Boolean {
    val parsedUri = runCatching { Uri.parse(deepLinkUri) }.getOrNull() ?: return false

    if (parsedUri.scheme != "app" || parsedUri.host != "bouncer") {
        return false
    }

    val pathSegments = parsedUri.pathSegments.filter { it.isNotBlank() }
    val topLevel = pathSegments.firstOrNull()?.lowercase() ?: "home"

    when (topLevel) {
        "home" -> navController.navigateToTab(BouncerScreen.Home.route)
        "matches", "all_matches" -> navController.navigateToTab(BouncerScreen.Matches.route)
        "favorites", "favourites" -> navController.navigateToTab(BouncerScreen.Favorites.route)
        "stats" -> navController.navigateForward(BouncerScreen.Stats.route)
        "more" -> navController.navigateToTab(BouncerScreen.More.route)
        "settings" -> navController.navigateForward(BouncerScreen.Settings.route)
        "appearance" -> navController.navigateForward(BouncerScreen.Appearance.route)
        "about" -> navController.navigateForward(BouncerScreen.About.route)
        "news" -> navController.navigateToTab(BouncerScreen.News.route)
        "activity" -> navController.navigateForward(BouncerScreen.ActivityFeed.route)
        "ar", "ar-tracking" -> navController.navigateForward(BouncerScreen.ArTracking.route)
        "match" -> {
            val deepLinkMatchId = pathSegments.getOrNull(1) ?: parsedUri.getQueryParameter("matchId")
            if (deepLinkMatchId != null) {
                navController.navigateForward(BouncerScreen.MatchDetail.createRoute(deepLinkMatchId))
            } else {
                navController.navigateToTab(BouncerScreen.Matches.route)
            }
        }
        else -> return false
    }

    return true
}
