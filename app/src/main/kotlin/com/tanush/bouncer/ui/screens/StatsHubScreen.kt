package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.components.*
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.components.glass.GlassIconButton
import com.tanush.bouncer.ui.components.glass.GlassSecondaryButton
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsHubScreen(
    onBackClick: () -> Unit,
    onOpenTrophy: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenAchievements: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Stats Hub", style = typography.headingMedium, color = colors.textPrimary)
                        Text("Trends and quick actions", style = typography.bodySmall, color = colors.textMuted)
                    }
                },
                navigationIcon = {
                    GlassIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onBackClick,
                        contentDescription = "Back"
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        GlassBloomBackground(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = dimens.screenHorizontalPadding,
                        vertical = dimens.md
                    )
                    .padding(bottom = dimens.bottomNavHeight + dimens.lg),
                verticalArrangement = Arrangement.spacedBy(dimens.cardGap)
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    importance = BouncerImportance.CRITICAL
                ) {
                    Column(modifier = Modifier.padding(dimens.md)) {
                        Text("Overview", style = typography.labelLarge, color = colors.textSecondary)
                        Spacer(modifier = Modifier.height(dimens.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(dimens.sm)) {
                            StatCard(label = "Matches", value = "1,234", trend = 2.3f)
                            StatCard(label = "Runs", value = "98,321", trend = 1.2f)
                            StatCard(label = "Wkts", value = "4,321", trend = -0.6f)
                        }
                    }
                }

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    importance = BouncerImportance.STANDARD
                ) {
                    Column(modifier = Modifier.padding(dimens.md)) {
                        Text("Trending", style = typography.labelLarge, color = colors.textPrimary)
                        Spacer(modifier = Modifier.height(dimens.sm))
                        MiniSparkline(data = listOf(120f, 130f, 125f, 140f, 150f, 160f), color = colors.goldPrimary)
                        Spacer(modifier = Modifier.height(dimens.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(dimens.sm)) {
                            StatCard(label = "Form", value = "+8%", trend = 8f)
                            StatCard(label = "Strike", value = "+3%", trend = 3f)
                        }
                    }
                }

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    importance = BouncerImportance.STANDARD
                ) {
                    Column(
                        modifier = Modifier.padding(dimens.md),
                        verticalArrangement = Arrangement.spacedBy(dimens.sm)
                    ) {
                        Text("Quick links", style = typography.labelLarge, color = colors.textPrimary)
                        StatsHubActionRow(
                            icon = Icons.Default.BarChart,
                            title = "Analytics",
                            description = "Open match trends and performance insights.",
                            actionLabel = "Open",
                            onAction = onOpenAnalytics
                        )
                        StatsHubActionRow(
                            icon = Icons.Default.EmojiEvents,
                            title = "Trophy Room",
                            description = "Browse cups, titles and milestone wins.",
                            actionLabel = "Open",
                            onAction = onOpenTrophy
                        )
                        StatsHubActionRow(
                            icon = Icons.Default.MilitaryTech,
                            title = "Achievements",
                            description = "Track badges and progression history.",
                            actionLabel = "Open",
                            onAction = onOpenAchievements
                        )
                    }
                }

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    importance = BouncerImportance.MINIMAL
                ) {
                    Column(modifier = Modifier.padding(dimens.md)) {
                        Text("Deep dive", style = typography.labelLarge, color = colors.textSecondary)
                        Spacer(modifier = Modifier.height(dimens.xs))
                        Text(
                            "Compare players, run filters, and jump into detailed match stats from this hub.",
                            style = typography.bodyMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsHubActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(dimens.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.goldPrimary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = typography.bodyMedium, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                Text(description, style = typography.bodySmall, color = colors.textMuted)
            }
            GlassSecondaryButton(text = actionLabel, onClick = onAction)
        }
    }
}
