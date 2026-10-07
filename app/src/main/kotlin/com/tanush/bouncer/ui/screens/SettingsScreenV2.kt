package com.tanush.bouncer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tanush.bouncer.R
import com.tanush.bouncer.data.MatchesResponseCache
import com.tanush.bouncer.services.MatchCacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.SettingsItemV2
import com.tanush.bouncer.ui.components.v2.SettingsTrailing
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.ui.widgets.WidgetUpdateWorker
import com.tanush.bouncer.utils.PreferencesHelper
import com.tanush.bouncer.Diagnostics

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
    onRedoSetup: () -> Unit = {},
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

    val liveUpdateSubtitle = liveUpdateSubtitleRes()

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
    // Minutes, matching WidgetUpdateWorker.resolveUpdateDelayMs. The control previously offered
    // 5/10/15/30 SECONDS, which nothing read and which no fallback poller could have honoured.
    var refreshIntervalMinutes by remember {
        mutableStateOf(
            PreferencesHelper.getInt(
                context,
                PreferencesHelper.KEY_REFRESH_INTERVAL,
                WidgetUpdateWorker.DEFAULT_INTERVAL_MIN,
            ).coerceIn(WidgetUpdateWorker.MIN_INTERVAL_MIN, WidgetUpdateWorker.MAX_INTERVAL_MIN)
        )
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

    var shareDiagnostics by remember { mutableStateOf(Diagnostics.isEnabled(context)) }

    fun togglePref(key: String, current: Boolean, setter: (Boolean) -> Unit) {
        val next = !current
        PreferencesHelper.setBoolean(context, key, next)
        setter(next)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.common_settings), style = typography.titleMedium) },
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
                        icon = Icons.Default.Notifications,
                        title = stringResource(R.string.live_update_settings_title),
                        subtitle = stringResource(liveUpdateSubtitle),
                        trailing = SettingsTrailing.Navigation,
                        onClick = { context.openPromotedNotificationSettings() },
                    )
                    SettingsItemV2(
                        icon = Icons.Default.Notifications, title = stringResource(R.string.settings_live_match_alerts),
                        subtitle = stringResource(R.string.settings_get_notified_when_your_favorite_team),
                        trailing = SettingsTrailing.Toggle(liveNotifications),
                        onClick = { togglePref(PreferencesHelper.KEY_LIVE_NOTIFICATIONS, liveNotifications) { liveNotifications = it } },
                    )
                    SettingsItemV2(
                        icon = Icons.Default.Star, title = stringResource(R.string.settings_boundary_alerts),
                        subtitle = stringResource(R.string.settings_celebrate_every_four_and_six),
                        trailing = SettingsTrailing.Toggle(boundaryAlerts),
                        onClick = { togglePref(PreferencesHelper.KEY_BOUNDARY_ALERTS, boundaryAlerts) { boundaryAlerts = it } },
                    )
                    SettingsItemV2(
                        icon = Icons.Default.Star, title = stringResource(R.string.settings_wicket_alerts),
                        subtitle = stringResource(R.string.settings_never_miss_a_crucial_wicket),
                        trailing = SettingsTrailing.Toggle(wicketAlerts),
                        onClick = { togglePref(PreferencesHelper.KEY_WICKET_ALERTS, wicketAlerts) { wicketAlerts = it } },
                    )
                    SettingsItemV2(
                        icon = Icons.Default.AutoAwesome, title = stringResource(R.string.settings_ai_insights),
                        subtitle = stringResource(R.string.settings_ai_generated_insights_in_commentary),
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
                        icon = Icons.Default.Palette, title = stringResource(R.string.common_appearance),
                        subtitle = stringResource(R.string.settings_theme_variants_and_display_options),
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
                        icon = Icons.Default.Vibration, title = stringResource(R.string.settings_haptic_feedback),
                        subtitle = stringResource(R.string.settings_feel_the_game_with_vibrations),
                        trailing = SettingsTrailing.Toggle(hapticFeedback),
                        onClick = { togglePref(PreferencesHelper.KEY_HAPTIC_FEEDBACK, hapticFeedback) { hapticFeedback = it } },
                    )
                    SettingsItemV2(
                        icon = Icons.Default.Speed, title = stringResource(R.string.settings_auto_refresh),
                        subtitle = stringResource(R.string.settings_automatically_update_scores_every_10_seconds),
                        trailing = SettingsTrailing.Toggle(autoRefresh),
                        onClick = { togglePref(PreferencesHelper.KEY_AUTO_REFRESH, autoRefresh) { autoRefresh = it } },
                    )
                    SettingsItemV2(
                        icon = Icons.Default.Speed,
                        title = stringResource(R.string.settings_refresh_interval),
                        subtitle = "Fallback poll when push is unavailable: every ${refreshIntervalMinutes}m",
                        trailing = SettingsTrailing.Value("${refreshIntervalMinutes}m"),
                        onClick = {
                            // 5m → 15m → 30m → 60m. Push is the fast path, so this only sets how
                            // often the offline fallback runs.
                            val next = when (refreshIntervalMinutes) {
                                5 -> 15
                                15 -> 30
                                30 -> 60
                                else -> 5
                            }
                            PreferencesHelper.setInt(context, PreferencesHelper.KEY_REFRESH_INTERVAL, next)
                            refreshIntervalMinutes = next
                            // Re-arm immediately so the new interval takes effect without waiting
                            // out the old one.
                            WidgetUpdateWorker.schedulePeriodicUpdates(context)
                        },
                    )
                    SettingsItemV2(
                        title = stringResource(R.string.settings_win_probability),
                        subtitle = stringResource(R.string.settings_show_live_win_probability_predictions),
                        trailing = SettingsTrailing.Toggle(showProbability),
                        onClick = { togglePref(PreferencesHelper.KEY_SHOW_WIN_PROBABILITY, showProbability) { showProbability = it } },
                    )
                    SettingsItemV2(
                        title = stringResource(R.string.settings_reduced_motion),
                        subtitle = stringResource(R.string.settings_use_simpler_transitions),
                        trailing = SettingsTrailing.Toggle(reducedMotion),
                        onClick = { togglePref(PreferencesHelper.KEY_REDUCED_MOTION, reducedMotion) { reducedMotion = it } },
                    )
                    SettingsItemV2(
                        title = stringResource(R.string.settings_share_diagnostics),
                        subtitle = stringResource(R.string.settings_share_diagnostics_detail),
                        trailing = SettingsTrailing.Toggle(shareDiagnostics),
                        onClick = {
                            shareDiagnostics = !shareDiagnostics
                            Diagnostics.setEnabled(context, shareDiagnostics)
                        },
                    )
                    SettingsItemV2(
                        icon = Icons.Default.Delete,
                        title = stringResource(R.string.settings_clear_app_cache),
                        subtitle = stringResource(R.string.settings_remove_cached_match_data),
                        trailing = SettingsTrailing.Navigation,
                        onClick = { showClearCacheDialog = true },
                    )
                    SettingsItemV2(
                        icon = Icons.Default.Favorite, title = stringResource(R.string.common_favorite_teams),
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
                        icon = Icons.Default.Info, title = stringResource(R.string.settings_about_bouncer),
                        trailing = SettingsTrailing.Navigation,
                        onClick = onAboutClick,
                    )
                    SettingsItemV2(
                        icon = Icons.Default.Refresh, title = stringResource(R.string.settings_redo_setup),
                        subtitle = stringResource(R.string.settings_re_run_the_onboarding_flow_to),
                        trailing = SettingsTrailing.Navigation,
                        onClick = onRedoSetup,
                    )
                }
            }
        }

        // Clear cache confirmation dialog
        if (showClearCacheDialog) {
            AlertDialog(
                onDismissRequest = { showClearCacheDialog = false },
                title = { Text(stringResource(R.string.settings_clear_cache)) },
                text = { Text(stringResource(R.string.settings_this_will_remove_all_cached_match)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearCacheDialog = false
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) { context.cacheDir.deleteRecursively() }
                                MatchCacheService.clearCache()
                                // Also drop the raw match-list response cache, or "Clear cache"
                                // would leave stale fixtures on screen for up to its TTL.
                                MatchesResponseCache.shared.clear()
                                cacheCleared = true
                            }
                        }
                    ) { Text(stringResource(R.string.settings_clear)) }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheDialog = false }) { Text(stringResource(R.string.common_cancel)) }
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

