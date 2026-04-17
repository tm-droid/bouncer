package com.tanush.bouncer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tanush.bouncer.ui.HomeScreen
import com.tanush.bouncer.ui.MatchDetailView
import com.tanush.bouncer.ui.screens.*
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import com.tanush.bouncer.viewmodels.LandingTab
import com.tanush.bouncer.ui.components.InAppBanner
import com.tanush.bouncer.ui.components.PremiumBottomNavBar
import com.tanush.bouncer.utils.PreferencesHelper

/**
 * MainActivity - Main entry point for Bouncer app.
 * Root Architecture with modern navigation and transitions.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        val prefs = getSharedPreferences("bouncer_prefs", Context.MODE_PRIVATE)
        val hasCompletedOnboarding = prefs.getBoolean("onboarding_complete", false)
        val initialThemeVariant = runCatching {
            ThemeVariant.valueOf(
                PreferencesHelper.getString(
                    this,
                    PreferencesHelper.KEY_THEME_VARIANT,
                    ThemeVariant.Dark.name
                )
            )
        }.getOrDefault(ThemeVariant.Dark)
        val initialBackgroundSkin = runCatching {
            BackgroundSkinType.valueOf(
                PreferencesHelper.getString(
                    this,
                    PreferencesHelper.KEY_BACKGROUND_SKIN,
                    BackgroundSkinType.MIDNIGHT_GRADIENT.name
                )
            )
        }.getOrDefault(BackgroundSkinType.MIDNIGHT_GRADIENT)
        val initialLandingRoute = when (
            PreferencesHelper.getString(
                this,
                PreferencesHelper.KEY_DEFAULT_LANDING,
                LandingTab.HOME.name
            )
        ) {
            LandingTab.ALL_MATCHES.name -> "all_matches"
            LandingTab.FAVOURITES.name -> "favourites"
            else -> "home"
        }
        
        setContent {
            var currentThemeVariant by remember { mutableStateOf(initialThemeVariant) }
            var currentBackgroundSkin by remember { mutableStateOf(initialBackgroundSkin) }

            BouncerTheme(
                themeVariant = currentThemeVariant,
                backgroundSkin = BackgroundSkin.fromType(currentBackgroundSkin)
            ) {
                MainAppContent(
                    hasCompletedOnboarding = hasCompletedOnboarding,
                    prefs = prefs,
                    defaultLandingRoute = initialLandingRoute,
                    currentThemeVariant = currentThemeVariant,
                    currentBackgroundSkin = currentBackgroundSkin,
                    onThemeVariantChanged = { variant ->
                        currentThemeVariant = variant
                        PreferencesHelper.setString(
                            this@MainActivity,
                            PreferencesHelper.KEY_THEME_VARIANT,
                            variant.name
                        )
                    },
                    onBackgroundSkinChanged = { skin ->
                        currentBackgroundSkin = skin
                        PreferencesHelper.setString(
                            this@MainActivity,
                            PreferencesHelper.KEY_BACKGROUND_SKIN,
                            skin.name
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun MainAppContent(
    hasCompletedOnboarding: Boolean = true,
    prefs: android.content.SharedPreferences? = null,
    defaultLandingRoute: String = "home",
    currentThemeVariant: ThemeVariant = ThemeVariant.Dark,
    currentBackgroundSkin: BackgroundSkinType = BackgroundSkinType.MIDNIGHT_GRADIENT,
    onThemeVariantChanged: (ThemeVariant) -> Unit = {},
    onBackgroundSkinChanged: (BackgroundSkinType) -> Unit = {}
) {
    val navController = rememberNavController()
    val viewModel: LiveActivityViewModel = viewModel()
    val keyEvent by viewModel.keyEvent.collectAsState()
    var bannerMessageLocal by remember { mutableStateOf<String?>(null) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    
    // Determine start destination: splash -> onboarding (if first time) -> home
    val startDestination = if (!hasCompletedOnboarding) "splash" else defaultLandingRoute
    
    // Wire LiveNotificationManager so enriched notifications work
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.setNotificationManager(
            com.tanush.bouncer.services.LiveNotificationManager(context.applicationContext)
        )
    }
    
    // Derive live matches state for nav badge
    val currentMatches by viewModel.currentMatches.collectAsState()
    val hasLiveMatches = remember(currentMatches) {
        currentMatches.any { it.isLive }
    }
    
    // Show bottom bar on all 5 primary tabs and their sub-routes (but not on splash/onboarding)
    val showBottomBar = currentRoute in listOf(
        "home", "all_matches", "favourites", "stats", "more",
        "achievements", "settings", "appearance", "about", "activity"
    )

    // Background with subtle grid pattern
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

        NavHost(
            navController = navController, 
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(
                "splash",
                enterTransition = { fadeIn(tween(AnimationDuration.moderate)) },
                exitTransition = { fadeOut(tween(AnimationDuration.normal)) }
            ) { 
                SplashScreen(onSplashComplete = { 
                    val destination = if (!hasCompletedOnboarding) "onboarding" else defaultLandingRoute
                    navController.navigate(destination) { 
                        popUpTo("splash") { inclusive = true } 
                    } 
                }) 
            }
            composable(
                "onboarding",
                enterTransition = { fadeIn(tween(AnimationDuration.moderate)) + slideInVertically(initialOffsetY = { it / 6 }, animationSpec = tween(AnimationDuration.moderate, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(AnimationDuration.normal)) + slideOutVertically(targetOffsetY = { -it / 6 }, animationSpec = tween(AnimationDuration.normal, easing = FastOutSlowInEasing)) }
            ) {
                OnboardingScreen(onComplete = {
                    prefs?.edit()?.putBoolean("onboarding_complete", true)?.apply()
                    navController.navigate(defaultLandingRoute) {
                        popUpTo("onboarding") { inclusive = true }
                    }
                })
            }
            composable(
                "home",
                enterTransition = { fadeIn(tween(AnimationDuration.moderate)) + slideInHorizontally(initialOffsetX = { -it / 8 }, animationSpec = tween(AnimationDuration.moderate, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(AnimationDuration.normal)) + slideOutHorizontally(targetOffsetX = { -it / 8 }, animationSpec = tween(AnimationDuration.normal, easing = FastOutSlowInEasing)) }
            ) { 
                HomeScreen(
                    viewModel = viewModel, 
                    onMatchClick = { match -> navController.navigate("match/${match.id}") },
                    onMoreMatchesClick = { navController.navigate("all_matches") },
                    onAchievementsClick = { navController.navigate("achievements") },
                    onDevToolsClick = { navController.navigate("devtools") },
                    onIntelligenceFeedClick = { navController.navigate("activity") }
                ) 
            }
            
            composable(
                "match/{matchId}",
                enterTransition = { 
                    slideInVertically(initialOffsetY = { it / 10 }, animationSpec = tween(500, easing = FastOutSlowInEasing)) + fadeIn()
                },
                exitTransition = { 
                    slideOutVertically(targetOffsetY = { it / 10 }, animationSpec = tween(500, easing = FastOutSlowInEasing)) + fadeOut()
                }
            ) { backStackEntry ->
                val matchId = backStackEntry.arguments?.getString("matchId")
                val match = currentMatches.find { it.id == matchId }
                if (match != null) { 
                    MatchDetailView(
                        match = match, 
                        onBackClick = { navController.popBackStack() }, 
                        onForecastClick = { navController.navigate("forecast/${match.id}") },
                        onArClick = { navController.navigate("ar_tracking") },
                        onOpenCommentary = { navController.navigate("match/${match.id}/commentary") },
                        onOpenScorecard = { navController.navigate("match/${match.id}/scorecard") },
                        onOpenAnalysis = { navController.navigate("match/${match.id}/analysis") }
                    ) 
                }
            }
            
            composable("forecast/{matchId}") { backStackEntry ->
                val matchId = backStackEntry.arguments?.getString("matchId")
                val match = currentMatches.find { it.id == matchId }
                if (match != null) { 
                    McpPredictiveForecastScreen(match = match, onBackClick = { navController.popBackStack() }) 
                }
            }

            composable(
                "match/{matchId}/commentary",
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it / 8 },
                        animationSpec = tween(AnimationDuration.moderate, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(AnimationDuration.moderate))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it / 8 },
                        animationSpec = tween(AnimationDuration.normal, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(AnimationDuration.normal))
                }
            ) { backStackEntry ->
                val matchId = backStackEntry.arguments?.getString("matchId")
                val match = currentMatches.find { it.id == matchId }
                if (match != null) {
                    MatchCommentaryScreen(
                        match = match,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }

            composable(
                "match/{matchId}/scorecard",
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it / 8 },
                        animationSpec = tween(AnimationDuration.moderate, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(AnimationDuration.moderate))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it / 8 },
                        animationSpec = tween(AnimationDuration.normal, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(AnimationDuration.normal))
                }
            ) { backStackEntry ->
                val matchId = backStackEntry.arguments?.getString("matchId")
                val match = currentMatches.find { it.id == matchId }
                if (match != null) {
                    MatchScorecardScreen(
                        match = match,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }

            composable(
                "match/{matchId}/analysis",
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it / 8 },
                        animationSpec = tween(AnimationDuration.moderate, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(AnimationDuration.moderate))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it / 8 },
                        animationSpec = tween(AnimationDuration.normal, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(AnimationDuration.normal))
                }
            ) { backStackEntry ->
                val matchId = backStackEntry.arguments?.getString("matchId")
                val match = currentMatches.find { it.id == matchId }
                if (match != null) {
                    MatchAnalysisScreen(
                        match = match,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }
            
            composable(
                "achievements",
                enterTransition = { fadeIn(tween(AnimationDuration.moderate)) + slideInHorizontally(initialOffsetX = { it / 6 }, animationSpec = tween(AnimationDuration.moderate, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(AnimationDuration.normal)) }
            ) { TrophyRoomScreen(onBackClick = { navController.popBackStack() }) }
            composable(
                "all_matches",
                enterTransition = { fadeIn(tween(AnimationDuration.moderate)) + slideInHorizontally(initialOffsetX = { it / 8 }, animationSpec = tween(AnimationDuration.moderate, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(AnimationDuration.normal)) + slideOutHorizontally(targetOffsetX = { it / 8 }, animationSpec = tween(AnimationDuration.normal, easing = FastOutSlowInEasing)) }
            ) { AllMatchesScreen(onBackClick = { navController.popBackStack() }) }
            composable(
                "ar_tracking",
                enterTransition = { fadeIn(tween(AnimationDuration.moderate)) + slideInVertically(initialOffsetY = { it / 6 }, animationSpec = tween(AnimationDuration.moderate, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(AnimationDuration.normal)) + slideOutVertically(targetOffsetY = { it / 6 }) }
            ) { ArBallTrackingScreen(onBackClick = { navController.popBackStack() }) }
            composable(
                "favourites",
                enterTransition = { fadeIn(tween(AnimationDuration.moderate)) + slideInHorizontally(initialOffsetX = { it / 8 }, animationSpec = tween(AnimationDuration.moderate, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(AnimationDuration.normal)) + slideOutHorizontally(targetOffsetX = { it / 8 }, animationSpec = tween(AnimationDuration.normal, easing = FastOutSlowInEasing)) }
            ) { FavoritesScreen(onBackClick = { navController.popBackStack() }, onTeamClick = { }) }
            composable(
                "stats",
                enterTransition = { fadeIn(tween(AnimationDuration.moderate)) + slideInHorizontally(initialOffsetX = { it / 8 }, animationSpec = tween(AnimationDuration.moderate, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(AnimationDuration.normal)) + slideOutHorizontally(targetOffsetX = { it / 8 }, animationSpec = tween(AnimationDuration.normal, easing = FastOutSlowInEasing)) }
            ) {
                StatsHubScreen(
                    onBackClick = { navController.popBackStack() },
                    onOpenTrophy = { navController.navigate("trophy-room") },
                    onOpenAnalytics = { navController.navigate("stats_overview") },
                    onOpenAchievements = { navController.navigate("achievements") }
                )
            }

            composable(
                "stats_overview",
                enterTransition = { fadeIn(tween(AnimationDuration.moderate)) + slideInHorizontally(initialOffsetX = { it / 8 }, animationSpec = tween(AnimationDuration.moderate, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(AnimationDuration.normal)) + slideOutHorizontally(targetOffsetX = { it / 8 }, animationSpec = tween(AnimationDuration.normal, easing = FastOutSlowInEasing)) }
            ) { StatsOverviewScreen(onBackClick = { navController.popBackStack() }) }
            composable(
                "activity",
                enterTransition = { fadeIn(tween(AnimationDuration.moderate)) + slideInHorizontally(initialOffsetX = { it / 6 }, animationSpec = tween(AnimationDuration.moderate, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(AnimationDuration.normal)) }
            ) { IntelligenceFeedScreen(onBackClick = { navController.popBackStack() }) }
            composable(
                "settings",
                enterTransition = { fadeIn(tween(AnimationDuration.moderate)) + slideInHorizontally(initialOffsetX = { it / 6 }, animationSpec = tween(AnimationDuration.moderate, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(AnimationDuration.normal)) }
            ) { 
                SettingsScreen(
                    onBackClick = { navController.popBackStack() }, 
                    onFavoritesClick = { navController.navigate("favourites") }, 
                    onAchievementsClick = { navController.navigate("achievements") }, 
                    onAboutClick = { navController.navigate("about") },
                    onAppearanceClick = { navController.navigate("appearance") },
                    onNotificationSettingsClick = { navController.navigate("notification_settings") },
                    onAIInsightsChanged = { enabled -> viewModel.setAIInsightsEnabled(enabled) }
                ) 
            }
            composable(
                "appearance",
                enterTransition = { fadeIn(tween(AnimationDuration.moderate)) + slideInHorizontally(initialOffsetX = { it / 6 }, animationSpec = tween(AnimationDuration.moderate, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(AnimationDuration.normal)) + slideOutHorizontally(targetOffsetX = { it / 6 }, animationSpec = tween(AnimationDuration.normal, easing = FastOutSlowInEasing)) }
            ) {
                AppearanceScreen(
                    onBackClick = { navController.popBackStack() },
                    currentThemeVariant = currentThemeVariant,
                    currentBackgroundSkin = currentBackgroundSkin,
                    onThemeVariantChanged = onThemeVariantChanged,
                    onBackgroundSkinChanged = onBackgroundSkinChanged
                )
            }
            composable(
                "about",
                enterTransition = { fadeIn(tween(AnimationDuration.moderate)) + slideInHorizontally(initialOffsetX = { it / 6 }, animationSpec = tween(AnimationDuration.moderate, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(AnimationDuration.normal)) }
            ) { AboutScreen(onBackClick = { navController.popBackStack() }) }
            composable(
                "more",
                enterTransition = { fadeIn(tween(AnimationDuration.moderate)) + slideInHorizontally(initialOffsetX = { it / 8 }, animationSpec = tween(AnimationDuration.moderate, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(AnimationDuration.normal)) + slideOutHorizontally(targetOffsetX = { it / 8 }, animationSpec = tween(AnimationDuration.normal, easing = FastOutSlowInEasing)) }
            ) {
                MoreScreen(
                    onSettingsClick = { navController.navigate("settings") },
                    onStatsClick = { navController.navigate("stats") },
                    onAchievementsClick = { navController.navigate("achievements") },
                    onActivityClick = { navController.navigate("activity") },
                    onArTrackingClick = { navController.navigate("ar_tracking") },
                    onAboutClick = { navController.navigate("about") },
                    onDevToolsClick = { navController.navigate("devtools") }
                )
            }
            composable(
                "devtools",
                enterTransition = { fadeIn(tween(AnimationDuration.moderate)) + slideInVertically(initialOffsetY = { it / 6 }, animationSpec = tween(AnimationDuration.moderate, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(AnimationDuration.normal)) + slideOutVertically(targetOffsetY = { it / 6 }) }
            ) { com.tanush.bouncer.ui.devtools.DevToolsScreen(onBackClick = { navController.popBackStack() }) }
            composable(
                "notification_settings",
                enterTransition = { fadeIn(tween(AnimationDuration.moderate)) + slideInHorizontally(initialOffsetX = { it / 6 }, animationSpec = tween(AnimationDuration.moderate, easing = FastOutSlowInEasing)) },
                exitTransition = { fadeOut(tween(AnimationDuration.normal)) + slideOutHorizontally(targetOffsetX = { it / 6 }, animationSpec = tween(AnimationDuration.normal, easing = FastOutSlowInEasing)) }
            ) { NotificationSettingsScreen(onBack = { navController.popBackStack() }) }
        }

        // Bottom Navigation — visibility is handled internally via the `visible` param
        PremiumBottomNavBar(
            currentRoute = currentRoute,
            onNavigate = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
            visible = showBottomBar,
            hasLiveMatches = hasLiveMatches
        )
        
        // Notification Banner
        keyEvent?.let { (title, body) ->
            bannerMessageLocal = "$title — ${body.take(100)}"
            LaunchedEffect(bannerMessageLocal) { 
                kotlinx.coroutines.delay(5000)
                bannerMessageLocal = null 
            }
            viewModel.clearKeyEvent()
        }
        
        bannerMessageLocal?.let { msg -> 
            InAppBanner(
                message = msg, 
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp) // Adjusted for status bar depth
            )
        }
    }
}
