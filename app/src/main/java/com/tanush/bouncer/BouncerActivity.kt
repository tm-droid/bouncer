package com.tanush.bouncer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.ui.components.BouncerBottomNavBar
import com.tanush.bouncer.ui.screens.AnalystFeedScreen
import com.tanush.bouncer.ui.screens.BouncerFavouritesScreen
import com.tanush.bouncer.ui.screens.BouncerHomeScreen
import com.tanush.bouncer.ui.screens.BouncerMatchDetailScreen
import com.tanush.bouncer.ui.screens.BouncerSettingsScreen
import com.tanush.bouncer.ui.screens.NewsScreen
import com.tanush.bouncer.ui.theme.BouncerTheme
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import io.sentry.Sentry

/**
 * Main entry point for the Bouncer cricket app.
 *
 * Uses Jetpack Compose with Material 3, edge-to-edge display, and a
 * bottom-navigation–driven single-Activity architecture.
 */
class BouncerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    // waiting for view to draw to better represent a captured error with a screenshot
    findViewById<android.view.View>(android.R.id.content).viewTreeObserver.addOnGlobalLayoutListener {
      try {
        throw Exception("This app uses Sentry! :)")
      } catch (e: Exception) {
        Sentry.captureException(e)
      }
    }


        // Enable edge-to-edge display
        enableEdgeToEdge()

        // Initialize singleton managers
        FavouriteTeamsManager.initialize(this)

        setContent {
            BouncerTheme {
                BouncerAppRoot()
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Navigation routes
// ──────────────────────────────────────────────────────────────────────────────

private object Routes {
    const val HOME = "home"
    const val MATCHES = "matches"
    const val QUICK_ACTION = "quick_action"
    const val NEWS = "news"
    const val SETTINGS = "settings"
    const val FAVOURITES = "favourites"
    const val MATCH_DETAIL = "match/{matchId}"
    const val ABOUT = "about"

    fun matchDetail(matchId: String) = "match/$matchId"
}

// ──────────────────────────────────────────────────────────────────────────────
// Root composable
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BouncerAppRoot() {
    val navController = rememberNavController()
    val viewModel: LiveActivityViewModel = viewModel()

    // Track current route for bottom nav visibility
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Routes where the bottom navigation bar should be visible
    val bottomNavRoutes = listOf(
        Routes.HOME,
        Routes.MATCHES,
        Routes.QUICK_ACTION,
        Routes.NEWS,
        Routes.SETTINGS,
        Routes.FAVOURITES
    )
    val showBottomNav = currentRoute in bottomNavRoutes

    Scaffold(
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
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            // ── Home ─────────────────────────────────────────────────────
            composable(
                route = Routes.HOME,
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

            // ── All Matches (Analyst Feed) ───────────────────────────────────
            composable(
                route = Routes.MATCHES,
                enterTransition = { fadeIn(animationSpec = tween(300)) },
                exitTransition = { fadeOut(animationSpec = tween(300)) }
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
                    onBackClick = { navController.popBackStack() }
                )
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