/** Android 16 (API 36) is where promoted ongoing notifications — Live Updates — exist. */
private const val PROMOTED_NOTIFICATION_SDK = 36

/**
 * Subtitle for the Live Updates row, which doubles as the only place the user is told that the
 * system has switched promotion off for this app.
 *
 * Re-read on every resume because the only way to change it is in system settings, which means
 * leaving this screen and coming back.
 */
@Composable
private fun liveUpdateSubtitleRes(): Int {
    val context = LocalContext.current
    var blocked by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        blocked = Build.VERSION.SDK_INT >= PROMOTED_NOTIFICATION_SDK &&
            !NotificationManagerCompat.from(context).canPostPromotedNotifications()
        onPauseOrDispose { }
    }
    return if (blocked) R.string.live_update_settings_blocked else R.string.live_update_settings_subtitle
}

/**
 * Open the system screen governing whether this app may promote notifications, falling back to the
 * app's ordinary notification settings.
 *
 * The fallback is not only for older releases. `ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS` is
 * declared in the API 36 SDK but the screen behind it does not exist on every API 36 build — the
 * stock Android 16 emulator resolves nothing for it, where an unguarded `startActivity` is an
 * `ActivityNotFoundException`. Resolve before launching, and treat "no such screen" as ordinary.
 */
private fun Context.openPromotedNotificationSettings() {
    // Each action addresses the app differently — the two notification screens by EXTRA_APP_PACKAGE,
    // app details by a package: URI — so they cannot share one Intent.
    val candidates = buildList {
        if (Build.VERSION.SDK_INT >= PROMOTED_NOTIFICATION_SDK) {
            add(Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
        add(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }
    for (intent in candidates) {
        if (intent.resolveActivity(packageManager) == null) continue
        if (runCatching { startActivity(intent) }.isSuccess) return
    }
}
