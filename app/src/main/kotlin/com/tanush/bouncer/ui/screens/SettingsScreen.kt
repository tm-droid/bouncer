package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.components.glass.GlassIconButton
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.BouncerMotion
import com.tanush.bouncer.utils.PreferencesHelper
import com.tanush.bouncer.viewmodels.LandingTab

data class SettingsSection(
    val title: String,
    val items: List<SettingsItem>
)

sealed class SettingsItem {
    data class Toggle(
        val title: String,
        val description: String,
        val icon: ImageVector,
        val isEnabled: Boolean,
        val onToggle: (Boolean) -> Unit
    ) : SettingsItem()

    data class Navigation(
        val title: String,
        val description: String,
        val icon: ImageVector,
        val onClick: () -> Unit
    ) : SettingsItem()

    data class Info(
        val title: String,
        val value: String,
        val icon: ImageVector
    ) : SettingsItem()
}

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onAchievementsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onAppearanceClick: () -> Unit = {},
    onNotificationSettingsClick: (() -> Unit)? = null,
    onAIInsightsChanged: (Boolean) -> Unit = {}
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val ctx = LocalContext.current

    var liveNotifications by remember {
        mutableStateOf(
            PreferencesHelper.getBoolean(ctx, PreferencesHelper.KEY_LIVE_NOTIFICATIONS, true)
        )
    }
    var boundaryAlerts by remember {
        mutableStateOf(
            PreferencesHelper.getBoolean(ctx, PreferencesHelper.KEY_BOUNDARY_ALERTS, true)
        )
    }
    var wicketAlerts by remember {
        mutableStateOf(
            PreferencesHelper.getBoolean(ctx, PreferencesHelper.KEY_WICKET_ALERTS, true)
        )
    }
    var matchReminders by remember {
        mutableStateOf(
            PreferencesHelper.getBoolean(ctx, PreferencesHelper.KEY_MATCH_REMINDERS, false)
        )
    }
    var hapticFeedback by remember {
        mutableStateOf(
            PreferencesHelper.getBoolean(ctx, PreferencesHelper.KEY_HAPTIC_FEEDBACK, true)
        )
    }
    var autoRefresh by remember {
        mutableStateOf(
            PreferencesHelper.getBoolean(ctx, PreferencesHelper.KEY_AUTO_REFRESH, true)
        )
    }
    var showProbability by remember {
        mutableStateOf(
            PreferencesHelper.getBoolean(ctx, PreferencesHelper.KEY_SHOW_WIN_PROBABILITY, true)
        )
    }
    var reducedMotion by remember {
        mutableStateOf(
            PreferencesHelper.getBoolean(ctx, PreferencesHelper.KEY_REDUCED_MOTION, false)
        )
    }
    var aiInsights by remember {
        mutableStateOf(
            PreferencesHelper.getBoolean(ctx, PreferencesHelper.KEY_AI_INSIGHTS, false)
        )
    }
    var defaultLandingTab by remember { mutableStateOf(LandingTab.HOME) }
    var showLandingDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        BouncerMotion.applyReducedMotionPreference(reducedMotion)

        defaultLandingTab = when (
            PreferencesHelper.getString(
                ctx,
                PreferencesHelper.KEY_DEFAULT_LANDING,
                LandingTab.HOME.name
            )
        ) {
            LandingTab.ALL_MATCHES.name -> LandingTab.ALL_MATCHES
            LandingTab.FAVOURITES.name -> LandingTab.FAVOURITES
            else -> LandingTab.HOME
        }
    }

    val sections = listOf(
        SettingsSection(
            title = "Notifications",
            items = listOf(
                SettingsItem.Toggle(
                    title = "Live Match Alerts",
                    description = "Get notified when your favorite team is playing",
                    icon = Icons.Default.Notifications,
                    isEnabled = liveNotifications,
                    onToggle = {
                        liveNotifications = it
                        PreferencesHelper.setBoolean(ctx, PreferencesHelper.KEY_LIVE_NOTIFICATIONS, it)
                    }
                ),
                SettingsItem.Toggle(
                    title = "Boundary Alerts",
                    description = "Celebrate every four and six",
                    icon = Icons.Default.Celebration,
                    isEnabled = boundaryAlerts,
                    onToggle = {
                        boundaryAlerts = it
                        PreferencesHelper.setBoolean(ctx, PreferencesHelper.KEY_BOUNDARY_ALERTS, it)
                    }
                ),
                SettingsItem.Toggle(
                    title = "Wicket Alerts",
                    description = "Never miss a crucial wicket",
                    icon = Icons.Default.SportsCricket,
                    isEnabled = wicketAlerts,
                    onToggle = {
                        wicketAlerts = it
                        PreferencesHelper.setBoolean(ctx, PreferencesHelper.KEY_WICKET_ALERTS, it)
                    }
                ),
                SettingsItem.Toggle(
                    title = "Match Reminders",
                    description = "Remind me before matches start",
                    icon = Icons.Default.AccessTime,
                    isEnabled = matchReminders,
                    onToggle = {
                        matchReminders = it
                        PreferencesHelper.setBoolean(ctx, PreferencesHelper.KEY_MATCH_REMINDERS, it)
                    }
                ),
                SettingsItem.Toggle(
                    title = "AI insights",
                    description = "Enable short AI-generated insights in commentary and stats",
                    icon = Icons.Default.Memory,
                    isEnabled = aiInsights,
                    onToggle = {
                        aiInsights = it
                        PreferencesHelper.setBoolean(ctx, PreferencesHelper.KEY_AI_INSIGHTS, it)
                        onAIInsightsChanged(it)
                    }
                )
            ).let { items ->
                if (onNotificationSettingsClick != null) {
                    items + SettingsItem.Navigation(
                        title = "Notification Settings",
                        description = "Advanced notification preferences",
                        icon = Icons.Default.Settings,
                        onClick = onNotificationSettingsClick
                    )
                } else {
                    items
                }
            }
        ),
        SettingsSection(
            title = "Appearance",
            items = listOf(
                SettingsItem.Navigation(
                    title = "Appearance",
                    description = "Theme variants, skins, and display options",
                    icon = Icons.Default.Palette,
                    onClick = onAppearanceClick
                )
            )
        ),
        SettingsSection(
            title = "Data & Behavior",
            items = listOf(
                SettingsItem.Toggle(
                    title = "Haptic Feedback",
                    description = "Feel the game with vibrations",
                    icon = Icons.Default.Vibration,
                    isEnabled = hapticFeedback,
                    onToggle = {
                        hapticFeedback = it
                        PreferencesHelper.setBoolean(ctx, PreferencesHelper.KEY_HAPTIC_FEEDBACK, it)
                    }
                ),
                SettingsItem.Toggle(
                    title = "Reduced Motion",
                    description = "Use simpler transitions and fewer movement effects",
                    icon = Icons.Default.AccessTime,
                    isEnabled = reducedMotion,
                    onToggle = {
                        reducedMotion = it
                        PreferencesHelper.setBoolean(ctx, PreferencesHelper.KEY_REDUCED_MOTION, it)
                        BouncerMotion.applyReducedMotionPreference(it)
                    }
                ),
                SettingsItem.Toggle(
                    title = "Auto Refresh",
                    description = "Automatically update scores every 10 seconds",
                    icon = Icons.Default.Refresh,
                    isEnabled = autoRefresh,
                    onToggle = {
                        autoRefresh = it
                        PreferencesHelper.setBoolean(ctx, PreferencesHelper.KEY_AUTO_REFRESH, it)
                    }
                ),
                SettingsItem.Toggle(
                    title = "Win Probability",
                    description = "Show live win probability predictions",
                    icon = Icons.Default.Analytics,
                    isEnabled = showProbability,
                    onToggle = {
                        showProbability = it
                        PreferencesHelper.setBoolean(
                            ctx,
                            PreferencesHelper.KEY_SHOW_WIN_PROBABILITY,
                            it
                        )
                    }
                ),
                SettingsItem.Navigation(
                    title = "Default Landing Tab",
                    description = when (defaultLandingTab) {
                        LandingTab.ALL_MATCHES -> "Default: All Matches"
                        LandingTab.FAVOURITES -> "Default: Favorites"
                        LandingTab.HOME -> "Default: Home"
                    },
                    icon = Icons.Default.Home,
                    onClick = { showLandingDialog = true }
                ),
                SettingsItem.Navigation(
                    title = "Favorite Teams",
                    description = "Manage your favorite teams",
                    icon = Icons.Default.Favorite,
                    onClick = onFavoritesClick
                ),
                SettingsItem.Navigation(
                    title = "Achievements",
                    description = "View your cricket achievements",
                    icon = Icons.Default.EmojiEvents,
                    onClick = onAchievementsClick
                )
            )
        ),
        SettingsSection(
            title = "About",
            items = listOf(
                SettingsItem.Info(
                    title = "Version",
                    value = "1.0.0",
                    icon = Icons.Default.Info
                ),
                SettingsItem.Navigation(
                    title = "About Bouncer",
                    description = "Learn more about the app",
                    icon = Icons.Default.Description,
                    onClick = onAboutClick
                )
            )
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundBase)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = dimens.screenHorizontal, vertical = dimens.md),
                horizontalArrangement = Arrangement.spacedBy(dimens.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBackClick,
                    contentDescription = "Back",
                    useGoldTint = false
                )
                Column(verticalArrangement = Arrangement.spacedBy(dimens.xxs)) {
                    Text(
                        text = "Settings",
                        style = typography.headingMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Manage notifications, appearance, and app behavior",
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = dimens.xl,
                    end = dimens.xl,
                    top = dimens.sm,
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(dimens.xl)
            ) {
                items(sections) { section ->
                    SettingsSectionView(section)
                }
            }
        }

        if (showLandingDialog) {
            AlertDialog(
                onDismissRequest = { showLandingDialog = false },
                title = { Text(text = "Default Landing Tab") },
                text = {
                    Column {
                        val options = listOf(
                            LandingTab.HOME to "Home",
                            LandingTab.ALL_MATCHES to "All Matches",
                            LandingTab.FAVOURITES to "Favorites"
                        )
                        options.forEach { (tab, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        defaultLandingTab = tab
                                        PreferencesHelper.setString(
                                            ctx,
                                            PreferencesHelper.KEY_DEFAULT_LANDING,
                                            tab.name
                                        )
                                        showLandingDialog = false
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = defaultLandingTab == tab,
                                    onClick = {
                                        defaultLandingTab = tab
                                        PreferencesHelper.setString(
                                            ctx,
                                            PreferencesHelper.KEY_DEFAULT_LANDING,
                                            tab.name
                                        )
                                        showLandingDialog = false
                                    }
                                )
                                Text(
                                    text = label,
                                    style = typography.bodyMedium,
                                    color = colors.textPrimary,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLandingDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsSectionView(section: SettingsSection) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Column(verticalArrangement = Arrangement.spacedBy(dimens.sm)) {
        Text(
            text = section.title,
            style = typography.labelLarge,
            color = colors.textSecondary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = dimens.xs)
        )

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Column {
                section.items.forEachIndexed { index, item ->
                    when (item) {
                        is SettingsItem.Toggle -> SettingsToggleRow(item)
                        is SettingsItem.Navigation -> SettingsNavigationRow(item)
                        is SettingsItem.Info -> SettingsInfoRow(item)
                    }

                    if (index < section.items.lastIndex) {
                        HorizontalDivider(
                            color = colors.glassBorder,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 68.dp, end = dimens.lg)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(item: SettingsItem.Toggle) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors

    SettingsRow(
        title = item.title,
        description = item.description,
        icon = item.icon,
        iconTint = if (item.isEnabled) hierarchy.secondary.primary else colors.textMuted,
        trailing = {
            Switch(
                checked = item.isEnabled,
                onCheckedChange = item.onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.goldPrimary,
                    checkedTrackColor = colors.goldPrimary.copy(alpha = 0.35f),
                    uncheckedThumbColor = colors.textMuted,
                    uncheckedTrackColor = colors.glassBackground
                )
            )
        }
    )
}

@Composable
private fun SettingsNavigationRow(item: SettingsItem.Navigation) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors

    SettingsRow(
        title = item.title,
        description = item.description,
        icon = item.icon,
        iconTint = hierarchy.secondary.primary,
        onClick = item.onClick,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    )
}

@Composable
private fun SettingsInfoRow(item: SettingsItem.Info) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    SettingsRow(
        title = item.title,
        description = null,
        icon = item.icon,
        iconTint = colors.textSecondary,
        trailing = {
            Text(
                text = item.value,
                style = typography.bodyMedium,
                color = colors.textSecondary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(72.dp)
            )
        }
    )
}

@Composable
private fun SettingsRow(
    title: String,
    description: String?,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)? = null
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTouchTarget)
            .let {
                if (onClick != null) {
                    it.clickable(onClick = onClick).semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = listOfNotNull(title, description).joinToString(". ")
                    }
                } else {
                    it
                }
            }
            .padding(horizontal = dimens.lg, vertical = dimens.md),
        horizontalArrangement = Arrangement.spacedBy(dimens.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(colors.glassBackground, RoundedCornerShape(10.dp))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.xxs)
        ) {
            Text(
                text = title,
                style = typography.headingSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            if (!description.isNullOrEmpty()) {
                Text(
                    text = description,
                    style = typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }

        trailing()
    }
}
