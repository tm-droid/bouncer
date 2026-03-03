package com.tanush.bouncer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.components.glass.GlassCardLarge
import com.tanush.bouncer.ui.components.glass.GlassIconButton
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

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
    onAIInsightsChanged: (Boolean) -> Unit = {}
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val ctx = LocalContext.current

    // Preference states (persisted)
    var liveNotifications by remember { mutableStateOf(com.tanush.bouncer.utils.PreferencesHelper.getBoolean(ctx, com.tanush.bouncer.utils.PreferencesHelper.KEY_LIVE_NOTIFICATIONS, true)) }
    var boundaryAlerts by remember { mutableStateOf(com.tanush.bouncer.utils.PreferencesHelper.getBoolean(ctx, com.tanush.bouncer.utils.PreferencesHelper.KEY_BOUNDARY_ALERTS, true)) }
    var wicketAlerts by remember { mutableStateOf(com.tanush.bouncer.utils.PreferencesHelper.getBoolean(ctx, com.tanush.bouncer.utils.PreferencesHelper.KEY_WICKET_ALERTS, true)) }
    var matchReminders by remember { mutableStateOf(com.tanush.bouncer.utils.PreferencesHelper.getBoolean(ctx, com.tanush.bouncer.utils.PreferencesHelper.KEY_MATCH_REMINDERS, false)) }
    var hapticFeedback by remember { mutableStateOf(true) }
    var autoRefresh by remember { mutableStateOf(true) }
    var showProbability by remember { mutableStateOf(true) }
    var aiInsights by remember { mutableStateOf(com.tanush.bouncer.utils.PreferencesHelper.getBoolean(ctx, com.tanush.bouncer.utils.PreferencesHelper.KEY_AI_INSIGHTS, false)) }

    val sections = listOf(
        SettingsSection(
            title = "Notifications",
            items = listOf(
                SettingsItem.Toggle(
                    title = "Live Match Alerts",
                    description = "Get notified when your favorite team is playing",
                    icon = Icons.Default.Notifications,
                    isEnabled = liveNotifications,
                    onToggle = { liveNotifications = it; com.tanush.bouncer.utils.PreferencesHelper.setBoolean(ctx, com.tanush.bouncer.utils.PreferencesHelper.KEY_LIVE_NOTIFICATIONS, it) }
                ),
                SettingsItem.Toggle(
                    title = "Boundary Alerts",
                    description = "Celebrate every four and six",
                    icon = Icons.Default.Celebration,
                    isEnabled = boundaryAlerts,
                    onToggle = { boundaryAlerts = it; com.tanush.bouncer.utils.PreferencesHelper.setBoolean(ctx, com.tanush.bouncer.utils.PreferencesHelper.KEY_BOUNDARY_ALERTS, it) }
                ),
                SettingsItem.Toggle(
                    title = "Wicket Alerts",
                    description = "Never miss a crucial wicket",
                    icon = Icons.Default.SportsCricket,
                    isEnabled = wicketAlerts,
                    onToggle = { wicketAlerts = it; com.tanush.bouncer.utils.PreferencesHelper.setBoolean(ctx, com.tanush.bouncer.utils.PreferencesHelper.KEY_WICKET_ALERTS, it) }
                ),
                SettingsItem.Toggle(
                    title = "Match Reminders",
                    description = "Remind me before matches start",
                    icon = Icons.Default.AccessTime,
                    isEnabled = matchReminders,
                    onToggle = { matchReminders = it; com.tanush.bouncer.utils.PreferencesHelper.setBoolean(ctx, com.tanush.bouncer.utils.PreferencesHelper.KEY_MATCH_REMINDERS, it) }
                ),
                SettingsItem.Toggle(
                    title = "AI insights",
                    description = "Enable short AI-generated insights in commentary and stats",
                    icon = Icons.Default.Memory,
                    isEnabled = aiInsights,
                    onToggle = { aiInsights = it; com.tanush.bouncer.utils.PreferencesHelper.setBoolean(ctx, com.tanush.bouncer.utils.PreferencesHelper.KEY_AI_INSIGHTS, it); onAIInsightsChanged(it) }
                )
            )
        ),
        SettingsSection(
            title = "Preferences",
            items = listOf(
                SettingsItem.Toggle(
                    title = "Haptic Feedback",
                    description = "Feel the game with vibrations",
                    icon = Icons.Default.Vibration,
                    isEnabled = hapticFeedback,
                    onToggle = { hapticFeedback = it }
                ),
                SettingsItem.Toggle(
                    title = "Auto Refresh",
                    description = "Automatically update scores every 10 seconds",
                    icon = Icons.Default.Refresh,
                    isEnabled = autoRefresh,
                    onToggle = { autoRefresh = it }
                ),
                SettingsItem.Toggle(
                    title = "Win Probability",
                    description = "Show live win probability predictions",
                    icon = Icons.Default.Analytics,
                    isEnabled = showProbability,
                    onToggle = { showProbability = it }
                )
            )
        ),
        SettingsSection(
            title = "Account",
            items = listOf(
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

    GlassBloomBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Glass Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = dimens.screenHorizontal, vertical = dimens.sm),
                horizontalArrangement = Arrangement.spacedBy(dimens.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBackClick,
                    contentDescription = "Back",
                    useGoldTint = false
                )
                Text(
                    text = "SETTINGS",
                    style = typography.headingMedium,
                    color = colors.goldPrimary,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Black
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = dimens.lg,
                    start = dimens.xl,
                    end = dimens.xl,
                    bottom = 140.dp
                ),
                verticalArrangement = Arrangement.spacedBy(dimens.xxl)
            ) {
                // Profile Header
                item {
                    SettingsProfileHeader()
                }

                // Settings Sections
                sections.forEach { section ->
                    item {
                        SettingsSectionView(section = section)
                    }
                }

                // Footer
                item {
                    SettingsFooter()
                }
            }
        }
    }
}

