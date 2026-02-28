/* REFERENCE ONLY - DO NOT BUILD
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
import androidx.compose.ui.graphics.Brush
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
import com.tanush.bouncer.ui.components.InAppBanner
import com.tanush.bouncer.ui.components.PremiumBottomNavBar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val prefs = getSharedPreferences("bouncer_prefs", Context.MODE_PRIVATE)
        val hasCompletedOnboarding = prefs.getBoolean("onboarding_complete", false)
        setContent {
            BouncerTheme {
                var showOnboarding by remember { mutableStateOf(!hasCompletedOnboarding) }
                if (showOnboarding) {
                    OnboardingScreen(onComplete = {
                        prefs.edit().putBoolean("onboarding_complete", true).apply()
                        showOnboarding = false
                    })
                } else {
                    MainAppContent()
                }
            }
        }
    }
}

@Composable
private fun MainAppContent() {
    val navController = rememberNavController()
    val viewModel: LiveActivityViewModel = viewModel()
    val keyEvent by viewModel.keyEvent.collectAsState()
    var bannerMessageLocal by remember { mutableStateOf<String?>(null) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    val showBottomBar = currentRoute in listOf("home", "favourites", "stats", "settings", "trophy_room")

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(BlackBackground, CardDarkBlue.copy(alpha = 0.3f), BlackBackground)))) {
        // NavHost occupies full screen for edge-to-edge experience
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    onMatchClick = { match -> navController.navigate("match/${match.id}") },
                    onVipBoxClick = { navController.navigate("executive_box") },
                    onTrophyRoomClick = { navController.navigate("trophy_room") },
                    onDevToolsClick = { navController.navigate("settings") }
                )
            }
            composable("match/{matchId}") { backStackEntry ->
                val matchId = backStackEntry.arguments?.getString("matchId")
                val matches by viewModel.currentMatches.collectAsState()
                val match = matches.find { it.id == matchId }
                if (match != null) {
                    MatchDetailView(
                        match = match,
                        onBackClick = { navController.popBackStack() },
                        onForecastClick = { navController.navigate("forecast/$matchId") },
                        onArClick = { navController.navigate("ar_tracking") }
                    )
                }
            }
            composable("forecast/{matchId}") { backStackEntry ->
                val matchId = backStackEntry.arguments?.getString("matchId")
                val matches by viewModel.currentMatches.collectAsState()
                val match = matches.find { it.id == matchId }
                if (match != null) {
                    McpPredictiveForecastScreen(match = match, onBackClick = { navController.popBackStack() })
                }
            }
            composable("trophy_room") { TrophyRoomScreen(onBackClick = { navController.popBackStack() }) }
            composable("executive_box") { ExecutiveBoxScreen(onBackClick = { navController.popBackStack() }) }
            composable("ar_tracking") { ArBallTrackingScreen(onBackClick = { navController.popBackStack() }) }
            composable("favourites") { FavoritesScreen(onBackClick = { navController.popBackStack() }, onTeamClick = { }) }
            composable("stats") { StatsOverviewScreen(onBackClick = { navController.popBackStack() }) }
            composable("settings") {
                SettingsScreen(
                    onBackClick = { navController.popBackStack() },
                    onFavoritesClick = { navController.navigate("favourites") },
                    onAchievementsClick = { navController.navigate("trophy_room") },
                    onAboutClick = { navController.navigate("about") },
                    onAIInsightsChanged = { enabled -> viewModel.setAIInsightsEnabled(enabled) }
                )
            }
            composable("about") { AboutScreen(onBackClick = { navController.popBackStack() }) }
            composable("devtools") { com.tanush.bouncer.ui.devtools.DevToolsScreen(onBackClick = { navController.popBackStack() }) }
        }

        // Global Navigation Bar overlays content
        AnimatedVisibility(
            visible = showBottomBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            PremiumBottomNavBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        keyEvent?.let { (title, body) ->
            bannerMessageLocal = "$title — ${body.take(100)}"
            LaunchedEffect(bannerMessageLocal) {
                kotlinx.coroutines.delay(3500)
                bannerMessageLocal = null
            }
            viewModel.clearKeyEvent()
        }
        bannerMessageLocal?.let { msg ->
            InAppBanner(message = msg, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}
*/
