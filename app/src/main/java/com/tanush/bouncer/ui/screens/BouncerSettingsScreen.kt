package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.BouncerDimens
import com.tanush.bouncer.ui.theme.etchedText
import com.tanush.bouncer.utils.PreferencesHelper

// ─────────────────────────────────────────────────────────────────────────────
// Main Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BouncerSettingsScreen(
    onBackClick: () -> Unit,
    onFavouritesClick: () -> Unit,
    onAboutClick: () -> Unit,
    onAIInsightsChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // ── Toggle states using PreferencesHelper generic getBoolean/setBoolean ──
    var liveMatchAlerts by remember {
        mutableStateOf(
            PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_LIVE_NOTIFICATIONS, false)
        )
    }
    var boundaryAlerts by remember {
        mutableStateOf(
            PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_BOUNDARY_ALERTS, false)
        )
    }
    var wicketAlerts by remember {
        mutableStateOf(
            PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_WICKET_ALERTS, false)
        )
    }
    var matchReminders by remember {
        mutableStateOf(
            PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_MATCH_REMINDERS, false)
        )
    }
    var aiInsights by remember {
        mutableStateOf(
            PreferencesHelper.getBoolean(context, PreferencesHelper.KEY_AI_INSIGHTS, false)
        )
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        "Settings",
                        modifier = Modifier.etchedText()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = BouncerDesignSystem.colors.goldPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = BouncerDesignSystem.colors.backgroundBase,
                    scrolledContainerColor = BouncerDesignSystem.colors.backgroundElevated
                ),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = BouncerDesignSystem.colors.backgroundBase
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            // ─────────────────────────────────────────────────────────────
            // Section: Notifications
            // ─────────────────────────────────────────────────────────────
            item(key = "section_notifications") {
                SettingsSectionHeader(title = "Notifications")
            }

            item(key = "live_match_alerts") {
                SettingsToggleRow(
                    icon = Icons.Outlined.Notifications,
                    title = "Live Match Alerts",
                    description = "Get notified when a match goes live",
                    checked = liveMatchAlerts,
                    onCheckedChange = { enabled ->
                        liveMatchAlerts = enabled
                        PreferencesHelper.setBoolean(
                            context, PreferencesHelper.KEY_LIVE_NOTIFICATIONS, enabled
                        )
                    }
                )
            }

            item(key = "boundary_alerts") {
                SettingsItemDivider()
                SettingsToggleRow(
                    icon = Icons.Outlined.SportsCricket,
                    title = "Boundary Alerts",
                    description = "Alerts for fours and sixes",
                    checked = boundaryAlerts,
                    onCheckedChange = { enabled ->
                        boundaryAlerts = enabled
                        PreferencesHelper.setBoolean(
                            context, PreferencesHelper.KEY_BOUNDARY_ALERTS, enabled
                        )
                    }
                )
            }

            item(key = "wicket_alerts") {
                SettingsItemDivider()
                SettingsToggleRow(
                    icon = Icons.Outlined.Warning,
                    title = "Wicket Alerts",
                    description = "Alerts for wicket events",
                    checked = wicketAlerts,
                    onCheckedChange = { enabled ->
                        wicketAlerts = enabled
                        PreferencesHelper.setBoolean(
                            context, PreferencesHelper.KEY_WICKET_ALERTS, enabled
                        )
                    }
                )
            }

            item(key = "match_reminders") {
                SettingsItemDivider()
                SettingsToggleRow(
                    icon = Icons.Outlined.Schedule,
                    title = "Match Reminders",
                    description = "Reminders before matches start",
                    checked = matchReminders,
                    onCheckedChange = { enabled ->
                        matchReminders = enabled
                        PreferencesHelper.setBoolean(
                            context, PreferencesHelper.KEY_MATCH_REMINDERS, enabled
                        )
                    }
                )
            }

            // ─────────────────────────────────────────────────────────────
            // Section: AI & Analysis
            // ─────────────────────────────────────────────────────────────
            item(key = "section_ai") {
                SettingsSectionHeader(title = "AI & Analysis")
            }

            item(key = "ai_commentary") {
                SettingsToggleRow(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "AI Commentary",
                    description = "AI-powered commentary fusion (requires API key)",
                    checked = aiInsights,
                    onCheckedChange = { enabled ->
                        aiInsights = enabled
                        PreferencesHelper.setBoolean(
                            context, PreferencesHelper.KEY_AI_INSIGHTS, enabled
                        )
                        onAIInsightsChanged(enabled)
                    }
                )
            }

            // ─────────────────────────────────────────────────────────────
            // Section: General
            // ─────────────────────────────────────────────────────────────
            item(key = "section_general") {
                SettingsSectionHeader(title = "General")
            }

            item(key = "favourite_teams") {
                SettingsNavigationRow(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = "Favourite Teams",
                    description = "Manage your favourite teams",
                    onClick = onFavouritesClick
                )
            }

            // ─────────────────────────────────────────────────────────────
            // Section: About
            // ─────────────────────────────────────────────────────────────
            item(key = "section_about") {
                SettingsSectionHeader(title = "About")
            }

            item(key = "about_bouncer") {
                SettingsNavigationRow(
                    icon = Icons.Outlined.Info,
                    title = "About Bouncer",
                    description = "App information and credits",
                    onClick = onAboutClick
                )
            }

            item(key = "version") {
                SettingsItemDivider()
                SettingsInfoRow(
                    icon = Icons.Outlined.Update,
                    title = "Version",
                    value = "1.0.0"
                )
            }

            // ─────────────────────────────────────────────────────────────
            // Footer
            // ─────────────────────────────────────────────────────────────
            item(key = "footer") {
                Spacer(modifier = Modifier.height(BouncerDimens.xxxl))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = BouncerDimens.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Bouncer v1.0.0",
                        style = BouncerDesignSystem.typography.bodySmall,
                        color = BouncerDesignSystem.colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(BouncerDimens.xs))
                    Text(
                        text = "Made with love for cricket fans",
                        style = BouncerDesignSystem.typography.bodySmall,
                        color = BouncerDesignSystem.colors.textMuted
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.padding(
            start = BouncerDimens.screenHorizontal,
            end = BouncerDimens.screenHorizontal,
            top = BouncerDimens.xxl,
            bottom = BouncerDimens.sm
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gold accent bar
        Spacer(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(colors.goldLight, colors.goldPrimary, colors.goldDark)
                    )
                )
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = colors.goldPrimary,
            modifier = Modifier.etchedText()
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Toggle row (switch)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = BouncerDimens.screenHorizontal,
                vertical = 4.dp
            )
            .clip(RoundedCornerShape(12.dp))
            .background(colors.glassSurface)
            .border(1.dp, colors.glassBorder, RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(
                horizontal = BouncerDimens.screenHorizontal,
                vertical = BouncerDimens.md
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = colors.goldPrimary.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(BouncerDimens.lg))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = BouncerDesignSystem.typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                style = BouncerDesignSystem.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(BouncerDimens.md))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.backgroundBase,
                checkedTrackColor = colors.goldPrimary,
                uncheckedThumbColor = colors.textTertiary,
                uncheckedTrackColor = colors.surface2
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Navigation row (clickable with chevron)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = BouncerDimens.screenHorizontal,
                vertical = 4.dp
            )
            .clip(RoundedCornerShape(12.dp))
            .background(colors.glassSurface)
            .border(1.dp, colors.glassBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(
                horizontal = BouncerDimens.screenHorizontal,
                vertical = BouncerDimens.md
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = colors.goldPrimary.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(BouncerDimens.lg))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = BouncerDesignSystem.typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                style = BouncerDesignSystem.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(BouncerDimens.sm))

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = "Navigate to $title",
            tint = colors.goldPrimary.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Info row (static value display)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    title: String,
    value: String
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = BouncerDimens.screenHorizontal,
                vertical = 4.dp
            )
            .clip(RoundedCornerShape(12.dp))
            .background(colors.glassSurface)
            .border(1.dp, colors.glassBorder, RoundedCornerShape(12.dp))
            .padding(
                horizontal = BouncerDimens.screenHorizontal,
                vertical = BouncerDimens.md
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = colors.goldPrimary.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(BouncerDimens.lg))

        Text(
            text = title,
            style = BouncerDesignSystem.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            style = BouncerDesignSystem.typography.bodySmall,
            color = colors.goldPrimary
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Intra-section divider
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsItemDivider() {
    Spacer(modifier = Modifier.height(2.dp))
}