@Composable
private fun SettingsProfileHeader() {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    val infiniteTransition = rememberInfiniteTransition(label = "profileGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    GlassCardLarge(
        modifier = Modifier.fillMaxWidth(),
        useGoldBorder = true
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                colors.goldPrimary.copy(alpha = glowAlpha),
                                colors.goldPrimary.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    colors.goldLight,
                                    colors.goldPrimary
                                )
                            ),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = colors.backgroundBase,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(dimens.xs)
            ) {
                Text(
                    text = "Cricket Fan",
                    style = typography.headingMedium,
                    color = colors.textPrimary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(dimens.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                colors.goldPrimary.copy(alpha = 0.15f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = dimens.sm, vertical = 3.dp)
                    ) {
                        Text(
                            text = "PREMIUM",
                            style = typography.labelSmall,
                            color = colors.goldPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = "Since 2024",
                        style = typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionView(section: SettingsSection) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Column(
        verticalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
    ) {
        // Section Header
        Text(
            text = section.title.uppercase(),
            style = typography.labelLarge,
            color = colors.goldPrimary,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = dimens.xs, bottom = dimens.xs)
        )

        // Section Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
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
                            modifier = Modifier.padding(horizontal = dimens.xl)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(item: SettingsItem.Toggle) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.xl, vertical = dimens.lg),
        horizontalArrangement = Arrangement.spacedBy(dimens.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (item.isEnabled) colors.goldPrimary.copy(alpha = 0.12f)
                    else colors.glassBackground,
                    RoundedCornerShape(12.dp)
                )
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (item.isEnabled) colors.goldPrimary else colors.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.xxs)
        ) {
            Text(
                text = item.title,
                style = typography.headingSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = item.description,
                style = typography.bodySmall,
                color = colors.textMuted
            )
        }

        Switch(
            checked = item.isEnabled,
            onCheckedChange = item.onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.goldPrimary,
                checkedTrackColor = colors.goldPrimary.copy(alpha = 0.3f),
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.glassBackground
            )
        )
    }
}

@Composable
private fun SettingsNavigationRow(item: SettingsItem.Navigation) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(horizontal = dimens.xl, vertical = dimens.lg),
        horizontalArrangement = Arrangement.spacedBy(dimens.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(colors.goldPrimary.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = colors.goldPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.xxs)
        ) {
            Text(
                text = item.title,
                style = typography.headingSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = item.description,
                style = typography.bodySmall,
                color = colors.textMuted
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun SettingsInfoRow(item: SettingsItem.Info) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.xl, vertical = dimens.lg),
        horizontalArrangement = Arrangement.spacedBy(dimens.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(colors.glassBackground, RoundedCornerShape(12.dp))
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        Text(
            text = item.title,
            style = typography.headingSmall,
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = item.value,
            style = typography.bodyMedium,
            color = colors.textMuted
        )
    }
}

@Composable
private fun SettingsFooter() {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.sm)
    ) {
        // Decorative line
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            colors.goldPrimary.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    ),
                    RoundedCornerShape(50)
                )
        )

        Spacer(modifier = Modifier.height(dimens.sm))

        Text(
            text = "Made with passion for cricket",
            style = typography.bodySmall,
            color = colors.textMuted
        )

        Text(
            text = "Bouncer v1.0",
            style = typography.labelSmall,
            color = colors.goldPrimary.copy(alpha = 0.6f),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
    }
}
