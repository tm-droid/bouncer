package com.tanush.bouncer

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tanush.bouncer.liveactivity.LiveActivityNotificationBuilder
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.ui.components.AppUpdateDialog
import com.tanush.bouncer.ui.components.BouncerBottomNavBar
import com.tanush.bouncer.ui.components.InAppBanner
import com.tanush.bouncer.ui.screens.AnalystFeedScreen
import com.tanush.bouncer.ui.screens.BouncerFavouritesScreen
import com.tanush.bouncer.ui.screens.BouncerHomeScreen
import com.tanush.bouncer.ui.screens.BouncerMatchDetailScreen
import com.tanush.bouncer.ui.screens.BouncerSettingsScreen
import com.tanush.bouncer.ui.screens.NewsScreen
import com.tanush.bouncer.ui.screens.NewsDetailScreen
import com.tanush.bouncer.ui.screens.OnboardingScreen
import com.tanush.bouncer.ui.screens.SplashScreen
import com.tanush.bouncer.ui.theme.BgBase
import com.tanush.bouncer.ui.theme.BouncerTheme
import com.tanush.bouncer.ui.theme.Gold400
import com.tanush.bouncer.ui.updates.AppUpdateManager
import com.tanush.bouncer.ui.updates.AppUpdateUiState
import com.tanush.bouncer.ui.updates.UpdateCheckService
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import com.tanush.bouncer.viewmodels.NewsViewModel
import com.tanush.bouncer.ui.screens.ArBallTrackingScreen
import com.tanush.bouncer.ui.screens.IntelligenceFeedScreen
import com.tanush.bouncer.ui.screens.McpPredictiveForecastScreen
import com.tanush.bouncer.ui.screens.MoreScreen
import com.tanush.bouncer.ui.screens.NotificationSettingsScreen
import com.tanush.bouncer.ui.screens.StatsOverviewScreen
import com.tanush.bouncer.ui.screens.TrophyRoomScreen
import com.tanush.bouncer.services.LiveNotificationManager
import io.sentry.Sentry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main entry point for the Bouncer cricket app.
 *
 * Uses Jetpack Compose with Material 3, edge-to-edge display, and a
 * bottom-navigation–driven single-Activity architecture.
 */
class BouncerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Leave a startup breadcrumb so we know Sentry is connected
        io.sentry.Sentry.addBreadcrumb(
            io.sentry.Breadcrumb().apply {
                category = "app.lifecycle"
                message = "BouncerActivity.onCreate"
                level = io.sentry.SentryLevel.INFO
            }
        )

        // Enable edge-to-edge display
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Initialize singleton managers
        FavouriteTeamsManager.initialize(this)

        LiveActivityNotificationBuilder(applicationContext).createNotificationChannel()
        requestNotificationPermissionIfNeeded()

        // Check onboarding status
        val prefs = getSharedPreferences("bouncer_prefs", Context.MODE_PRIVATE)
        val hasCompletedOnboarding = prefs.getBoolean("onboarding_complete", false)

        setContent {
            BouncerTheme {
                BouncerAppRoot(
                    hasCompletedOnboarding = hasCompletedOnboarding,
                    prefs = prefs
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS
            )
        }
    }

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 4112
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Navigation routes
// ──────────────────────────────────────────────────────────────────────────────

private object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val MATCHES = "matches"
    const val QUICK_ACTION = "quick_action"
    const val NEWS = "news"
    const val SETTINGS = "settings"
    const val FAVOURITES = "favourites"
    const val MATCH_DETAIL = "match/{matchId}"
    const val NEWS_DETAIL = "news_detail"
    const val ABOUT = "about"
    const val FORECAST = "forecast/{matchId}"
    const val ACHIEVEMENTS = "achievements"
    const val AR_TRACKING = "ar_tracking"
    const val STATS = "stats"
    const val ACTIVITY = "activity"
    const val MORE = "more"
    const val DEVTOOLS = "devtools"
    const val NOTIFICATION_SETTINGS = "notification_settings"

    fun matchDetail(matchId: String) = "match/$matchId"
    fun forecast(matchId: String) = "forecast/$matchId"
}

