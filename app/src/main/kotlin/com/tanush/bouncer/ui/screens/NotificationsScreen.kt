package com.tanush.bouncer.ui.screens

import androidx.compose.ui.graphics.vector.ImageVector
import com.tanush.bouncer.ui.theme.v2.BouncerColorsV2
import com.tanush.bouncer.ui.components.v2.ballChipInk
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.ui.platform.LocalContext
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.managers.NotificationInboxManager
import com.tanush.bouncer.managers.NotificationItem
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit, onRemindersClick: () -> Unit = {}) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val items by NotificationInboxManager.items.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        NotificationInboxManager.markAllRead()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Notifications",
                        style = typography.titleMedium,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = colors.onSurface,
                        )
                    }
                },
                actions = { RemindersAction(onRemindersClick) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        },
        containerColor = colors.background,
    ) { innerPadding ->
        if (items.isEmpty()) {
            EmptyStateV2(
                title = stringResource(R.string.notifications_no_notifications_yet),
                icon = Icons.Outlined.NotificationsNone,
                subtitle = stringResource(R.string.notifications_subscribe_to_matches_with_the_bell),
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = spacing.sm),
            ) {
                items(items, key = { it.id }) { item ->
                    NotificationRow(item = item)
                    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(start = spacing.lg + CHIP + spacing.md))
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

/**
 * Reminders are the other half of "what will tell me about a match": alerts that have arrived are
 * listed on this screen, the ones still to come one tap away.
 */
@Composable
private fun RemindersAction(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(stringResource(R.string.reminders_scheduled_reminders), color = BouncerThemeV2.colors.primaryText)
    }
}

/**
 * One alert: an event chip (the over strip's treatment — solid event fill, legible glyph), the
 * title and time, then the body. Unread is weight and a trailing dot. The event colour used to be a
 * small dot plus the type word written in that colour, and unread rows were tinted pills.
 */
@Composable
private fun NotificationRow(item: NotificationItem) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        EventChip(item.type)
        Spacer(Modifier.width(spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = typography.titleSmall,
                    fontWeight = if (!item.isRead) FontWeight.Bold else FontWeight.Medium,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(spacing.sm))
                Text(
                    text = formatTimestamp(LocalContext.current, item.timestamp),
                    style = typography.numericSmall,
                    color = colors.textSecondary,
                )
                if (!item.isRead) {
                    Spacer(Modifier.width(spacing.sm))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(colors.onSurface))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.body,
                style = typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 2,
            )
        }
    }
}

/** The alert's kind as a chip: the ball's glyph for wickets and boundaries, an icon otherwise. */
@Composable
private fun EventChip(type: String) {
    val colors = BouncerThemeV2.colors
    val (fill, glyph, icon) = eventChipSpec(type, colors)
    val ink = if (fill == colors.surfaceVariant) colors.textSecondary else ballChipInk(fill)
    Box(
        modifier = Modifier.size(CHIP).clip(RoundedCornerShape(8.dp)).background(fill),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph != null) {
            Text(glyph, style = BouncerThemeV2.typography.titleSmall, fontWeight = FontWeight.Bold, color = ink)
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        }
    }
}

private fun eventChipSpec(type: String, colors: BouncerColorsV2): Triple<Color, String?, ImageVector?> {
    val cricket = colors.cricket
    return when (type) {
        "wicket" -> Triple(cricket.wicket, "W", null)
        "six" -> Triple(cricket.six, "6", null)
        "four" -> Triple(cricket.four, "4", null)
        "milestone" -> Triple(cricket.milestone, null, Icons.Outlined.Star)
        "match_start" -> Triple(cricket.matchStart, null, Icons.Outlined.PlayArrow)
        "match_end" -> Triple(cricket.matchEnd, null, Icons.Outlined.Flag)
        else -> Triple(colors.surfaceVariant, null, Icons.Outlined.Notifications)
    }
}

private val CHIP = 32.dp

/**
 * Today's alerts show the time; older ones add the day. Every alert used to read as a bare
 * "7:30 PM", whatever day it came, in a 12-hour clock and the locale the file first loaded with.
 * DateUtils follows the device's 24-hour setting and current locale.
 */
private fun formatTimestamp(context: android.content.Context, ts: Long): String {
    val flags = if (DateUtils.isToday(ts)) {
        DateUtils.FORMAT_SHOW_TIME
    } else {
        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or
            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY
    }
    return DateUtils.formatDateTime(context, ts, flags)
}
