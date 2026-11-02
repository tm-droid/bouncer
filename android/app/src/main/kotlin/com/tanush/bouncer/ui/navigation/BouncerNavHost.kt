package com.tanush.bouncer.ui.navigation

import com.tanush.bouncer.R
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import com.tanush.bouncer.utils.PreferencesHelper
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanush.bouncer.BuildConfig
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.HomeScreenV2
import com.tanush.bouncer.ui.MatchDetailViewV2
import com.tanush.bouncer.ui.devtools.DevToolsScreen
import com.tanush.bouncer.ui.screens.*
import com.tanush.bouncer.ui.screens.news.NewsDetailScreenV2
import com.tanush.bouncer.ui.screens.player.HistoricalPlayerScreen
import com.tanush.bouncer.ui.screens.news.NewsScreenV2
import com.tanush.bouncer.ui.theme.BouncerMotion
import com.tanush.bouncer.ui.theme.v2.ThemeModeV2
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import com.tanush.bouncer.viewmodels.SearchTab
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
 * @param modifier Modifier for the NavHost
 */
@Composable
fun BouncerNavHost(
    navController: NavHostController,
    liveActivityViewModel: LiveActivityViewModel,
    newsViewModel: NewsViewModel,
    onMatchClick: (CricketMatch) -> Unit,
    isOnboardingComplete: Boolean = true,
    themeMode: ThemeModeV2 = ThemeModeV2.System,
    onThemeModeChanged: (ThemeModeV2) -> Unit = {},
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = if (isOnboardingComplete) BouncerScreen.Home.route else BouncerScreen.Onboarding.route,
        modifier = modifier.fillMaxSize(),
        enterTransition = { BouncerMotion.Transitions.horizontalEnter() },
        exitTransition = { BouncerMotion.Transitions.horizontalExit() },
        popEnterTransition = { BouncerMotion.Transitions.horizontalPopEnter() },
        popExitTransition = { BouncerMotion.Transitions.horizontalPopExit() }
    ) {
        // Onboarding — only shown on first launch
        composable(
            BouncerScreen.Onboarding.route,
            enterTransition = { fadeIn(BouncerMotion.Specs.FadeIn) },
            exitTransition = { fadeOut(BouncerMotion.Specs.FadeOut) },
            popEnterTransition = { fadeIn(BouncerMotion.Specs.FadeIn) },
            popExitTransition = { fadeOut(BouncerMotion.Specs.FadeOut) },
        ) {
            OnboardingScreenV2(
                onComplete = { navController.navigateAndClearStack(BouncerScreen.Home.route) }
            )
        }

        // Primary tabs
        primaryTabDestinations(navController, liveActivityViewModel, newsViewModel, onMatchClick, themeMode, onThemeModeChanged)

        // Secondary screens from More tab
        moreChildDestinations(navController, liveActivityViewModel, newsViewModel, onMatchClick)

        // Pages opened from any tab: comparison and Alerts
        sharedDestinations(navController, liveActivityViewModel, onMatchClick)

        // Parameterized screens
        parameterizedDestinations(navController, newsViewModel, liveActivityViewModel, onMatchClick)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Primary Tab Destinations
// ═══════════════════════════════════════════════════════════════════════════════

private fun NavGraphBuilder.primaryTabDestinations(
    navController: NavHostController,
    liveActivityViewModel: LiveActivityViewModel,
    newsViewModel: NewsViewModel,
    onMatchClick: (CricketMatch) -> Unit,
    themeMode: ThemeModeV2,
    onThemeModeChanged: (ThemeModeV2) -> Unit,
) {
    // Home Tab
    composable(
        BouncerScreen.Home.route,
        enterTransition = { BouncerMotion.Transitions.tabEnter() },
        exitTransition = { BouncerMotion.Transitions.tabExit() },
        popEnterTransition = { BouncerMotion.Transitions.tabEnter() },
        popExitTransition = { BouncerMotion.Transitions.tabExit() }
    ) {
        HomeScreenV2(
            viewModel = liveActivityViewModel,
            onMatchClick = onMatchClick,
            onSearchClick = {
                navController.navigateForward(BouncerScreen.Search.route)
            },
            onNotificationsClick = {
                navController.navigateForward(BouncerScreen.Notifications.route)
            },
        )
    }

    // Series Tab
    composable(
        BouncerScreen.SeriesList.route,
        enterTransition = { BouncerMotion.Transitions.tabEnter() },
        exitTransition = { BouncerMotion.Transitions.tabExit() },
        popEnterTransition = { BouncerMotion.Transitions.tabEnter() },
        popExitTransition = { BouncerMotion.Transitions.tabExit() }
    ) {
        SeriesListScreen(
            onSeriesClick = { seriesId ->
                navController.navigateForward(BouncerScreen.SeriesDetail.createRoute(seriesId))
            },
            onSearchClick = {
                navController.navigateForward(BouncerScreen.Search.createRoute(SearchTab.TOURNAMENTS.name))
            },
        )
    }

    // News Tab (primary tab in V2)
    composable(
        BouncerScreen.News.route,
        enterTransition = { BouncerMotion.Transitions.tabEnter() },
        exitTransition = { BouncerMotion.Transitions.tabExit() },
        popEnterTransition = { BouncerMotion.Transitions.tabEnter() },
        popExitTransition = { BouncerMotion.Transitions.tabExit() }
    ) {
        LaunchedEffect(Unit) { newsViewModel.ensureLoaded() }
        NewsScreenV2(
            viewModel = newsViewModel,
            onArticleClick = { article ->
                newsViewModel.selectArticle(article)
                navController.navigateForward(BouncerScreen.NewsDetail.createRoute(article.id.toString()))
            },
            onSearchClick = {
                navController.navigateForward(BouncerScreen.Search.createRoute(SearchTab.NEWS.name))
            },
        )
    }

    moreTabDestinations(navController, themeMode, onThemeModeChanged)
}


// ═══════════════════════════════════════════════════════════════════════════════
// More Tab Child Destinations
// ═══════════════════════════════════════════════════════════════════════════════

private fun NavGraphBuilder.moreChildDestinations(
    navController: NavHostController,
    liveActivityViewModel: LiveActivityViewModel,
    newsViewModel: NewsViewModel,
    onMatchClick: (CricketMatch) -> Unit,
) {
    // Search
    composable(
        route = BouncerScreen.Search.ROUTE_PATTERN,
        arguments = BouncerScreen.Search.arguments,
    ) { backStackEntry ->
        // Every cached match, not Home's filtered list: search must find a women's fixture or
        // last week's result whichever filter Home was left on.
        val allMatches by liveActivityViewModel.allMatches.collectAsStateWithLifecycle()
        val news by newsViewModel.articles.collectAsStateWithLifecycle()
        val initialTab = backStackEntry.arguments?.getString(BouncerScreen.Search.ARG_TAB)
            ?.let { tab -> SearchTab.entries.firstOrNull { it.name == tab } }
        LaunchedEffect(Unit) {
            liveActivityViewModel.warmAllStatuses()
            newsViewModel.ensureLoaded()
        }

        SearchScreen(
            matches = allMatches,
            news = news,
            initialTab = initialTab,
            onMatchClick = { match ->
                onMatchClick(match)
            },
            onPlayerClick = { playerName, cricbuzzId ->
                navController.navigateForward(
                    BouncerScreen.PlayerDetail.createRoute(playerName, cricbuzzId)
                )
            },
            onHistoryPlayerClick = { id, name -> navController.navigateToHistoryPlayer(id, name) },
            onTeamClick = { teamName ->
                navController.navigateForward(
                    BouncerScreen.TeamDetail.createRoute(teamName)
                )
            },
            onSeriesClick = { seriesId ->
                navController.navigateForward(
                    BouncerScreen.SeriesDetail.createRoute(seriesId)
                )
            },
            onNewsClick = { article ->
                newsViewModel.selectArticle(article)
                navController.navigateForward(
                    BouncerScreen.NewsDetail.createRoute(article.id.toString())
                )
            },
            onBackClick = { navController.popBackStack() }
        )
    }

    // About
    composable(BouncerScreen.About.route) {
        AboutScreenV2(
            onBackClick = { navController.popBackStack() }
        )
    }

    // DevTools — debug builds only. It was in the release graph and reachable from the More tab,
    // fronted by an "Echo" control wired to a stub MCP client that returned a canned string and
    // pointed at http://localhost:8080. The stub is gone; the real diagnostics stay behind here.
    if (BuildConfig.DEBUG) {
        composable(BouncerScreen.DevTools.route) {
            DevToolsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }

}

// ═══════════════════════════════════════════════════════════════════════════════
// Parameterized Destinations
// ═══════════════════════════════════════════════════════════════════════════════

private fun NavGraphBuilder.parameterizedDestinations(
    navController: NavHostController,
    newsViewModel: NewsViewModel,
    liveActivityViewModel: LiveActivityViewModel,
    onMatchClick: (com.tanush.bouncer.models.CricketMatch) -> Unit,
) {
    detailDestinations(navController, newsViewModel)
    matchDeepLinkDestination(navController, liveActivityViewModel)
    entityDestinations(navController, liveActivityViewModel, onMatchClick)
}

private fun NavGraphBuilder.detailDestinations(
    navController: NavHostController,
    newsViewModel: NewsViewModel,
) {
    // News Detail with article ID
    composable(
        route = BouncerScreen.NewsDetail.ROUTE_PATTERN,
        arguments = BouncerScreen.NewsDetail.arguments
    ) { backStackEntry ->
        val articleId = backStackEntry.arguments?.getString(BouncerScreen.NewsDetail.ARG_ARTICLE_ID)
            ?.decodeUrlSafe() ?: return@composable

        val selectedArticle by newsViewModel.selectedArticle.collectAsStateWithLifecycle()
        // The id in the route was read and never used: a route restored after process death had
        // no selection in memory and popped straight back. It now finds the story by that id.
        val unavailable by produceState(initialValue = false, articleId) {
            value = articleId.toLongOrNull()?.let { newsViewModel.restoreArticle(it) } != true
        }

        when {
            selectedArticle != null -> NewsDetailScreenV2(
                viewModel = newsViewModel,
                onBack = {
                    newsViewModel.clearSelectedArticle()
                    navController.popBackStack()
                }
            )
            unavailable -> LaunchedEffect(Unit) { navController.popBackStack() }
        }
    }

    // Player Detail with player ID
    composable(
        route = BouncerScreen.PlayerDetail.ROUTE_PATTERN,
        arguments = BouncerScreen.PlayerDetail.arguments
    ) { backStackEntry ->
        val playerId = backStackEntry.arguments?.getString(BouncerScreen.PlayerDetail.ARG_PLAYER_ID)
            ?.decodeUrlSafe() ?: return@composable
        val cricbuzzId = backStackEntry.arguments?.getString(BouncerScreen.PlayerDetail.ARG_CRICBUZZ_ID)
            ?.decodeUrlSafe()

        PlayerDetailScreenV2(
            playerName = playerId,
            playerRole = "Player",
            playerImageUrl = null,
            isCaptain = false,
            isWicketKeeper = false,
            onBackClick = { navController.popBackStack() },
            onCompareClick = { navController.navigateForward(BouncerScreen.PlayerComparison.createRoute(playerId)) },
            cricbuzzId = cricbuzzId,
            onHistoryCareerClick = { id, name -> navController.navigateToHistoryPlayer(id, name) },
        )
    }

}

// Match Detail with match ID — used by widget/notification deep links
private fun NavGraphBuilder.matchDeepLinkDestination(
    navController: NavHostController,
    liveActivityViewModel: LiveActivityViewModel,
) {
    composable(
        route = BouncerScreen.MatchDetail.ROUTE_PATTERN,
        arguments = BouncerScreen.MatchDetail.arguments
    ) { backStackEntry ->
        val rawMatchId = backStackEntry.arguments?.getString(BouncerScreen.MatchDetail.ARG_MATCH_ID)
            ?.decodeUrlSafe()
        // Reject blank or implausibly long IDs (deep-link injection guard)
        val matchId = rawMatchId?.takeIf { it.isNotBlank() && it.length <= 64 }
            ?: run {
                LaunchedEffect(Unit) { navController.navigateToTab(BouncerScreen.Home.route) }
                return@composable
            }

        val matches by liveActivityViewModel.currentMatches.collectAsStateWithLifecycle()
        val isLoading by liveActivityViewModel.isLoading.collectAsStateWithLifecycle()
        // Resolve across all cached statuses, not just the active tab — a deep link / notification
        // tap carries only an id and is independent of the selected tab or type/gender filter.
        val match = matches.firstOrNull { it.id == matchId }
            ?: liveActivityViewModel.findMatchById(matchId)

        when {
            match != null -> CompositionLocalProvider(
                LocalOpenRoute provides { route: String -> navController.navigateForward(route) },
            ) {
                MatchDetailViewV2(
                    match = match,
                    onBackClick = { navController.popBackStack() }
                )
            }
            // Still fetching: the id may yet arrive. Show a loader instead of bouncing out
            // (cold notification/widget launch hits this before the first fetch completes).
            isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            // Fetch done and the id resolved nowhere — genuinely unknown match. Said, as the
            // external deep-link path already does, rather than a silent jump to Home.
            else -> {
                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    android.widget.Toast.makeText(
                        context, R.string.deep_link_match_unavailable, android.widget.Toast.LENGTH_SHORT
                    ).show()
                    navController.navigateToTab(BouncerScreen.Home.route)
                }
            }
        }
    }
}

private fun NavGraphBuilder.entityDestinations(
    navController: NavHostController,
    liveActivityViewModel: LiveActivityViewModel,
    onMatchClick: (com.tanush.bouncer.models.CricketMatch) -> Unit,
) {
    // Cricsheet career page, for players Cricbuzz does not list
    composable(
        route = BouncerScreen.HistoryPlayer.ROUTE_PATTERN,
        arguments = BouncerScreen.HistoryPlayer.arguments,
    ) { backStackEntry ->
        val id = backStackEntry.arguments?.getString(BouncerScreen.HistoryPlayer.ARG_ID)
            ?.decodeUrlSafe() ?: return@composable
        val name = backStackEntry.arguments?.getString(BouncerScreen.HistoryPlayer.ARG_NAME)?.decodeUrlSafe()
        HistoricalPlayerScreen(
            playerId = id,
            displayName = name,
            onBackClick = { navController.popBackStack() },
            onMatchClick = onMatchClick,
            onLiveProfileClick = { cricbuzzId ->
                navController.navigateForward(BouncerScreen.PlayerDetail.createRoute(name.orEmpty(), cricbuzzId))
            },
        )
    }

    // Series Detail with series ID
    composable(
        route = BouncerScreen.SeriesDetail.ROUTE_PATTERN,
        arguments = BouncerScreen.SeriesDetail.arguments
    ) { backStackEntry ->
        val seriesId = backStackEntry.arguments?.getString(BouncerScreen.SeriesDetail.ARG_SERIES_ID)
            ?.decodeUrlSafe() ?: return@composable
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

        SeriesDetailScreen(
            seriesId = seriesId,
            onMatchClick = onMatchClick,
            onPlayerClick = { name, cricbuzzId ->
                navController.navigateForward(BouncerScreen.PlayerDetail.createRoute(name, cricbuzzId))
            },
            onTeamClick = { name -> navController.navigateForward(BouncerScreen.TeamDetail.createRoute(name)) },
            // Through the dispatcher, not popBackStack: a series opened from a match's series link
            // returns to that match, and the arrow must take the same path as the system back.
            onBackClick = {
                if (backDispatcher != null) backDispatcher.onBackPressed() else navController.popBackStack()
            }
        )
    }

    // Team Detail
    composable(
        route = BouncerScreen.TeamDetail.ROUTE_PATTERN,
        arguments = BouncerScreen.TeamDetail.arguments,
    ) { backStackEntry ->
        val teamName = backStackEntry.arguments
            ?.getString(BouncerScreen.TeamDetail.ARG_TEAM_NAME)
            ?.decodeUrlSafe() ?: return@composable

        TeamDetailScreen(
            teamName = teamName,
            liveActivityViewModel = liveActivityViewModel,
            onMatchClick = onMatchClick,
            onPlayerClick = { name, cricbuzzId ->
                navController.navigateForward(BouncerScreen.PlayerDetail.createRoute(name, cricbuzzId))
            },
            onBackClick = { navController.popBackStack() },
        )
    }
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
