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
import com.tanush.bouncer.models.AchievementKind
import com.tanush.bouncer.models.CricketMatch
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
    var selectedTab by remember { mutableStateOf(NavigationItem.HOME) }
    var currentSubScreen by remember { mutableStateOf<String?>(null) }

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
        // Content Area
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn(tween(300)) + slideInHorizontally(
                    initialOffsetX = { it * direction / 2 },
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                )).togetherWith(
                    fadeOut(tween(200)) + slideOutHorizontally(
                        targetOffsetX = { -it * direction / 2 },
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    )
                )
            },
            modifier = Modifier.fillMaxSize(),
            label = "tabNavigation"
        ) { tab ->
            when (tab) {
                NavigationItem.HOME -> {
                    HomeScreen(
                        viewModel = viewModel,
                        onMatchClick = onMatchClick,
                        onMoreMatchesClick = { currentSubScreen = "all_matches" },
                        onAchievementsClick = { selectedTab = NavigationItem.ACHIEVEMENTS }
                    )
                }
                NavigationItem.FAVORITES -> {
                    FavoritesScreen(
                        onBackClick = { selectedTab = NavigationItem.HOME },
                        onTeamClick = { /* Handle team click if needed */ }
                    )
                }
                NavigationItem.ACHIEVEMENTS -> {
                    AchievementsScreen(
                        unlockedAchievements = emptySet(),
                        onBackClick = { selectedTab = NavigationItem.HOME }
                    )
                }
                NavigationItem.SETTINGS -> {
                    SettingsScreen(
                        onBackClick = { selectedTab = NavigationItem.HOME },
                        onFavoritesClick = { selectedTab = NavigationItem.FAVORITES },
                        onAchievementsClick = { selectedTab = NavigationItem.ACHIEVEMENTS },
                        onAboutClick = { /* Handle about click */ }
                    )
                }
            }
        }

        // All Matches Screen Slide-up
        AnimatedVisibility(
            visible = currentSubScreen == "all_matches",
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            AllMatchesScreen(onBackClick = { currentSubScreen = null })
        }
    }
}
