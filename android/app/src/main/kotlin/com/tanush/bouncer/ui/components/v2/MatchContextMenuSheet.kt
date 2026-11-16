package com.tanush.bouncer.ui.components.v2

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanush.bouncer.R
import com.tanush.bouncer.managers.FavouriteTeamsManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.network.CricBuzzUrls
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.PreferencesHelper
import com.tanush.bouncer.utils.rememberHapticFeedback

/**
 * Long-press actions for a match row.
 *
 * [onSubscribeToggle] is null for a match with nothing left to follow; when present it opens the
 * same follow sheet as the row's bell, so the control is reachable without hitting a 16dp icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchContextMenuSheet(
    match: CricketMatch,
    onDismiss: () -> Unit,
    onSetReminder: () -> Unit = {},
    isSubscribed: Boolean = false,
    onSubscribeToggle: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = colors.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = spacing.xl)) {
            Text(
                text = match.matchTitle,
                style = BouncerThemeV2.typography.titleSmall,
                color = colors.onSurface,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
            )
            HorizontalDivider(color = colors.divider)
            SheetItem(stringResource(R.string.match_share_score), Icons.Default.Share) {
                shareScore(context, match)
                onDismiss()
            }
            SheetItem(stringResource(R.string.match_copy_score), Icons.Default.ContentCopy) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Score", scoreText(match)))
                onDismiss()
            }
            if (onSubscribeToggle != null) {
                LiveUpdatesItem(isSubscribed = isSubscribed, onToggle = onSubscribeToggle, onDismiss = onDismiss)
            }
            FavouriteItems(match = match, onDismiss = onDismiss)
            SheetItem(stringResource(R.string.common_set_reminder), Icons.Default.Notifications) {
                onSetReminder()
                onDismiss()
            }
            SheetItem(stringResource(R.string.match_copy_match_id), Icons.Default.ContentCopy) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Match ID", match.id))
                onDismiss()
            }
        }
    }
}

@Composable
private fun LiveUpdatesItem(isSubscribed: Boolean, onToggle: () -> Unit, onDismiss: () -> Unit) {
    SheetItem(
        label = stringResource(if (isSubscribed) R.string.match_stop_live_updates else R.string.match_follow_live_updates),
        icon = if (isSubscribed) Icons.Default.NotificationsActive else Icons.Default.Notifications,
    ) {
        onToggle()
        onDismiss()
    }
}

/**
 * One item per side. It used to be one item that read "Remove" when either side was a favourite
 * and always added or removed the first side, so it could remove a team the reader never chose.
 */
@Composable
private fun FavouriteItems(match: CricketMatch, onDismiss: () -> Unit) {
    val favorites by FavouriteTeamsManager.favourites.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hapticEnabled = remember {
        PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_HAPTIC_FEEDBACK, true)
    }
    val haptics = rememberHapticFeedback()
    match.teams.map { it.name }.filter { it.isNotBlank() }.distinct().forEach { name ->
        val isFavorite = name in favorites
        SheetItem(
            label = stringResource(if (isFavorite) R.string.favourite_remove_team else R.string.favourite_add_team, name),
            icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
        ) {
            if (hapticEnabled) haptics.toggle(turningOn = !isFavorite)
            if (isFavorite) FavouriteTeamsManager.remove(name) else FavouriteTeamsManager.add(name)
            onDismiss()
        }
    }
}

@Composable
private fun SheetItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private fun scoreText(match: CricketMatch) =
    match.teams.joinToString(" vs ") { "${it.name} ${it.score.ifBlank { "-" }}" }

private fun shareScore(context: Context, match: CricketMatch) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        val link = CricBuzzUrls.liveScoresUrl(match.id)
        putExtra(Intent.EXTRA_TEXT, "${scoreText(match)}\n$link\n#cricket #bouncer")
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.match_share_score)))
}
