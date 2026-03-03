package com.tanush.bouncer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.PremiumBottomNavBar
import com.tanush.bouncer.ui.components.navDestinations
import com.tanush.bouncer.ui.screens.*
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.viewmodels.LiveActivityViewModel

/**
 * App entry point with navigation handling.
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
                        }
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

@Composable
fun MainAppContainer(
    viewModel: LiveActivityViewModel = viewModel(),
    onMatchClick: (CricketMatch) -> Unit
) {
    // Route-based navigation matching PremiumBottomNavBar destinations
    var currentRoute by remember { mutableStateOf("home") }

    // Track previous route index for slide direction
    val routeIndex = remember(currentRoute) {
        navDestinations.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    }
    var previousIndex by remember { mutableIntStateOf(0) }

    // Determine if viewModel has live matches for badge
    val currentMatches by viewModel.currentMatches.collectAsState()
    val hasLiveMatches = remember(currentMatches) {
        currentMatches.any { it.isLive }
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
        // Content Area — padded at bottom for nav bar clearance
        AnimatedContent(
            targetState = currentRoute,
            transitionSpec = {
                val direction = if (
                    navDestinations.indexOfFirst { it.route == targetState }
                    > previousIndex
                ) 1 else -1
                (fadeIn(tween(300)) + slideInHorizontally(
                    initialOffsetX = { it * direction / 4 },
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                )).togetherWith(
                    fadeOut(tween(200)) + slideOutHorizontally(
                        targetOffsetX = { -it * direction / 4 },
                        animationSpec = tween(350, easing = FastOutSlowInEasing)
                    )
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),  // clearance for bottom nav bar
            label = "tabNavigation"
        ) { route ->
            when (route) {
                "home" -> {
                    HomeScreen(
                        viewModel = viewModel,
                        onMatchClick = onMatchClick,
                        onMoreMatchesClick = {
                            previousIndex = routeIndex
                            currentRoute = "all_matches"
                        },
                        onAchievementsClick = {
                            previousIndex = routeIndex
                            currentRoute = "stats"
                        }
                    )
                }
                "all_matches" -> {
                    AllMatchesScreen(
                        viewModel = viewModel,
                        onBackClick = {
                            previousIndex = routeIndex
                            currentRoute = "home"
                        }
                    )
                }
                "favourites" -> {
                    FavoritesScreen(
                        onBackClick = {
                            previousIndex = routeIndex
                            currentRoute = "home"
                        },
                        onTeamClick = { /* Handle team click */ }
                    )
                }
                "stats" -> {
                    TrophyRoomScreen(
                        onBackClick = {
                            previousIndex = routeIndex
                            currentRoute = "home"
                        }
                    )
                }
                "more" -> {
                    MoreScreen(
                        onSettingsClick = { /* push settings sub-screen */ },
                        onAchievementsClick = {
                            previousIndex = routeIndex
                            currentRoute = "stats"
                        },
                        onActivityClick = { /* push activity sub-screen */ },
                        onArTrackingClick = { /* push AR tracking */ },
                        onAboutClick = { /* push about sub-screen */ },
                        onDevToolsClick = { /* push dev tools */ }
                    )
                }
            }
        }

        // ── Bottom Navigation Bar (v3.0) ──
        PremiumBottomNavBar(
            currentRoute = currentRoute,
            onNavigate = { route ->
                previousIndex = routeIndex
                currentRoute = route
            },
            hasLiveMatches = hasLiveMatches,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
