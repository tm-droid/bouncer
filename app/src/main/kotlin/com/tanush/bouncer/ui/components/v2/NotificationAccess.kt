package com.tanush.bouncer.ui.components.v2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Whether this app's notifications reach the user, and the one way to turn them on.
 *
 * Following a match, its alerts and a reminder all end in a notification. With notifications off,
 * each of them used to look set (a gold bell, "Reminder set") and then never arrive, because
 * `NotificationPermission.notify` declines quietly, as it should.
 */
@Stable
class NotificationAccess internal constructor(
    val enabled: Boolean,
    /** Asks for the permission once; after that, or below API 33, opens the app's notification settings. */
    val turnOn: () -> Unit,
)

@Composable
fun rememberNotificationAccess(): NotificationAccess {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(notificationsEnabled(context)) }
    // A second refusal stops the system showing the dialog at all, so a second tap goes to Settings.
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        enabled = notificationsEnabled(context)
    }
    // Settings is the only place to change it after a refusal, so it is re-read on every return.
    LifecycleResumeEffect(Unit) {
        enabled = notificationsEnabled(context)
        onPauseOrDispose { }
    }
    return NotificationAccess(enabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.openAppNotificationSettings()
        }
    }
}

/** Covers both the API 33 runtime grant and the app-level switch every release has. */
internal fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

internal fun Context.openAppNotificationSettings() {
    val candidates = listOf(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    )
    for (intent in candidates) {
        if (intent.resolveActivity(packageManager) == null) continue
        if (runCatching { startActivity(intent) }.isSuccess) return
    }
}

/** Says notifications are off and offers to turn them on. Draws nothing when they are on. */
@Composable
fun NotificationsOffNotice(access: NotificationAccess, modifier: Modifier = Modifier) {
    if (access.enabled) return
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(start = spacing.md, end = spacing.xs, top = spacing.xs, bottom = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.NotificationsOff,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = stringResource(R.string.notifications_off_notice),
            style = BouncerThemeV2.typography.bodySmall,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = access.turnOn) {
            Text(stringResource(R.string.notifications_turn_on), color = colors.primaryText)
        }
    }
}
