package com.tanush.bouncer.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.delay

// ──────────────────────────────────────────────────────────────────────────────
// Colors (hardcoded)
// ──────────────────────────────────────────────────────────────────────────────

private val BlackBackground = Color(0xFF0A0C10)
private val CardSurface = Color(0xFF141720)
private val ShinyGoldDeep = Color(0xFFD4AF37)
private val TextPrimary = Color(0xFFF5F5F5)
private val TextSecondary = Color(0xFFAAAAAA)
private val SwitchTrackOff = Color(0xFF2A2D35)
private val BorderColor = Color(0xFF1E2128)

// ──────────────────────────────────────────────────────────────────────────────
// Main Screen
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // State
    var liveMatchUpdates by remember { 
        mutableStateOf(getNotificationPref(context, PREF_LIVE_MATCH_UPDATES, true)) 
    }
    var scoreChangesMode by remember { 
        mutableIntStateOf(getNotificationPref(context, PREF_SCORE_CHANGES_MODE, 0)) 
    }
    var wicketAlerts by remember { 
        mutableStateOf(getNotificationPref(context, PREF_WICKET_ALERTS, true)) 
    }
    var boundaryAlerts by remember { 
        mutableStateOf(getNotificationPref(context, PREF_BOUNDARY_ALERTS, true)) 
    }
    var overCompletion by remember { 
        mutableStateOf(getNotificationPref(context, PREF_OVER_COMPLETION, false)) 
    }
    var inningsBreak by remember { 
        mutableStateOf(getNotificationPref(context, PREF_INNINGS_BREAK, true)) 
    }
    var matchResult by remember { 
        mutableStateOf(getNotificationPref(context, PREF_MATCH_RESULT, true)) 
    }

    var favouriteTeamMatches by remember { 
        mutableStateOf(getNotificationPref(context, PREF_FAVOURITE_TEAM_MATCHES, true)) 
    }
    var teamSwitch by remember { 
        mutableStateOf(getNotificationPref(context, PREF_TEAM_SWITCH, true)) 
    }

    var preMatchReminders by remember { 
        mutableStateOf(getNotificationPref(context, PREF_PRE_MATCH_REMINDERS, true)) 
    }
    var reminderTime by remember { 
        mutableIntStateOf(getNotificationPref(context, PREF_REMINDER_TIME, 30)) 
    }
    var tossAlerts by remember { 
        mutableStateOf(getNotificationPref(context, PREF_TOSS_ALERTS, true)) 
    }
    var playingXIAnnounced by remember { 
        mutableStateOf(getNotificationPref(context, PREF_PLAYING_XI, false)) 
    }

    var breakingNews by remember { 
        mutableStateOf(getNotificationPref(context, PREF_BREAKING_NEWS, true)) 
    }
    var matchPreviews by remember { 
        mutableStateOf(getNotificationPref(context, PREF_MATCH_PREVIEWS, false)) 
    }
    var postMatchAnalysis by remember { 
        mutableStateOf(getNotificationPref(context, PREF_POST_MATCH_ANALYSIS, false)) 
    }
    var playerMilestones by remember { 
        mutableStateOf(getNotificationPref(context, PREF_PLAYER_MILESTONES, true)) 
    }

    var showLiveActivity by remember { 
        mutableStateOf(getNotificationPref(context, PREF_SHOW_LIVE_ACTIVITY, false)) 
    }
    var liveActivityStyle by remember { 
        mutableIntStateOf(getNotificationPref(context, PREF_LIVE_ACTIVITY_STYLE, 0)) 
    }

    var enableQuietHours by remember { 
        mutableStateOf(getNotificationPref(context, PREF_QUIET_HOURS_ENABLED, false)) 
    }
    var quietHoursStart by remember { 
        mutableIntStateOf(getNotificationPref(context, PREF_QUIET_HOURS_START, 22)) 
    }
    var quietHoursEnd by remember { 
        mutableIntStateOf(getNotificationPref(context, PREF_QUIET_HOURS_END, 7)) 
    }

    var vibration by remember { 
        mutableStateOf(getNotificationPref(context, PREF_VIBRATION, true)) 
    }
    var sound by remember { 
        mutableStateOf(getNotificationPref(context, PREF_SOUND, true)) 
    }
    var ledIndicator by remember { 
        mutableStateOf(getNotificationPref(context, PREF_LED_INDICATOR, false)) 
    }

    var sectionIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        repeat(20) { index ->
            delay(50L)
            sectionIndex = index
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Notification Settings",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ShinyGoldDeep
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BlackBackground
                )
            )
        },
        containerColor = BlackBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ═════════════════════════════════════════════════════════════════
            // 1. MATCH NOTIFICATIONS
            // ═════════════════════════════════════════════════════════════════
            item(key = "section_match") {
                AnimatedSection(visible = sectionIndex >= 0) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SectionHeader(title = "MATCH NOTIFICATIONS")
                        Spacer(Modifier.height(8.dp))

                        GlassCard {
                            Column {
                                ToggleRow(
                                    icon = Icons.Outlined.NotificationsActive,
                                    title = "Live Match Updates",
                                    description = "Real-time updates during matches",
                                    checked = liveMatchUpdates,
                                    onCheckedChange = {
                                        liveMatchUpdates = it
                                        setNotificationPref(context, PREF_LIVE_MATCH_UPDATES, it)
                                        logSettingChange(PREF_LIVE_MATCH_UPDATES, it)
                                    }
                                )

                                SectionDivider()

                                SelectorRow(
                                    icon = Icons.Outlined.SportsCricket,
                                    title = "Score Changes",
                                    description = getScoreChangesLabel(scoreChangesMode),
                                    onClick = {
                                        scoreChangesMode = (scoreChangesMode + 1) % 3
                                        setNotificationPref(context, PREF_SCORE_CHANGES_MODE, scoreChangesMode)
                                        logSettingChange(PREF_SCORE_CHANGES_MODE, scoreChangesMode)
                                    }
                                )

                                SectionDivider()

                                ToggleRow(
                                    icon = Icons.Outlined.Campaign,
                                    title = "Wicket Alerts",
                                    description = "Get notified on wickets",
                                    checked = wicketAlerts,
                                    onCheckedChange = {
                                        wicketAlerts = it
                                        setNotificationPref(context, PREF_WICKET_ALERTS, it)
                                        logSettingChange(PREF_WICKET_ALERTS, it)
                                    }
                                )

                                SectionDivider()

                                ToggleRow(
                                    icon = Icons.Outlined.Star,
                                    title = "Boundary Alerts",
                                    description = "4s and 6s notifications",
                                    checked = boundaryAlerts,
                                    onCheckedChange = {
                                        boundaryAlerts = it
                                        setNotificationPref(context, PREF_BOUNDARY_ALERTS, it)
                                        logSettingChange(PREF_BOUNDARY_ALERTS, it)
                                    }
                                )

                                SectionDivider()

                                ToggleRow(
                                    icon = Icons.Outlined.CheckCircle,
                                    title = "Over Completion",
                                    description = "Alert at end of each over",
                                    checked = overCompletion,
                                    onCheckedChange = {
                                        overCompletion = it
                                        setNotificationPref(context, PREF_OVER_COMPLETION, it)
                                        logSettingChange(PREF_OVER_COMPLETION, it)
                                    }
                                )

                                SectionDivider()

                                ToggleRow(
                                    icon = Icons.Outlined.Refresh,
                                    title = "Innings Break",
                                    description = "Notify when innings ends",
                                    checked = inningsBreak,
                                    onCheckedChange = {
                                        inningsBreak = it
                                        setNotificationPref(context, PREF_INNINGS_BREAK, it)
                                        logSettingChange(PREF_INNINGS_BREAK, it)
                                    }
                                )

                                SectionDivider()

                                ToggleRow(
                                    icon = Icons.Outlined.Check,
                                    title = "Match Result",
                                    description = "Final result notifications",
                                    checked = matchResult,
                                    onCheckedChange = {
                                        matchResult = it
                                        setNotificationPref(context, PREF_MATCH_RESULT, it)
                                        logSettingChange(PREF_MATCH_RESULT, it)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ═════════════════════════════════════════════════════════════════
            // 2. TEAM NOTIFICATIONS
            // ═════════════════════════════════════════════════════════════════
            item(key = "section_team") {
                AnimatedSection(visible = sectionIndex >= 1) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SectionHeader(title = "TEAM NOTIFICATIONS")
                        Spacer(Modifier.height(8.dp))

                        GlassCard {
                            Column {
                                ToggleRow(
                                    icon = Icons.Outlined.Star,
                                    title = "Favourite Team Matches",
                                    description = "Alerts for your favourite teams",
                                    checked = favouriteTeamMatches,
                                    onCheckedChange = {
                                        favouriteTeamMatches = it
                                        setNotificationPref(context, PREF_FAVOURITE_TEAM_MATCHES, it)
                                        logSettingChange(PREF_FAVOURITE_TEAM_MATCHES, it)
                                    }
                                )

                                SectionDivider()

                                ToggleRow(
                                    icon = Icons.Outlined.Refresh,
                                    title = "Team Batting/Bowling Switch",
                                    description = "Notify when team role changes",
                                    checked = teamSwitch,
                                    onCheckedChange = {
                                        teamSwitch = it
                                        setNotificationPref(context, PREF_TEAM_SWITCH, it)
                                        logSettingChange(PREF_TEAM_SWITCH, it)
                                    }
                                )

                                SectionDivider()

                                InfoText(
                                    text = "Notifications will be sent for teams you've favourited"
                                )
                            }
                        }
                    }
                }
            }

            // ═════════════════════════════════════════════════════════════════
            // 3. MATCH REMINDERS
            // ═════════════════════════════════════════════════════════════════
            item(key = "section_reminders") {
                AnimatedSection(visible = sectionIndex >= 2) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SectionHeader(title = "MATCH REMINDERS")
                        Spacer(Modifier.height(8.dp))

                        GlassCard {
                            Column {
                                ToggleRow(
                                    icon = Icons.Outlined.Schedule,
                                    title = "Pre-match Reminders",
                                    description = "Remind before match starts",
                                    checked = preMatchReminders,
                                    onCheckedChange = {
                                        preMatchReminders = it
                                        setNotificationPref(context, PREF_PRE_MATCH_REMINDERS, it)
                                        logSettingChange(PREF_PRE_MATCH_REMINDERS, it)
                                    }
                                )

                                SectionDivider()

                                SelectorRow(
                                    icon = Icons.Outlined.AccessTime,
                                    title = "Reminder Time",
                                    description = getReminderTimeLabel(reminderTime),
                                    onClick = {
                                        reminderTime = when (reminderTime) {
                                            15 -> 30
                                            30 -> 60
                                            60 -> 120
                                            else -> 15
                                        }
                                        setNotificationPref(context, PREF_REMINDER_TIME, reminderTime)
                                        logSettingChange(PREF_REMINDER_TIME, reminderTime)
                                    }
                                )

                                SectionDivider()

                                ToggleRow(
                                    icon = Icons.Outlined.Campaign,
                                    title = "Toss Alerts",
                                    description = "Notify when toss happens",
                                    checked = tossAlerts,
                                    onCheckedChange = {
                                        tossAlerts = it
                                        setNotificationPref(context, PREF_TOSS_ALERTS, it)
                                        logSettingChange(PREF_TOSS_ALERTS, it)
                                    }
                                )

                                SectionDivider()

                                ToggleRow(
                                    icon = Icons.Outlined.Notifications,
                                    title = "Playing XI Announced",
                                    description = "Team lineup alerts",
                                    checked = playingXIAnnounced,
                                    onCheckedChange = {
                                        playingXIAnnounced = it
                                        setNotificationPref(context, PREF_PLAYING_XI, it)
                                        logSettingChange(PREF_PLAYING_XI, it)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ═════════════════════════════════════════════════════════════════
            // 4. NEWS & UPDATES
            // ═════════════════════════════════════════════════════════════════
            item(key = "section_news") {
                AnimatedSection(visible = sectionIndex >= 3) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SectionHeader(title = "NEWS & UPDATES")
                        Spacer(Modifier.height(8.dp))

                        GlassCard {
                            Column {
                                ToggleRow(
                                    icon = Icons.Outlined.Campaign,
                                    title = "Breaking News",
                                    description = "Latest cricket news alerts",
                                    checked = breakingNews,
                                    onCheckedChange = {
                                        breakingNews = it
                                        setNotificationPref(context, PREF_BREAKING_NEWS, it)
                                        logSettingChange(PREF_BREAKING_NEWS, it)
                                    }
                                )

                                SectionDivider()

                                ToggleRow(
                                    icon = Icons.Outlined.Notifications,
                                    title = "Match Previews",
                                    description = "Pre-match analysis",
                                    checked = matchPreviews,
                                    onCheckedChange = {
                                        matchPreviews = it
                                        setNotificationPref(context, PREF_MATCH_PREVIEWS, it)
                                        logSettingChange(PREF_MATCH_PREVIEWS, it)
                                    }
                                )

                                SectionDivider()

                                ToggleRow(
                                    icon = Icons.Outlined.CheckCircle,
                                    title = "Post-match Analysis",
                                    description = "Match summary notifications",
                                    checked = postMatchAnalysis,
                                    onCheckedChange = {
                                        postMatchAnalysis = it
                                        setNotificationPref(context, PREF_POST_MATCH_ANALYSIS, it)
                                        logSettingChange(PREF_POST_MATCH_ANALYSIS, it)
                                    }
                                )

                                SectionDivider()

                                ToggleRow(
                                    icon = Icons.Outlined.Star,
                                    title = "Player Milestones",
                                    description = "Century, 5-wicket hauls, etc.",
                                    checked = playerMilestones,
                                    onCheckedChange = {
                                        playerMilestones = it
                                        setNotificationPref(context, PREF_PLAYER_MILESTONES, it)
                                        logSettingChange(PREF_PLAYER_MILESTONES, it)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ═════════════════════════════════════════════════════════════════
            // 5. LIVE ACTIVITY
            // ═════════════════════════════════════════════════════════════════
            item(key = "section_live") {
                AnimatedSection(visible = sectionIndex >= 4) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SectionHeader(title = "LIVE ACTIVITY")
                        Spacer(Modifier.height(8.dp))

                        GlassCard {
                            Column {
                                ToggleRow(
                                    icon = Icons.Outlined.NotificationsActive,
                                    title = "Show Live Activity",
                                    description = "Persistent notification during live matches",
                                    checked = showLiveActivity,
                                    onCheckedChange = {
                                        showLiveActivity = it
                                        setNotificationPref(context, PREF_SHOW_LIVE_ACTIVITY, it)
                                        logSettingChange(PREF_SHOW_LIVE_ACTIVITY, it)
                                    }
                                )

                                SectionDivider()

                                SelectorRow(
                                    icon = Icons.Outlined.Settings,
                                    title = "Live Activity Style",
                                    description = getLiveActivityStyleLabel(liveActivityStyle),
                                    onClick = {
                                        liveActivityStyle = (liveActivityStyle + 1) % 3
                                        setNotificationPref(context, PREF_LIVE_ACTIVITY_STYLE, liveActivityStyle)
                                        logSettingChange(PREF_LIVE_ACTIVITY_STYLE, liveActivityStyle)
                                    }
                                )

                                SectionDivider()

                                LiveActivityPreview(style = liveActivityStyle)
                            }
                        }
                    }
                }
            }

            // ═════════════════════════════════════════════════════════════════
            // 6. QUIET HOURS
            // ═════════════════════════════════════════════════════════════════
            item(key = "section_quiet") {
                AnimatedSection(visible = sectionIndex >= 5) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SectionHeader(title = "QUIET HOURS")
                        Spacer(Modifier.height(8.dp))

                        GlassCard {
                            Column {
                                ToggleRow(
                                    icon = Icons.Outlined.Bedtime,
                                    title = "Enable Quiet Hours",
                                    description = "Silence notifications at night",
                                    checked = enableQuietHours,
                                    onCheckedChange = {
                                        enableQuietHours = it
                                        setNotificationPref(context, PREF_QUIET_HOURS_ENABLED, it)
                                        logSettingChange(PREF_QUIET_HOURS_ENABLED, it)
                                    }
                                )

                                SectionDivider()

                                SelectorRow(
                                    icon = Icons.Outlined.AccessTime,
                                    title = "Start Time",
                                    description = formatTime(quietHoursStart),
                                    onClick = {
                                        quietHoursStart = (quietHoursStart + 1) % 24
                                        setNotificationPref(context, PREF_QUIET_HOURS_START, quietHoursStart)
                                        logSettingChange(PREF_QUIET_HOURS_START, quietHoursStart)
                                    }
                                )

                                SectionDivider()

                                SelectorRow(
                                    icon = Icons.Outlined.AccessTime,
                                    title = "End Time",
                                    description = formatTime(quietHoursEnd),
                                    onClick = {
                                        quietHoursEnd = (quietHoursEnd + 1) % 24
                                        setNotificationPref(context, PREF_QUIET_HOURS_END, quietHoursEnd)
                                        logSettingChange(PREF_QUIET_HOURS_END, quietHoursEnd)
                                    }
                                )

                                SectionDivider()

                                InfoText(
                                    text = "Notifications will be silenced during quiet hours"
                                )
                            }
                        }
                    }
                }
            }

            // ═════════════════════════════════════════════════════════════════
            // 7. ADVANCED
            // ═════════════════════════════════════════════════════════════════
            item(key = "section_advanced") {
                AnimatedSection(visible = sectionIndex >= 6) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SectionHeader(title = "ADVANCED")
                        Spacer(Modifier.height(8.dp))

                        GlassCard {
                            Column {
                                ToggleRow(
                                    icon = Icons.Outlined.Vibration,
                                    title = "Vibration",
                                    description = "Vibrate on notifications",
                                    checked = vibration,
                                    onCheckedChange = {
                                        vibration = it
                                        setNotificationPref(context, PREF_VIBRATION, it)
                                        logSettingChange(PREF_VIBRATION, it)
                                    }
                                )

                                SectionDivider()

                                ToggleRow(
                                    icon = Icons.Outlined.VolumeUp,
                                    title = "Sound",
                                    description = "Play sound on notifications",
                                    checked = sound,
                                    onCheckedChange = {
                                        sound = it
                                        setNotificationPref(context, PREF_SOUND, it)
                                        logSettingChange(PREF_SOUND, it)
                                    }
                                )

                                SectionDivider()

                                ToggleRow(
                                    icon = Icons.Outlined.Notifications,
                                    title = "LED Indicator",
                                    description = "Flash LED for notifications",
                                    checked = ledIndicator,
                                    onCheckedChange = {
                                        ledIndicator = it
                                        setNotificationPref(context, PREF_LED_INDICATOR, it)
                                        logSettingChange(PREF_LED_INDICATOR, it)
                                    }
                                )

                                SectionDivider()

                                ActionButton(
                                    text = "Test Notification",
                                    onClick = {
                                        // TODO: Send test notification
                                        logSettingChange("test_notification", "triggered")
                                    }
                                )

                                Spacer(Modifier.height(8.dp))

                                ActionButton(
                                    text = "Reset to Defaults",
                                    onClick = {
                                        resetToDefaults(context)
                                        // Reload all states
                                        liveMatchUpdates = true
                                        scoreChangesMode = 0
                                        wicketAlerts = true
                                        boundaryAlerts = true
                                        overCompletion = false
                                        inningsBreak = true
                                        matchResult = true
                                        favouriteTeamMatches = true
                                        teamSwitch = true
                                        preMatchReminders = true
                                        reminderTime = 30
                                        tossAlerts = true
                                        playingXIAnnounced = false
                                        breakingNews = true
                                        matchPreviews = false
                                        postMatchAnalysis = false
                                        playerMilestones = true
                                        showLiveActivity = false
                                        liveActivityStyle = 0
                                        enableQuietHours = false
                                        quietHoursStart = 22
                                        quietHoursEnd = 7
                                        vibration = true
                                        sound = true
                                        ledIndicator = false
                                        logSettingChange("reset_defaults", "triggered")
                                    },
                                    secondary = true
                                )
                            }
                        }
                    }
                }
            }

            // Bottom spacer
            item {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Composable Components
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = ShinyGoldDeep,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp
    )
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurface)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        content()
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ShinyGoldDeep,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        
        Spacer(Modifier.width(8.dp))
        
        PremiumSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SelectorRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ShinyGoldDeep,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        
        Spacer(Modifier.width(8.dp))
        
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(ShinyGoldDeep.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Change",
                color = ShinyGoldDeep,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PremiumSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = ShinyGoldDeep,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = SwitchTrackOff,
            checkedBorderColor = ShinyGoldDeep,
            uncheckedBorderColor = SwitchTrackOff
        )
    )
}

@Composable
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(1.dp)
            .background(BorderColor)
    )
}

@Composable
private fun InfoText(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 12.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LiveActivityPreview(style: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(BlackBackground)
            .border(1.dp, ShinyGoldDeep.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = "Preview: ${getLiveActivityStyleLabel(style)}",
                color = ShinyGoldDeep,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(8.dp))
            when (style) {
                0 -> Text("IND 145/3 (15.2)", color = TextPrimary, fontSize = 13.sp)
                1 -> {
                    Text("IND 145/3 (15.2)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Kohli 52* | Required: 78 from 56", color = TextSecondary, fontSize = 11.sp)
                }
                2 -> {
                    Text("IND 145/3 (15.2) • AUS 222/8", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Kohli 52* (38) | Starc 2/34", color = TextSecondary, fontSize = 11.sp)
                    Text("Required: 78 from 56 • RRR: 8.36", color = ShinyGoldDeep, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    secondary: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) CardSurface else ShinyGoldDeep,
            contentColor = if (secondary) ShinyGoldDeep else BlackBackground
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun AnimatedSection(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) + 
                slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
    ) {
        content()
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Helper Functions
// ──────────────────────────────────────────────────────────────────────────────

private fun getScoreChangesLabel(mode: Int): String = when (mode) {
    0 -> "Every Run"
    1 -> "Wickets Only"
    2 -> "Milestones"
    else -> "Every Run"
}

private fun getReminderTimeLabel(minutes: Int): String = when (minutes) {
    15 -> "15 minutes before"
    30 -> "30 minutes before"
    60 -> "1 hour before"
    120 -> "2 hours before"
    else -> "$minutes minutes before"
}

private fun getLiveActivityStyleLabel(style: Int): String = when (style) {
    0 -> "Compact"
    1 -> "Expanded"
    2 -> "Detailed"
    else -> "Compact"
}

private fun formatTime(hour: Int): String {
    val period = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$displayHour:00 $period"
}

// ──────────────────────────────────────────────────────────────────────────────
// Preferences
// ──────────────────────────────────────────────────────────────────────────────

private const val PREFS_NAME = "bouncer_notifications"

// Keys
private const val PREF_LIVE_MATCH_UPDATES = "live_match_updates"
private const val PREF_SCORE_CHANGES_MODE = "score_changes_mode"
private const val PREF_WICKET_ALERTS = "wicket_alerts"
private const val PREF_BOUNDARY_ALERTS = "boundary_alerts"
private const val PREF_OVER_COMPLETION = "over_completion"
private const val PREF_INNINGS_BREAK = "innings_break"
private const val PREF_MATCH_RESULT = "match_result"

private const val PREF_FAVOURITE_TEAM_MATCHES = "favourite_team_matches"
private const val PREF_TEAM_SWITCH = "team_switch"

private const val PREF_PRE_MATCH_REMINDERS = "pre_match_reminders"
private const val PREF_REMINDER_TIME = "reminder_time"
private const val PREF_TOSS_ALERTS = "toss_alerts"
private const val PREF_PLAYING_XI = "playing_xi"

private const val PREF_BREAKING_NEWS = "breaking_news"
private const val PREF_MATCH_PREVIEWS = "match_previews"
private const val PREF_POST_MATCH_ANALYSIS = "post_match_analysis"
private const val PREF_PLAYER_MILESTONES = "player_milestones"

private const val PREF_SHOW_LIVE_ACTIVITY = "show_live_activity"
private const val PREF_LIVE_ACTIVITY_STYLE = "live_activity_style"

private const val PREF_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
private const val PREF_QUIET_HOURS_START = "quiet_hours_start"
private const val PREF_QUIET_HOURS_END = "quiet_hours_end"

private const val PREF_VIBRATION = "vibration"
private const val PREF_SOUND = "sound"
private const val PREF_LED_INDICATOR = "led_indicator"

private fun getNotificationPref(context: Context, key: String, default: Boolean): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(key, default)
}

private fun getNotificationPref(context: Context, key: String, default: Int): Int {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(key, default)
}

private fun setNotificationPref(context: Context, key: String, value: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(key, value)
        .apply()
}

private fun setNotificationPref(context: Context, key: String, value: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putInt(key, value)
        .apply()
}

private fun resetToDefaults(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val editor = prefs.edit()
    
    editor.putBoolean(PREF_LIVE_MATCH_UPDATES, true)
    editor.putInt(PREF_SCORE_CHANGES_MODE, 0)
    editor.putBoolean(PREF_WICKET_ALERTS, true)
    editor.putBoolean(PREF_BOUNDARY_ALERTS, true)
    editor.putBoolean(PREF_OVER_COMPLETION, false)
    editor.putBoolean(PREF_INNINGS_BREAK, true)
    editor.putBoolean(PREF_MATCH_RESULT, true)
    
    editor.putBoolean(PREF_FAVOURITE_TEAM_MATCHES, true)
    editor.putBoolean(PREF_TEAM_SWITCH, true)
    
    editor.putBoolean(PREF_PRE_MATCH_REMINDERS, true)
    editor.putInt(PREF_REMINDER_TIME, 30)
    editor.putBoolean(PREF_TOSS_ALERTS, true)
    editor.putBoolean(PREF_PLAYING_XI, false)
    
    editor.putBoolean(PREF_BREAKING_NEWS, true)
    editor.putBoolean(PREF_MATCH_PREVIEWS, false)
    editor.putBoolean(PREF_POST_MATCH_ANALYSIS, false)
    editor.putBoolean(PREF_PLAYER_MILESTONES, true)
    
    editor.putBoolean(PREF_SHOW_LIVE_ACTIVITY, false)
    editor.putInt(PREF_LIVE_ACTIVITY_STYLE, 0)
    
    editor.putBoolean(PREF_QUIET_HOURS_ENABLED, false)
    editor.putInt(PREF_QUIET_HOURS_START, 22)
    editor.putInt(PREF_QUIET_HOURS_END, 7)
    
    editor.putBoolean(PREF_VIBRATION, true)
    editor.putBoolean(PREF_SOUND, true)
    editor.putBoolean(PREF_LED_INDICATOR, false)
    
    editor.apply()
}

private fun logSettingChange(key: String, value: Any) {
    try {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "notification_settings"
            message = "Setting changed"
            level = SentryLevel.INFO
            setData("key", key)
            setData("value", value.toString())
        })
    } catch (e: Exception) {
        Sentry.captureException(e)
    }
}
