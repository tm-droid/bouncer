package com.tanush.bouncer.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.HomeScreen
import com.tanush.bouncer.ui.devtools.DevToolsScreen
import com.tanush.bouncer.ui.screens.*
import com.tanush.bouncer.ui.theme.BackgroundSkinType
import com.tanush.bouncer.ui.theme.BouncerMotion
import com.tanush.bouncer.ui.theme.ThemeVariant
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import com.tanush.bouncer.viewmodels.NewsViewModel

// ═══════════════════════════════════════════════════════════════════════════════
// NavHost Composable
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Main navigation host for the Bouncer app.
 *
 * Sets up all navigation destinations with proper type-safe routing,
 * animations, and argument handling.
 *
 * @param navController The NavHostController to use for navigation
 * @param liveActivityViewModel ViewModel for live match data
 * @param newsViewModel ViewModel for news data
 * @param onMatchClick Callback when a match card is clicked (for modal detail view)
 * @param onArTrackingClick Callback to show AR tracking overlay
 * @param modifier Modifier for the NavHost
 */
@Composable
fun BouncerNavHost(
    navController: NavHostController,
    liveActivityViewModel: LiveActivityViewModel,
    newsViewModel: NewsViewModel,
    onMatchClick: (CricketMatch) -> Unit,
    onArTrackingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = BouncerScreen.Home.route,
        modifier = modifier.fillMaxSize(),
        enterTransition = { BouncerMotion.Transitions.horizontalEnter() },
        exitTransition = { BouncerMotion.Transitions.horizontalExit() },
        popEnterTransition = { BouncerMotion.Transitions.horizontalPopEnter() },
        popExitTransition = { BouncerMotion.Transitions.horizontalPopExit() }
    ) {
        // Primary tabs
        primaryTabDestinations(navController, liveActivityViewModel, onMatchClick)

        // Secondary screens from More tab
        moreChildDestinations(navController, newsViewModel, onArTrackingClick)

        // Parameterized screens
        parameterizedDestinations(navController, newsViewModel)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Primary Tab Destinations
// ═══════════════════════════════════════════════════════════════════════════════

private fun NavGraphBuilder.primaryTabDestinations(
    navController: NavHostController,
    liveActivityViewModel: LiveActivityViewModel,
    onMatchClick: (CricketMatch) -> Unit
) {
    // Home Tab
    composable(BouncerScreen.Home.route) {
        HomeScreen(
            viewModel = liveActivityViewModel,
            onMatchClick = onMatchClick,
            onMoreMatchesClick = {
                navController.navigateToTab(BouncerScreen.Matches.route)
            },
            onAchievementsClick = {
                navController.navigateForward(BouncerScreen.Stats.route)
            }
        )
    }

    // All Matches Tab
    composable(BouncerScreen.Matches.route) {
        AllMatchesScreen(
            viewModel = liveActivityViewModel,
            onBackClick = {
                navController.navigateToTab(BouncerScreen.Home.route)
            },
            onMatchClick = onMatchClick
        )
    }

    // Favourites Tab
    composable(BouncerScreen.Favorites.route) {
        FavoritesScreen(
            onBackClick = {
                navController.navigateToTab(BouncerScreen.Home.route)
            },
            onTeamClick = { teamName ->
                // Team detail navigation (future)
                // navController.navigate(BouncerScreen.Squad.createRoute(teamName))
            }
        )
    }

    // Stats/Trophy Room Tab -> Stats Hub
    composable(BouncerScreen.Stats.route) {
        StatsHubScreen(
            onBackClick = {
                if (!navController.popBackStack()) {
                    navController.navigateToTab(BouncerScreen.Home.route)
                }
            },
            onOpenTrophy = {
                navController.navigateForward(BouncerScreen.Trophy.route)
            },
            onOpenAnalytics = {
                navController.navigateForward(BouncerScreen.StatsOverview.route)
            },
            onOpenAchievements = {
                navController.navigateForward(BouncerScreen.Achievements.route)
            }
        )
    }

    // More Tab
    composable(BouncerScreen.More.route) {
        MoreScreen(
            onSettingsClick = {
                navController.navigateForward(BouncerScreen.Settings.route)
            },
            onStatsClick = {
                navController.navigateForward(BouncerScreen.Stats.route)
            },
            onAchievementsClick = {
                navController.navigateForward(BouncerScreen.Achievements.route)
            },
            onActivityClick = {
                navController.navigateForward(BouncerScreen.ActivityFeed.route)
            },
            onArTrackingClick = {
                navController.navigateForward(BouncerScreen.ArTracking.route)
            },
            onAboutClick = {
                navController.navigateForward(BouncerScreen.About.route)
            },
            onDevToolsClick = {
                navController.navigateForward(BouncerScreen.DevTools.route)
            },
            onNewsClick = {
                navController.navigateForward(BouncerScreen.News.route)
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// More Tab Child Destinations
// ═══════════════════════════════════════════════════════════════════════════════

private fun NavGraphBuilder.moreChildDestinations(
    navController: NavHostController,
    newsViewModel: NewsViewModel,
    onArTrackingClick: () -> Unit
) {
    // Settings
    composable(BouncerScreen.Settings.route) {
        SettingsScreen(
            onBackClick = { navController.popBackStack() },
            onFavoritesClick = {
                navController.navigateToTab(BouncerScreen.Favorites.route)
            },
            onAchievementsClick = {
                navController.navigateForward(BouncerScreen.Stats.route)
            },
            onAboutClick = {
                navController.navigateForward(BouncerScreen.About.route)
            },
            onAppearanceClick = {
                navController.navigateForward(BouncerScreen.Appearance.route)
            }
        )
    }

    // Appearance customization
    composable(BouncerScreen.Appearance.route) {
        val selectedVariant by remember { mutableStateOf(ThemeVariant.Dark) }
        val selectedSkin by remember { mutableStateOf(BackgroundSkinType.MIDNIGHT_GRADIENT) }
        AppearanceScreen(
            onBackClick = { navController.popBackStack() },
            currentThemeVariant = selectedVariant,
            currentBackgroundSkin = selectedSkin,
            onThemeVariantChanged = { },
            onBackgroundSkinChanged = { }
        )
    }

    // About
    composable(BouncerScreen.About.route) {
        AboutScreen(
            onBackClick = { navController.popBackStack() }
        )
    }

    // DevTools
    composable(BouncerScreen.DevTools.route) {
        DevToolsScreen(
            onBackClick = { navController.popBackStack() }
        )
    }

    // News List
    composable(BouncerScreen.News.route) {
        NewsScreen(
            onBackClick = { navController.popBackStack() },
            newsViewModel = newsViewModel,
            onArticleClick = { article ->
                newsViewModel.selectArticle(article)
                navController.navigateForward(BouncerScreen.NewsDetail.createRoute(article.id.toString()))
            }
        )
    }

    // Achievements (standalone screen)
    composable(BouncerScreen.Achievements.route) {
        // For now, pass empty set - this should be connected to a ViewModel
        AchievementsScreen(
            unlockedAchievements = emptySet(),
            onBackClick = { navController.popBackStack() }
        )
    }

    // Trophy Room (legacy access) - reachable from Stats Hub
    composable(BouncerScreen.Trophy.route) {
        TrophyRoomScreen(
            onBackClick = { navController.popBackStack() }
        )
    }

    // Extended Stats Overview (detailed analytics)
    composable(BouncerScreen.StatsOverview.route) {
        StatsOverviewScreen(onBackClick = { navController.popBackStack() })
    }

    // AR Tracking (uses overlay pattern)
    composable(BouncerScreen.ArTracking.route) {
        // Navigate back and trigger overlay
        LaunchedEffect(Unit) {
            navController.popBackStack()
            onArTrackingClick()
        }
    }

    // Activity Feed / Intelligence
    composable(BouncerScreen.ActivityFeed.route) {
        IntelligenceFeedScreen(
            onBackClick = { navController.popBackStack() }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Parameterized Destinations
// ═══════════════════════════════════════════════════════════════════════════════

private fun NavGraphBuilder.parameterizedDestinations(
    navController: NavHostController,
    newsViewModel: NewsViewModel
) {
    // News Detail with article ID
    composable(
        route = BouncerScreen.NewsDetail.ROUTE_PATTERN,
        arguments = BouncerScreen.NewsDetail.arguments
    ) { backStackEntry ->
        val articleId = backStackEntry.arguments?.getString(BouncerScreen.NewsDetail.ARG_ARTICLE_ID)
            ?.decodeUrlSafe() ?: return@composable

        // Get article from ViewModel's selected state or fetch by ID
        val selectedArticle by newsViewModel.selectedArticle.collectAsState()

        selectedArticle?.let { article ->
            NewsDetailScreen(
                article = article,
                newsViewModel = newsViewModel,
                onBackClick = {
                    newsViewModel.clearSelectedArticle()
                    navController.popBackStack()
                }
            )
        } ?: run {
            // Article not in state - navigate back
            LaunchedEffect(Unit) {
                navController.popBackStack()
            }
        }
    }

    // Player Detail with player ID
    // NOTE: The actual PlayerDetailScreen takes individual params (playerName, playerRole, etc.)
    // In a full implementation, this would fetch player data from a ViewModel by ID
    composable(
        route = BouncerScreen.PlayerDetail.ROUTE_PATTERN,
        arguments = BouncerScreen.PlayerDetail.arguments
    ) { backStackEntry ->
        val playerId = backStackEntry.arguments?.getString(BouncerScreen.PlayerDetail.ARG_PLAYER_ID)
            ?.decodeUrlSafe() ?: return@composable

        // PlayerDetailScreen expects individual parameters - this is a placeholder
        // In a full implementation, you would fetch player data from a ViewModel
        PlayerDetailScreen(
            playerName = playerId, // Using ID as name placeholder
            playerRole = "Player",
            playerImageUrl = null,
            isCaptain = false,
            isWicketKeeper = false,
            onBackClick = { navController.popBackStack() }
        )
    }

    // NOTE: MatchInfoScreen and SquadScreen require CricketMatch objects, not just IDs.
    // These are currently handled via the modal overlay pattern in BouncerAppContent.
    // To enable ID-based navigation, the screens would need to be refactored to
    // accept an ID and load the match from a ViewModel/repository.
}

// ═══════════════════════════════════════════════════════════════════════════════
// Navigation Extension Functions
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Navigate to a primary tab, clearing the back stack to avoid deep stacking.
 */
fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        // Pop up to the start destination to avoid building up a large stack
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
        // Avoid multiple copies of the same destination
        launchSingleTop = true
        // Restore state when re-selecting a previously selected tab
        restoreState = true
    }
}

/**
 * Navigate forward to a detail screen, adding to back stack.
 */
fun NavHostController.navigateForward(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}

/**
 * Navigate to a screen and clear everything above it in the back stack.
 */
fun NavHostController.navigateAndClearStack(route: String) {
    navigate(route) {
        popUpTo(0) {
            inclusive = true
        }
        launchSingleTop = true
    }
}
