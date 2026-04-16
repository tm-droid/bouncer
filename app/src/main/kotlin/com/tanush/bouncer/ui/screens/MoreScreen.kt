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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

private data class MoreItemData(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@Composable
fun MoreScreen(
    onSettingsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onAchievementsClick: () -> Unit,
    onActivityClick: () -> Unit,
    onArTrackingClick: () -> Unit,
    onAboutClick: () -> Unit,
    onDevToolsClick: () -> Unit,
    onNewsClick: () -> Unit = {}
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    val exploreItems = listOf(
        MoreItemData(Icons.Default.Newspaper, "Cricket News", "Latest stories and updates", onNewsClick),
        MoreItemData(Icons.Default.BarChart, "Stats", "Analytics, trophies and milestones", onStatsClick),
        MoreItemData(Icons.Default.EmojiEvents, "Achievements", "Unlocks and progress badges", onAchievementsClick),
        MoreItemData(Icons.Default.Insights, "Activity Feed", "Recent insights and momentum events", onActivityClick),
        MoreItemData(Icons.Default.ViewInAr, "AR Ball Tracking", "Augmented reality experience", onArTrackingClick)
    )

    val settingsItems = listOf(
        MoreItemData(Icons.Default.Settings, "Settings", "Preferences and notifications", onSettingsClick),
        MoreItemData(Icons.AutoMirrored.Filled.HelpCenter, "About Bouncer", "Version, licenses and credits", onAboutClick),
        MoreItemData(Icons.Default.Code, "Developer Tools", "Diagnostics and debug utilities", onDevToolsClick)
    )

    GlassBloomBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = dimens.sm,
                start = dimens.screenHorizontal,
                end = dimens.screenHorizontal,
                bottom = dimens.bottomNavHeight + dimens.lg
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.lg)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(dimens.xs)
                ) {
                    Text(
                        text = "More",
                        style = typography.headingLarge,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Shortcuts, preferences, and app information in one place.",
                        style = typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }

            item {
                MoreSectionGroup(
                    title = "Explore",
                    subtitle = "Discover content and match intelligence",
                    items = exploreItems
                )
            }

            item {
                MoreSectionGroup(
                    title = "Support",
                    subtitle = "Manage app settings and information",
                    items = settingsItems
                )
            }
        }
    }
}

@Composable
private fun MoreSectionGroup(
    title: String,
    subtitle: String,
    items: List<MoreItemData>
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Column(verticalArrangement = Arrangement.spacedBy(dimens.sm)) {
        Text(
            text = title,
            style = typography.headingSmall,
            color = colors.textPrimary
        )
        Text(
            text = subtitle,
            style = typography.bodySmall,
            color = colors.textSecondary
        )

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(dimens.radius.card),
            contentPadding = PaddingValues(0.dp),
            useGoldBorder = false
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    MoreMenuItem(
                        icon = item.icon,
                        title = item.title,
                        subtitle = item.subtitle,
                        onClick = item.onClick
                    )
                    if (index < items.lastIndex) {
                        HorizontalDivider(
                            color = colors.glassBorder,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = dimens.md)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$title. $subtitle"
            }
            .padding(horizontal = dimens.md, vertical = dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.sm)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(dimens.md + dimens.sm)
                .background(colors.goldPrimary.copy(alpha = 0.14f), RoundedCornerShape(dimens.radius.sm))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.goldPrimary,
                modifier = Modifier.size(dimens.sm + dimens.xs)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.xxs)
        ) {
            Text(
                text = title,
                style = typography.bodyMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = typography.bodySmall,
                color = colors.textSecondary
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(dimens.sm + dimens.xs)
        )
    }
}