// ──────────────────────────────────────────────────────────────────────────────
// Root composable
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BouncerAppRoot(
    hasCompletedOnboarding: Boolean = true,
    prefs: SharedPreferences? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()
    val viewModel: LiveActivityViewModel = viewModel()
    val newsViewModel: NewsViewModel = viewModel()
    val coroutineScope = rememberCoroutineScope()
    val appUpdateManager = remember(context) { AppUpdateManager(context.applicationContext) }
    val liveNotificationManager = remember(context) { LiveNotificationManager(context.applicationContext) }
    val updateState by appUpdateManager.uiState.collectAsStateWithLifecycle()
    val showUpdateBadge by appUpdateManager.showUpdateBadge.collectAsStateWithLifecycle()
    
    // In-app banner for key events
    val keyEvent by viewModel.keyEvent.collectAsStateWithLifecycle()
    var bannerMessageLocal by remember { mutableStateOf<String?>(null) }
    
    // Derive live matches state for nav badge
    val currentMatches by viewModel.currentMatches.collectAsStateWithLifecycle()
    val hasLiveMatches = remember(currentMatches) {
        currentMatches.any { it.isLive }
    }
    
    // Determine start destination: splash -> onboarding (if first time) -> home
    val startDestination = if (!hasCompletedOnboarding) Routes.SPLASH else Routes.HOME

    LaunchedEffect(Unit) {
        UpdateCheckService.schedule(
            context = context.applicationContext,
            allowMetered = appUpdateManager.shouldAllowMeteredChecks()
        )
        // Defensive try-catch for update check - prevents crashes on sideloaded/emulator builds
        try {
            appUpdateManager.checkForUpdates()
        } catch (e: Exception) {
            Sentry.addBreadcrumb(
                io.sentry.Breadcrumb().apply {
                    category = "app.update"
                    message = "Update check failed at callsite: ${e.javaClass.simpleName}"
                    level = io.sentry.SentryLevel.WARNING
                }
            )
            // Silently ignore - update checks are non-critical
        }
        // Wire LiveNotificationManager for enriched notifications
        viewModel.setNotificationManager(liveNotificationManager)
    }

    LaunchedEffect(keyEvent) {
        keyEvent?.let { (title, body) ->
            bannerMessageLocal = "$title — ${body.take(100)}"
            viewModel.clearKeyEvent()
        }
    }

    LaunchedEffect(bannerMessageLocal) {
        if (bannerMessageLocal != null) {
            delay(5000)
            bannerMessageLocal = null
        }
    }

    // Track current route for bottom nav visibility
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Routes where the bottom navigation bar should be visible (not on splash/onboarding)
    val bottomNavRoutes = remember {
        setOf(
            Routes.HOME,
            Routes.MATCHES,
            Routes.QUICK_ACTION,
            Routes.NEWS,
            Routes.SETTINGS,
            Routes.FAVOURITES,
            Routes.ACHIEVEMENTS,
            Routes.ACTIVITY,
            Routes.MORE,
            Routes.ABOUT
        )
    }
    val showBottomNav = currentRoute in bottomNavRoutes

    // Premium background with subtle grid pattern
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBase)
            .drawWithCache {
                // Subtle grid pattern — cached to avoid recalculating every frame
                val gridSpacing = 60.dp.toPx()
                val gridColor = Color.White.copy(alpha = 0.005f)
                val xCount = (size.width / gridSpacing).toInt()
                val yCount = (size.height / gridSpacing).toInt()
                onDrawBehind {
                    for (x in 0..xCount) {
                        drawLine(
                            color = gridColor,
                            start = Offset(x * gridSpacing, 0f),
                            end = Offset(x * gridSpacing, size.height),
                            strokeWidth = 1f
                        )
                    }
                    for (y in 0..yCount) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y * gridSpacing),
                            end = Offset(size.width, y * gridSpacing),
                            strokeWidth = 1f
                        )
                    }
                }
            }
    ) {
        // Subtle atmospheric top-ambient glow — very restrained
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Gold400.copy(alpha = 0.015f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                AnimatedVisibility(
                    visible = showBottomNav,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    BouncerBottomNavBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                // Pop up to the start destination to avoid building up
                                // a large back-stack of top-level destinations.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        hasLiveMatch = hasLiveMatches
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(innerPadding)
            ) {
            // ── Splash Screen ────────────────────────────────────────────────
            composable(
                route = Routes.SPLASH,
                enterTransition = { fadeIn(tween(400)) },
                exitTransition = { fadeOut(tween(300)) }
            ) {
                SplashScreen(
                    onSplashComplete = {
                        val destination = if (!hasCompletedOnboarding) Routes.ONBOARDING else Routes.HOME
                        navController.navigate(destination) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }
            
            // ── Onboarding ───────────────────────────────────────────────────
            composable(
                route = Routes.ONBOARDING,
                enterTransition = { 
                    fadeIn(tween(400)) + slideInVertically(
                        initialOffsetY = { it / 6 },
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    )
                },
                exitTransition = { 
                    fadeOut(tween(300)) + slideOutVertically(
                        targetOffsetY = { -it / 6 },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    )
                }
            ) {
                OnboardingScreen(
                    onComplete = {
                        prefs?.edit()?.putBoolean("onboarding_complete", true)?.apply()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }
            
            // ── Home ─────────────────────────────────────────────────────
            composable(
                route = Routes.HOME,
                enterTransition = { 
                    fadeIn(tween(400)) + slideInHorizontally(
                        initialOffsetX = { -it / 8 },
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    )
                },
                exitTransition = { 
                    fadeOut(tween(300)) + slideOutHorizontally(
                        targetOffsetX = { -it / 8 },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    )
                }
            ) {
                BouncerHomeScreen(
                    viewModel = viewModel,
                    onMatchClick = { matchId ->
                        navController.navigate(Routes.matchDetail(matchId))
                    },
                    showUpdateBadge = showUpdateBadge,
                    onUpdateBadgeClick = {
                        coroutineScope.launch {
                            try {
                                appUpdateManager.checkForUpdates(forcePrompt = true)
                            } catch (_: Exception) {
                                // Silently ignore - update checks are non-critical
                            }
                        }
                    }
                )
            }

            // ── All Matches (Analyst Feed) ───────────────────────────────────
            composable(
                route = Routes.MATCHES,
                enterTransition = { 
                    fadeIn(tween(400)) + slideInHorizontally(
                        initialOffsetX = { it / 8 },
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    )
                },
                exitTransition = { 
                    fadeOut(tween(300)) + slideOutHorizontally(
                        targetOffsetX = { it / 8 },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    )
                }
            ) {
                AnalystFeedScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onMatchClick = { matchId ->
                        navController.navigate(Routes.matchDetail(matchId))
                    }
                )
            }

            // ── Quick Action (Home for now) ──────────────────────────────────
            composable(
                route = Routes.QUICK_ACTION,
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) }
            ) {
                BouncerHomeScreen(
                    viewModel = viewModel,
                    onMatchClick = { matchId ->
                        navController.navigate(Routes.matchDetail(matchId))
                    }
                )
            }

            // ── News ─────────────────────────────────────────────────────
            composable(
                route = Routes.NEWS,
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) }
            ) {
                NewsScreen(
                    onBackClick = { navController.popBackStack() },
                    newsViewModel = newsViewModel,
                    onArticleClick = { article ->
                        newsViewModel.selectArticle(article)
                        navController.navigate(Routes.NEWS_DETAIL)
                    }
                )
            }

            // ── News Detail ──────────────────────────────────────────────
            composable(
                route = Routes.NEWS_DETAIL,
                enterTransition = {
                    slideInVertically(initialOffsetY = { it }) + fadeIn()
                },
                exitTransition = {
                    slideOutVertically(targetOffsetY = { it }) + fadeOut()
                }
            ) {
                val selectedArticle = newsViewModel.selectedArticle.value
                if (selectedArticle != null) {
                    NewsDetailScreen(
                        article = selectedArticle,
                        newsViewModel = newsViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }

            // ── Match Detail ─────────────────────────────────────────────
            composable(
                route = Routes.MATCH_DETAIL,
                arguments = listOf(
                    navArgument("matchId") { type = NavType.StringType }
                ),
                enterTransition = {
                    slideInVertically(initialOffsetY = { it }) + fadeIn()
                },
                exitTransition = {
                    slideOutVertically(targetOffsetY = { it }) + fadeOut()
                }
            ) { backStackEntry ->
                val matchId = backStackEntry.arguments?.getString("matchId")
                    ?: return@composable
                BouncerMatchDetailScreen(
                    matchId = matchId,
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }

            // ── Favourites ───────────────────────────────────────────────
            composable(
                route = Routes.FAVOURITES,
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) }
            ) {
                BouncerFavouritesScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            // ── Settings ─────────────────────────────────────────────────
            composable(
                route = Routes.SETTINGS,
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) }
            ) {
                BouncerSettingsScreen(
                    onBackClick = { navController.popBackStack() },
                    onFavouritesClick = { navController.navigate(Routes.FAVOURITES) },
                    onAboutClick = { navController.navigate(Routes.ABOUT) },
                    onAIInsightsChanged = { enabled ->
                        viewModel.setAIInsightsEnabled(enabled)
                    }
                )
            }

            // ── About ────────────────────────────────────────────────────
            composable(
                route = Routes.ABOUT,
                enterTransition = {
                    slideInVertically(initialOffsetY = { it }) + fadeIn()
                },
                exitTransition = {
                    slideOutVertically(targetOffsetY = { it }) + fadeOut()
                }
            ) {
                BouncerAboutScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            // ── Forecast ─────────────────────────────────────────────────
            composable(
                route = Routes.FORECAST,
                arguments = listOf(
                    navArgument("matchId") { type = NavType.StringType }
                ),
                enterTransition = {
                    slideInVertically(initialOffsetY = { it }) + fadeIn()
                },
                exitTransition = {
                    slideOutVertically(targetOffsetY = { it }) + fadeOut()
                }
            ) { backStackEntry ->
                val matchId = backStackEntry.arguments?.getString("matchId")
                    ?: return@composable
                val matches by viewModel.currentMatches.collectAsStateWithLifecycle()
                val match = matches.find { it.id == matchId }
                if (match != null) {
                    McpPredictiveForecastScreen(
                        match = match,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }

            // ── Achievements / Trophy Room ───────────────────────────────
            composable(
                route = Routes.ACHIEVEMENTS,
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) }
            ) {
                TrophyRoomScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            // ── AR Ball Tracking ─────────────────────────────────────────
            composable(
                route = Routes.AR_TRACKING,
                enterTransition = {
                    slideInVertically(initialOffsetY = { it }) + fadeIn()
                },
                exitTransition = {
                    slideOutVertically(targetOffsetY = { it }) + fadeOut()
                }
            ) {
                ArBallTrackingScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            // ── Stats Overview ───────────────────────────────────────────
            composable(
                route = Routes.STATS,
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) }
            ) {
                StatsOverviewScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            // ── Intelligence Feed ────────────────────────────────────────
            composable(
                route = Routes.ACTIVITY,
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) }
            ) {
                IntelligenceFeedScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            // ── More ─────────────────────────────────────────────────────
            composable(
                route = Routes.MORE,
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) }
            ) {
                MoreScreen(
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    onAchievementsClick = { navController.navigate(Routes.ACHIEVEMENTS) },
                    onActivityClick = { navController.navigate(Routes.ACTIVITY) },
                    onArTrackingClick = { navController.navigate(Routes.AR_TRACKING) },
                    onAboutClick = { navController.navigate(Routes.ABOUT) },
                    onDevToolsClick = { navController.navigate(Routes.DEVTOOLS) }
                )
            }

            // ── Dev Tools ────────────────────────────────────────────────
            composable(
                route = Routes.DEVTOOLS,
                enterTransition = {
                    slideInVertically(initialOffsetY = { it }) + fadeIn()
                },
                exitTransition = {
                    slideOutVertically(targetOffsetY = { it }) + fadeOut()
                }
            ) {
                com.tanush.bouncer.ui.devtools.DevToolsScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            // ── Notification Settings ────────────────────────────────────
            composable(
                route = Routes.NOTIFICATION_SETTINGS,
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) }
            ) {
                NotificationSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            }
        }

        if (updateState !is AppUpdateUiState.Idle && updateState !is AppUpdateUiState.CheckingForUpdates) {
            AppUpdateDialog(
                state = updateState,
                onUpdateNow = {
                    coroutineScope.launch { appUpdateManager.downloadUpdate() }
                },
                onInstall = {
                    appUpdateManager.installUpdate()
                },
                onLater = {
                    appUpdateManager.dismissUpdate()
                },
                onDismiss = {
                    appUpdateManager.dismissUpdate()
                },
                onRetry = {
                    coroutineScope.launch {
                        try {
                            appUpdateManager.checkForUpdates(forcePrompt = true)
                        } catch (_: Exception) {
                            // Silently ignore - update checks are non-critical
                        }
                    }
                }
            )
        }
        
        bannerMessageLocal?.let { msg ->
            InAppBanner(
                message = msg,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp) // Adjusted for status bar
            )
        }
    }

    // ── Lifecycle-aware polling ──────────────────────────────────────────
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startPolling()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopPolling()
                else -> { /* no-op */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// About screen
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BouncerAboutScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // App icon
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Bouncer App Icon",
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(24.dp))
            )

            Spacer(modifier = Modifier.height(24.dp))

            // App name
            Text(
                text = "Bouncer",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Version
            Text(
                text = "v1.0.0",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Description
            Text(
                text = "A cricket score tracking app with live match updates, " +
                    "AI-powered insights, and detailed scorecards.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(24.dp))

            // Built with
            Text(
                text = "Built With",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            val technologies = listOf(
                "Jetpack Compose",
                "Material Design 3",
                "Kotlin Coroutines & Flows",
                "Navigation Compose",
                "Dynamic Color (Material You)"
            )
            technologies.forEach { tech ->
                Text(
                    text = tech,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(24.dp))

            // Credits
            Text(
                text = "Credits",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Cricket data provided by CricAPI",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Designed and developed by Tanush Malhotra",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
