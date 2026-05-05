package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.tanush.bouncer.services.MatchCacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.SettingsItemV2
import com.tanush.bouncer.ui.components.v2.SettingsTrailing
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.PreferencesHelper

/**
 * SettingsScreenV2 — Clean grouped settings with toggles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenV2(
    onBackClick: () -> Unit,
    onFavoritesClick: () -> Unit = {},
    onAchievementsClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onAppearanceClick: () -> Unit = {},
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val context = LocalContext.current

    // Cache clearing state
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Toggle states
    var liveNotifications by remember {
        mutableStateOf(PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_LIVE_NOTIFICATIONS, true))
    }
    var boundaryAlerts by remember {
        mutableStateOf(PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_BOUNDARY_ALERTS, true))
    }
    var wicketAlerts by remember {
        mutableStateOf(PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_WICKET_ALERTS, true))
    }
    var hapticFeedback by remember {
        mutableStateOf(PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_HAPTIC_FEEDBACK, true))
    }
    var autoRefresh by remember {
        mutableStateOf(PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_AUTO_REFRESH, true))
    }
    var refreshIntervalSeconds by remember {
        mutableStateOf(PreferencesHelper.getInt(context, PreferencesHelper.KEY_REFRESH_INTERVAL, 10))
    }
    var showProbability by remember {
        mutableStateOf(PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_SHOW_WIN_PROBABILITY, true))
    }
    var reducedMotion by remember {
        mutableStateOf(PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_REDUCED_MOTION, false))
    }
    var aiInsights by remember {
        mutableStateOf(PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_AI_INSIGHTS, false))
    }

    fun togglePref(key: String, current: Boolean, setter: (Boolean) -> Unit) {
        val next = !current
        PreferencesHelper.setBoolean(context, key, next)
        setter(next)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", style = typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.surface,
                    titleContentColor = colors.onSurface,
                    navigationIconContentColor = colors.onSurface,
                ),
            )
        },
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = spacing.xxl),
        ) {
            // Notifications
            item { SectionHeaderV2("Notifications", modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm)) }
            item {
                Column(Modifier.padding(horizontal = spacing.lg)) {
                    SettingsItemV2(
                        icon = Icons.Default.Notifications, title = "Live Match Alerts",
                        subtitle = "Get notified when your favorite team is playing",
                        trailing = SettingsTrailing.Toggle(liveNotifications),
                        onClick = { togglePref(PreferencesHelper.KEY_LIVE_NOTIFICATIONS, liveNotifications) { liveNotifications = it } },
                    )
                    SettingsItemV2(
                        icon = Icons.Default.Star, title = "Boundary Alerts",
                        subtitle = "Celebrate every four and six",
                        trailing = SettingsTrailing.Toggle(boundaryAlerts),
                        onClick = { togglePref(PreferencesHelper.KEY_BOUNDARY_ALERTS, boundaryAlerts) { boundaryAlerts = it } },
                    )
                    SettingsItemV2(
                        icon = Icons.Default.Star, title = "Wicket Alerts",
                        subtitle = "Never miss a crucial wicket",
                        trailing = SettingsTrailing.Toggle(wicketAlerts),
                        onClick = { togglePref(PreferencesHelper.KEY_WICKET_ALERTS, wicketAlerts) { wicketAlerts = it } },
                    )
                    SettingsItemV2(
                        icon = Icons.Default.AutoAwesome, title = "AI Insights",
                        subtitle = "AI-generated insights in commentary",
                        trailing = SettingsTrailing.Toggle(aiInsights),
                        onClick = { togglePref(PreferencesHelper.KEY_AI_INSIGHTS, aiInsights) { aiInsights = it } },
                    )
                }
            }

            // Appearance
            item { Spacer(Modifier.height(spacing.md)) }
            item { SectionHeaderV2("Appearance", modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm)) }
            item {
                Column(Modifier.padding(horizontal = spacing.lg)) {
                    SettingsItemV2(
                        icon = Icons.Default.Palette, title = "Appearance",
                        subtitle = "Theme variants and display options",
                        trailing = SettingsTrailing.Navigation,
                        onClick = onAppearanceClick,
                    )
                }
            }

            // Data & Behavior
            item { Spacer(Modifier.height(spacing.md)) }
            item { SectionHeaderV2("Data & Behavior", modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm)) }
            item {
                Column(Modifier.padding(horizontal = spacing.lg)) {
                    SettingsItemV2(
                        icon = Icons.Default.Vibration, title = "Haptic Feedback",
                        subtitle = "Feel the game with vibrations",
                        trailing = SettingsTrailing.Toggle(hapticFeedback),
                        onClick = { togglePref(PreferencesHelper.KEY_HAPTIC_FEEDBACK, hapticFeedback) { hapticFeedback = it } },
                    )
                    SettingsItemV2(
                        icon = Icons.Default.Speed, title = "Auto Refresh",
                        subtitle = "Automatically update scores every 10 seconds",
                        trailing = SettingsTrailing.Toggle(autoRefresh),
                        onClick = { togglePref(PreferencesHelper.KEY_AUTO_REFRESH, autoRefresh) { autoRefresh = it } },
                    )
                    SettingsItemV2(
                        icon = Icons.Default.Speed,
                        title = "Refresh Interval",
                        subtitle = "Score update frequency: ${refreshIntervalSeconds}s",
                        trailing = SettingsTrailing.Value("${refreshIntervalSeconds}s"),
                        onClick = {
                            // Cycle through 5s → 10s → 15s → 30s
                            val next = when (refreshIntervalSeconds) {
                                5 -> 10
                                10 -> 15
                                15 -> 30
                                else -> 5
                            }
                            PreferencesHelper.setInt(context, PreferencesHelper.KEY_REFRESH_INTERVAL, next)
                            refreshIntervalSeconds = next
                        },
                    )
                    SettingsItemV2(
                        title = "Win Probability",
                        subtitle = "Show live win probability predictions",
                        trailing = SettingsTrailing.Toggle(showProbability),
                        onClick = { togglePref(PreferencesHelper.KEY_SHOW_WIN_PROBABILITY, showProbability) { showProbability = it } },
                    )
                    SettingsItemV2(
                        title = "Reduced Motion",
                        subtitle = "Use simpler transitions",
                        trailing = SettingsTrailing.Toggle(reducedMotion),
                        onClick = { togglePref(PreferencesHelper.KEY_REDUCED_MOTION, reducedMotion) { reducedMotion = it } },
                    )
                    SettingsItemV2(
                        icon = Icons.Default.Delete,
                        title = "Clear App Cache",
                        subtitle = "Remove cached match data",
                        trailing = SettingsTrailing.Navigation,
                        onClick = { showClearCacheDialog = true },
                    )
                    SettingsItemV2(
                        icon = Icons.Default.Favorite, title = "Favorite Teams",
                        trailing = SettingsTrailing.Navigation,
                        onClick = onFavoritesClick,
                    )
                }
            }

            // About
            item { Spacer(Modifier.height(spacing.md)) }
            item { SectionHeaderV2("About", modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm)) }
            item {
                Column(Modifier.padding(horizontal = spacing.lg)) {
                    SettingsItemV2(
                        icon = Icons.Default.Info, title = "About Bouncer",
                        trailing = SettingsTrailing.Navigation,
                        onClick = onAboutClick,
                    )
                }
            }
        }

        // Clear cache confirmation dialog
        if (showClearCacheDialog) {
            AlertDialog(
                onDismissRequest = { showClearCacheDialog = false },
                title = { Text("Clear Cache?") },
                text = { Text("This will remove all cached match data. The app will re-fetch on next load.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearCacheDialog = false
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) { context.cacheDir.deleteRecursively() }
                                MatchCacheService.clearCache()
                                cacheCleared = true
                            }
                        }
                    ) { Text("Clear") }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Show snackbar after cache is cleared
        LaunchedEffect(cacheCleared) {
            if (cacheCleared) {
                snackbarHostState.showSnackbar("Cache cleared")
                cacheCleared = false
            }
        }
    }
}
