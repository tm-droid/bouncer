package com.tanush.bouncer.ui.screens

import com.tanush.bouncer.BuildConfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.SettingsItemV2
import com.tanush.bouncer.ui.components.v2.SettingsTrailing
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * MoreScreenV2 — Hub for secondary features (Explore grid + Settings list).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreenV2(
    onSettingsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onAchievementsClick: () -> Unit,
    onActivityClick: () -> Unit,
    onAboutClick: () -> Unit,
    onDevToolsClick: () -> Unit,
    onNewsClick: () -> Unit = {},
    onAppearanceClick: () -> Unit = {},
    onSeriesClick: () -> Unit = {},
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    data class ExploreItem(val icon: ImageVector, val label: String, val onClick: () -> Unit)

    val exploreItems = remember(onStatsClick, onAchievementsClick, onActivityClick, onSeriesClick) {
        listOf(
            ExploreItem(Icons.Default.BarChart, "Stats", onStatsClick),
            ExploreItem(Icons.Default.EmojiEvents, "Trophies", onStatsClick),
            ExploreItem(Icons.Default.SportsCricket, "Achievements", onAchievementsClick),
            ExploreItem(Icons.Default.Timeline, "Activity", onActivityClick),
            ExploreItem(Icons.Default.DateRange, "Series", onSeriesClick),
        )
    }

    Scaffold(
        topBar = { BouncerTopBarV2(title = "BOUNCER") },
        containerColor = colors.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = spacing.xxl),
        ) {
            // Explore section
            item(key = "explore_header") {
                SectionHeaderV2(
                    title = "Explore",
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                )
            }
            item(key = "explore_grid") {
                // Inline 2-column grid using Row
                val rows = exploreItems.chunked(2)
                Column(
                    modifier = Modifier.padding(horizontal = spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            row.forEach { item ->
                                ExploreCard(
                                    icon = item.icon,
                                    label = item.label,
                                    onClick = item.onClick,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Settings section
            item(key = "settings_header") {
                Spacer(Modifier.height(spacing.lg))
                SectionHeaderV2(
                    title = "Settings",
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                )
            }
            item(key = "settings_list") {
                Column(
                    modifier = Modifier.padding(horizontal = spacing.lg),
                ) {
                    SettingsItemV2(
                        icon = Icons.Default.Settings,
                        title = "Settings",
                        subtitle = "Preferences and notifications",
                        trailing = SettingsTrailing.Navigation,
                        onClick = onSettingsClick,
                    )
                    SettingsItemV2(
                        icon = Icons.Default.Palette,
                        title = "Appearance",
                        subtitle = "Theme and display",
                        trailing = SettingsTrailing.Navigation,
                        onClick = onAppearanceClick,
                    )
                    SettingsItemV2(
                        icon = Icons.Default.Info,
                        title = "About",
                        subtitle = "Version, licenses and credits",
                        trailing = SettingsTrailing.Navigation,
                        onClick = onAboutClick,
                    )
                    // Debug builds only. This row shipped in release, and the screen behind it
                    // led with an "Echo" button wired to a placeholder MCP client.
                    if (BuildConfig.DEBUG) {
                        SettingsItemV2(
                            icon = Icons.Default.Build,
                            title = "Dev Tools",
                            subtitle = "Diagnostics and debug",
                            trailing = SettingsTrailing.Navigation,
                            onClick = onDevToolsClick,
                        )
                    }
                }
            }

            // Version footer
            item(key = "version") {
                Spacer(Modifier.height(spacing.xl))
                Text(
                    text = "Version 3.0.0",
                    style = typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ExploreCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Card(
        onClick = onClick,
        modifier = modifier.height(96.dp),
        shape = BouncerThemeV2.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = BouncerThemeV2.elevation.card),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = colors.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = label,
                style = typography.labelMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
