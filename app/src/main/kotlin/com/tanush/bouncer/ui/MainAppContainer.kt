package com.tanush.bouncer.ui

import com.tanush.bouncer.data.AppJson
import androidx.compose.runtime.saveable.Saver
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
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
import com.tanush.bouncer.ui.navigation.LocalOpenSeries
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
 *
 * The MainAppContainer handles all in-app navigation via NavController.
 */
/**
 * Runs [LiveActivityViewModel]'s score polling only while the host screen is started.
 *
 * `stopPolling()` existed with zero callers anywhere in the tree, and `onCleared()` closed the
 * commentary manager without touching the poll loop — so the 6s Live loop ran for the whole life of
 * the Activity, including every minute the app spent backgrounded, fetching matches nothing could
 * render. Appwrite Realtime already gets exactly this treatment via `RealtimeLifecycleController`
 * ("a backgrounded app can render none of it"); the HTTP half never did.
 *
 * START/STOP rather than RESUME/PAUSE, to match the boundary `RealtimeLifecycleController` uses and
 * so a notification shade or a transparent overlay does not kill the loop. `startPolling()` is
 * idempotent and runs its own fast-resume fetch when it was previously stopped, so returning to the
 * foreground costs one catch-up fetch, not a restart.
 */
@Composable
private fun PollWhileStarted(viewModel: LiveActivityViewModel, liveListVisible: Boolean) {
    LifecycleStartEffect(viewModel) {
        viewModel.startPolling()
        onStopOrDispose { viewModel.stopPolling() }
    }
    // Under an open match the poll used to stop outright. It now slows instead: the match screen
    // falls back to the list when its own row stops being written, and a list that never moves
    // gave it nothing to fall back to.
    LaunchedEffect(viewModel, liveListVisible) { viewModel.setLiveListVisible(liveListVisible) }
}

// The routes that show a list of live scores.
private val LIVE_LIST_ROUTES = setOf(BouncerScreen.Home.route, BouncerScreen.Matches.route)

/**
 * The persisted [ThemeModeV2], defaulting to [ThemeModeV2.System] for anyone who has never picked.
 *
 * An unrecognised stored value (a rename, a downgrade) falls back to System rather than throwing —
 * a corrupt preference should cost the user their choice, not the launch.
 */
private fun loadThemeMode(context: android.content.Context): ThemeModeV2 {
    val stored = PreferencesHelper.getString(
        context,
        PreferencesHelper.KEY_THEME_VARIANT,
        ThemeModeV2.System.name,
    )
    return ThemeModeV2.entries.firstOrNull { it.name == stored } ?: ThemeModeV2.System
}

/**
 * The app, or only the update screen when this build is retired (`AppUpdateGate`): a retired
 * build would render screens the backend no longer serves.
 */
@Composable
fun BouncerRoot(
    updateRequired: Boolean,
    onUpdateClick: () -> Unit,
    externalDeepLinkUri: String?,
    onDeepLinkConsumed: () -> Unit,
) {
    if (!updateRequired) {
        BouncerAppContent(externalDeepLinkUri, onDeepLinkConsumed)
        return
    }
    val context = LocalContext.current
    BouncerThemeV2(themeMode = remember { loadThemeMode(context) }) {
        UpdateRequiredScreen(onUpdate = onUpdateClick)
    }
}

@Composable
fun BouncerAppContent(
    externalDeepLinkUri: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    // Saveable: a dark-mode switch, a font-size change or the system reclaiming the process used
    // to drop the reader back on Home with the match they had open gone.
    var currentMatch by rememberSaveable(stateSaver = OpenMatchSaver) { mutableStateOf<CricketMatch?>(null) }
    val context = LocalContext.current
    // The picked theme is persisted. It used to live only in `rememberSaveable`, so it survived a
    // rotation and nothing else: every cold start reset it, and the reset landed on a hard `Dark`
    // rather than `System` — meaning the app ignored the OS setting on every launch until the user
    // re-picked. `KEY_THEME_VARIANT` had been defined in PreferencesHelper all along with nothing
    // reading or writing it.
    //
    // Read synchronously rather than through produceState: this decides the very first frame, and
    // a deferred read would paint one frame of the wrong theme. The prefs file is the same one
    // read a few lines below for onboarding state, so this costs an in-memory map lookup.
    var themeMode by rememberSaveable { mutableStateOf(loadThemeMode(context)) }

    LaunchedEffect(Unit) {
        BouncerMotion.applyReducedMotionPreference(
            PreferencesHelper.getBoolean(
                context,
                PreferencesHelper.KEY_REDUCED_MOTION,
                false
            )
        )
    }

    // Hoisted so the match overlay can navigate the app underneath it (a match's series link).
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    // A series opened from a match's series link, and the match to reopen when that series page
    // is backed out of. Keyed on the back-stack entry, so leaving it any other way (a bottom tab)
    // never matches again and the match stays closed.
    var seriesReturn by remember { mutableStateOf<Pair<String, CricketMatch>?>(null) }

    BouncerThemeV2(themeMode = themeMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BouncerThemeV2.colors.background)
        ) {
            // The app stays composed under an open match. It used to be swapped out for the match
            // screen, which disposed the NavHost and its back stack: closing a match opened from a
            // series, a search or a team landed on Home instead of where the reader was.
            MainAppContainer(
                navController = navController,
                onMatchClick = { currentMatch = it },
                isCovered = currentMatch != null,
                externalDeepLinkUri = externalDeepLinkUri,
                onDeepLinkConsumed = onDeepLinkConsumed,
                themeMode = themeMode,
                onThemeModeChanged = {
                    themeMode = it
                    PreferencesHelper.setString(
                        context,
                        PreferencesHelper.KEY_THEME_VARIANT,
                        it.name,
                    )
                }
            )
            // Composed after the NavHost, so its back callback outranks the NavHost's own.
            BackHandler(enabled = seriesReturn != null && backStackEntry?.id == seriesReturn?.first) {
                val match = seriesReturn?.second
                seriesReturn = null
                navController.popBackStack()
                currentMatch = match
            }
            MatchOverlay(
                match = currentMatch,
                onClose = { currentMatch = null },
                onSeriesClick = { seriesId ->
                    val from = currentMatch
                    currentMatch = null
                    navController.navigateForward(BouncerScreen.SeriesDetail.createRoute(seriesId))
                    val entryId = navController.currentBackStackEntry?.id
                    seriesReturn = if (from != null && entryId != null) entryId to from else null
                },
            )
        }
    }
}

