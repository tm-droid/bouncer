package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

// ==========================================
// BOUNCER – More Hub Screen
// The 5th tab: gateway to Settings, Achievements,
// Activity Feed, AR Tracking, About, Dev Tools.
// ==========================================

private data class MoreItemData(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@Composable
fun MoreScreen(
    onSettingsClick: () -> Unit,
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
        MoreItemData(Icons.Default.Newspaper, "Cricket News", "Latest stories & updates", onNewsClick),
        MoreItemData(Icons.Default.Settings, "Settings", "Preferences, notifications, data", onSettingsClick),
        MoreItemData(Icons.Default.EmojiEvents, "Achievements", "Trophy room & milestones", onAchievementsClick),
        MoreItemData(Icons.Default.Insights, "Activity Feed", "Intelligence & AI insights", onActivityClick),
        MoreItemData(Icons.Default.ViewInAr, "AR Ball Tracking", "Augmented reality experience", onArTrackingClick)
    )

    val supportItems = listOf(
        MoreItemData(Icons.AutoMirrored.Filled.HelpCenter, "About Bouncer", "Version, licenses, credits", onAboutClick),
        MoreItemData(Icons.Default.Code, "Developer Tools", "API diagnostics & debug", onDevToolsClick)
    )

    GlassBloomBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Gold gradient header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = dimens.screenHorizontal, vertical = dimens.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MORE",
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
                item {
                    MoreSectionGroup(title = "EXPLORE", items = exploreItems)
                }
                item {
                    MoreSectionGroup(title = "SUPPORT", items = supportItems)
                }
            }
        }
    }
}

@Composable
private fun MoreSectionGroup(
    title: String,
    items: List<MoreItemData>
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Column(
        verticalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
    ) {
        // Section header with gold text
        Text(
            text = title,
            style = typography.labelLarge,
            color = colors.goldPrimary,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = dimens.xs, bottom = dimens.xs)
        )

        // Glass card container
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(0.dp)
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
                            modifier = Modifier.padding(horizontal = dimens.xl)
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
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.xl, vertical = dimens.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.lg)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(
                    colors.goldPrimary.copy(alpha = 0.12f),
                    RoundedCornerShape(12.dp)
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.goldPrimary.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.xxs)
        ) {
            Text(
                text = title,
                style = typography.headingSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                style = typography.bodySmall,
                color = colors.textMuted
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}
