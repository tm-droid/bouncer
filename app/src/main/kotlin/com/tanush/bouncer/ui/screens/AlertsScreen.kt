package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanush.bouncer.R
import com.tanush.bouncer.managers.MatchSubscriptionManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.MatchCardV2
import com.tanush.bouncer.ui.components.v2.TextTabBarV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.ReminderScheduler
import com.tanush.bouncer.utils.RemindersManager

/** The Alerts screen's tabs, in order. */
enum class AlertsTab { INBOX, FOLLOWING, REMINDERS }

/**
 * Everything that tells the reader about a match, in one place: alerts that have arrived (Inbox),
 * the matches they follow and what for (Following), and reminders still to come (Reminders). They
 * were a notifications screen behind the Home bell, a reminders screen behind a text button on it,
 * and no list at all of what was followed — that was only a bell's fill on a match row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    matches: List<CricketMatch>,
    onOpenMatch: (String) -> Unit,
    onBack: () -> Unit,
    initialTab: AlertsTab = AlertsTab.INBOX,
) {
    val colors = BouncerThemeV2.colors
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(initialTab.ordinal) }
    var reminders by remember { mutableStateOf(RemindersManager.upcoming(context, System.currentTimeMillis())) }

    Scaffold(
        topBar = {
            AlertsTopBar(showClearAll = tab == AlertsTab.REMINDERS.ordinal && reminders.isNotEmpty(), onBack = onBack) {
                reminders.forEach { ReminderScheduler.cancelReminder(context, it.id) }
                RemindersManager.clearAll(context)
                reminders = emptyList()
            }
        },
        containerColor = colors.background,
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            TextTabBarV2(
                tabs = listOf(
                    stringResource(R.string.alerts_inbox),
                    stringResource(R.string.alerts_following),
                    stringResource(R.string.reminders_scheduled_reminders),
                ),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
            when (AlertsTab.entries[tab]) {
                AlertsTab.INBOX -> NotificationInbox(onOpenMatch)
                AlertsTab.FOLLOWING -> FollowingList(matches, onOpenMatch)
                AlertsTab.REMINDERS -> ReminderList(
                    reminders = reminders,
                    onCancel = { reminder ->
                        ReminderScheduler.cancelReminder(context, reminder.id)
                        RemindersManager.deleteReminder(context, reminder.id)
                        reminders = RemindersManager.upcoming(context, System.currentTimeMillis())
                    },
                    onOpenMatch = onOpenMatch,
                )
            }
        }
    }
}

/**
 * The matches being followed: on the card (Live Updates) or for any alert. Each is its row from
 * Home, bell included, which opens the same sheet to change or stop following. A followed match
 * the app no longer holds in any list still shows, by id, so it can be opened and unfollowed.
 */
@Composable
private fun FollowingList(matches: List<CricketMatch>, onOpenMatch: (String) -> Unit) {
    val cards by MatchSubscriptionManager.subscriptions.collectAsStateWithLifecycle()
    val alerts by MatchSubscriptionManager.alertPrefs.collectAsStateWithLifecycle()
    val ids = (cards + alerts.filterValues { it.anyOn }.keys).toList()
    if (ids.isEmpty()) {
        EmptyStateV2(
            title = stringResource(R.string.alerts_following_empty_title),
            subtitle = stringResource(R.string.alerts_following_empty_subtitle),
            icon = Icons.Outlined.NotificationsNone,
        )
        return
    }
    val byId = matches.associateBy { it.id }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BouncerThemeV2.spacing.xxl)) {
        items(ids, key = { it }) { id ->
            val match = byId[id]
            if (match != null) {
                MatchCardV2(
                    match = match,
                    onClick = { onOpenMatch(id) },
                    modifier = Modifier.fillMaxWidth(),
                    isFollowing = true,
                    followable = true,
                )
            } else {
                Text(
                    text = stringResource(R.string.alerts_following_unlisted, id),
                    style = BouncerThemeV2.typography.bodyMedium,
                    color = BouncerThemeV2.colors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenMatch(id) }
                        .padding(BouncerThemeV2.spacing.lg),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertsTopBar(showClearAll: Boolean, onBack: () -> Unit, onClearAll: () -> Unit) {
    val colors = BouncerThemeV2.colors
    CenterAlignedTopAppBar(
        title = {
            Text(
                stringResource(R.string.alerts_title),
                style = BouncerThemeV2.typography.titleMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = colors.onSurface)
            }
        },
        actions = {
            if (showClearAll) {
                TextButton(onClick = onClearAll) { Text(stringResource(R.string.reminders_clear_all), color = colors.primaryText) }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
    )
}