/** The open match, over the app. Keeps the last match on screen while it animates out. */
@Composable
private fun MatchOverlay(
    match: CricketMatch?,
    onClose: () -> Unit,
    onSeriesClick: (String) -> Unit,
) {
    var shown by remember { mutableStateOf(match) }
    if (match != null) shown = match
    AnimatedVisibility(
        visible = match != null,
        enter = BouncerMotion.Transitions.verticalEnter(offsetDivisor = 10),
        exit = BouncerMotion.Transitions.verticalExit(offsetDivisor = 10),
    ) {
        val visibleMatch = currentListEntry(shown ?: return@AnimatedVisibility)
        // Registered after the NavHost's own back callback, so back closes the match first.
        BackHandler(enabled = match != null, onBack = onClose)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BouncerThemeV2.colors.background)
        ) {
            key(visibleMatch.id) {
                CompositionLocalProvider(LocalOpenSeries provides onSeriesClick) {
                    MatchDetailViewV2(match = visibleMatch, onBackClick = onClose)
                }
            }
        }
    }
}

/**
 * The list's current entry for an open match. The overlay used to hold the entry as it was when
 * tapped, so `MatchDetailViewModel.onMatchUpdated` ran once and the list could never correct a
 * detail row that had stopped being written, nor mark the match finished.
 */
@Composable
private fun currentListEntry(opened: CricketMatch, viewModel: LiveActivityViewModel = viewModel()): CricketMatch {
    val all by viewModel.allMatches.collectAsStateWithLifecycle()
    return remember(all, opened) { all.firstOrNull { it.id == opened.id } ?: opened }
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
 */
@Composable
fun MainAppContainer(
    viewModel: LiveActivityViewModel = viewModel(),
    newsViewModel: NewsViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),
    onMatchClick: (CricketMatch) -> Unit,
    /** A match is open over the app; the list poll slows while the list cannot be seen. */
    isCovered: Boolean = false,
    externalDeepLinkUri: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    themeMode: ThemeModeV2 = ThemeModeV2.System,
    onThemeModeChanged: (ThemeModeV2) -> Unit = {}
) {
    val context = LocalContext.current
    val isOnboardingComplete = remember {
        PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_ONBOARDING_COMPLETE, false)
            || FavouriteTeamsManager.favourites.value.isNotEmpty()
    }

    // Observe current route for bottom nav state
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    PollWhileStarted(viewModel, liveListVisible = !isCovered && currentRoute in LIVE_LIST_ROUTES)

    HandleDeepLink(externalDeepLinkUri, viewModel, navController, onMatchClick, onDeepLinkConsumed)

    // Map current route to bottom nav route (for child routes like settings -> more)
    val bottomNavRoute = remember(currentRoute) {
        getBottomNavRouteForCurrentRoute(currentRoute)
    }
    val showBottomNav = currentRoute != BouncerScreen.Onboarding.route

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
            isOnboardingComplete = isOnboardingComplete,
            themeMode = themeMode,
            onThemeModeChanged = onThemeModeChanged,
            modifier = Modifier
                .fillMaxSize()
                .then(if (showBottomNav) Modifier.padding(bottom = 80.dp) else Modifier)
        )

        // Bottom Navigation Bar (V2)
        if (showBottomNav) {
            BottomNavBarV2(
                currentRoute = bottomNavRoute,
                onTabSelected = { route ->
                    navController.navigateToTab(route)
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
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

/** The open match, as JSON. A null match saves nothing and restores to the initial null. */
private val OpenMatchSaver = Saver<CricketMatch?, String>(
    save = { match -> match?.let { AppJson.encodeToString(CricketMatch.serializer(), it) } },
    restore = { json -> runCatching { AppJson.decodeFromString(CricketMatch.serializer(), json) }.getOrNull() },
)
