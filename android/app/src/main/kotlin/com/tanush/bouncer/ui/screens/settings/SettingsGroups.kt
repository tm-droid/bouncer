package com.tanush.bouncer.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MotionPhotosOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tanush.bouncer.Diagnostics
import com.tanush.bouncer.R
import com.tanush.bouncer.data.MatchesResponseCache
import com.tanush.bouncer.services.MatchCacheService
import com.tanush.bouncer.ui.components.v2.NotificationsOffNotice
import com.tanush.bouncer.ui.components.v2.SettingsItemV2
import com.tanush.bouncer.ui.components.v2.SettingsTrailing
import com.tanush.bouncer.ui.components.v2.rememberNotificationAccess
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The settings the More tab shows, one group per composable, each switch holding its own state.
// Only switches something reads are here: "Auto-refresh", "Win probability" and "AI insights" were
// saved and never read anywhere, so flipping them changed nothing.

/** Live Updates, then the match alerts the push service reads (`BouncerFirebaseMessagingService`). */
@Composable
internal fun NotificationSettingsGroup() {
    val context = LocalContext.current
    NotificationsOffNotice(rememberNotificationAccess(), Modifier.padding(vertical = BouncerThemeV2.spacing.xs))
    SettingsItemV2(
        icon = Icons.Default.NotificationsActive,
        title = stringResource(R.string.live_update_settings_title),
        subtitle = stringResource(liveUpdateSubtitleRes()),
        trailing = SettingsTrailing.Navigation,
        onClick = { context.openPromotedNotificationSettings() },
    )
    PrefToggle(
        Icons.Default.Notifications, R.string.settings_live_match_alerts,
        R.string.settings_get_notified_when_your_favorite_team, PreferencesHelper.KEY_LIVE_NOTIFICATIONS,
    )
    PrefToggle(
        Icons.Default.SportsCricket, R.string.settings_boundary_alerts,
        R.string.settings_celebrate_every_four_and_six, PreferencesHelper.KEY_BOUNDARY_ALERTS,
    )
    PrefToggle(
        Icons.Default.Sports, R.string.settings_wicket_alerts,
        R.string.settings_never_miss_a_crucial_wicket, PreferencesHelper.KEY_WICKET_ALERTS,
    )
    SettingsItemV2(
        icon = Icons.Default.Vibration,
        title = stringResource(R.string.settings_sounds_vibration),
        subtitle = stringResource(R.string.settings_sounds_vibration_subtitle),
        trailing = SettingsTrailing.Navigation,
        onClick = { context.openAppNotificationSettings() },
    )
    QuietHoursItems()
}

/** The system's notification screen for this app, where channel sound and vibration are set. */
private fun Context.openAppNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    if (intent.resolveActivity(packageManager) != null) runCatching { startActivity(intent) }
}

/** How the app behaves on this device, and what it keeps. */
@Composable
internal fun DataSettingsGroup() {
    PrefToggle(
        Icons.Default.Vibration, R.string.settings_haptic_feedback,
        R.string.settings_feel_the_game_with_vibrations, PreferencesHelper.KEY_HAPTIC_FEEDBACK,
    )
    PrefToggle(
        Icons.Default.MotionPhotosOff, R.string.settings_reduced_motion,
        R.string.settings_use_simpler_transitions, PreferencesHelper.KEY_REDUCED_MOTION, default = false,
    )
    RefreshIntervalItem()
    DiagnosticsItem()
    ClearCacheItem()
}

@Composable
private fun PrefToggle(icon: ImageVector, title: Int, subtitle: Int, key: String, default: Boolean = true) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(PreferencesHelper.getBoolean(context, key, default)) }
    SettingsItemV2(
        icon = icon,
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        trailing = SettingsTrailing.Toggle(on),
        onClick = {
            on = !on
            PreferencesHelper.setBoolean(context, key, on)
        },
    )
}

@Composable
private fun DiagnosticsItem() {
    val context = LocalContext.current
    var share by remember { mutableStateOf(Diagnostics.isEnabled(context)) }
    SettingsItemV2(
        icon = Icons.Default.BugReport,
        title = stringResource(R.string.settings_share_diagnostics),
        subtitle = stringResource(R.string.settings_share_diagnostics_detail),
        trailing = SettingsTrailing.Toggle(share),
        // What setEnabled reports, not what was asked for: a choice that could not be saved as
        // "on" stays off, and the switch must say so.
        onClick = { share = Diagnostics.setEnabled(context, !share) },
    )
}

@Composable
private fun ClearCacheItem() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf(false) }
    SettingsItemV2(
        icon = Icons.Default.Delete,
        title = stringResource(R.string.settings_clear_app_cache),
        subtitle = stringResource(R.string.settings_remove_cached_match_data),
        trailing = SettingsTrailing.Navigation,
        onClick = { confirming = true },
    )
    if (!confirming) return
    AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text(stringResource(R.string.settings_clear_cache)) },
        text = { Text(stringResource(R.string.settings_this_will_remove_all_cached_match)) },
        confirmButton = {
            TextButton(
                onClick = {
                    confirming = false
                    scope.launch {
                        withContext(Dispatchers.IO) { context.cacheDir.deleteRecursively() }
                        MatchCacheService.clearCache()
                        // The raw match-list response cache too, or stale fixtures stay for its TTL.
                        MatchesResponseCache.shared.clear()
                        Toast.makeText(context, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show()
                    }
                },
            ) { Text(stringResource(R.string.settings_clear)) }
        },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.common_cancel)) } },
    )
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

/** The page column a group sits in, padded once for all of them. */
@Composable
internal fun SettingsColumn(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = BouncerThemeV2.spacing.lg)) { content() }
}
