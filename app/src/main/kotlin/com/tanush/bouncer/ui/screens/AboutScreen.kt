package com.tanush.bouncer.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AboutScreen(
    onBackClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "About",
                        style = typography.headingMedium,
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
        }
    ) { paddingValues ->
        GlassBloomBackground(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    start = dimens.screenHorizontal,
                    end = dimens.screenHorizontal,
                    top = dimens.sm,
                    bottom = dimens.xxl
                ),
                verticalArrangement = Arrangement.spacedBy(dimens.md)
            ) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(dimens.radius.card),
                        useGoldBorder = true,
                        contentPadding = PaddingValues(dimens.md)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(dimens.sm)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsCricket,
                                contentDescription = null,
                                tint = colors.goldPrimary,
                                modifier = Modifier.size(dimens.lg)
                            )
                            Text(
                                text = "Bouncer",
                                style = typography.headingLarge,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Live Cricket Scores",
                                style = typography.bodyMedium,
                                color = colors.textSecondary
                            )
                            Text(
                                text = "Version 1.0.0",
                                style = typography.bodySmall,
                                color = colors.textMuted
                            )
                        }
                    }
                }

                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        useGoldBorder = false,
                        contentPadding = PaddingValues(dimens.md)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(dimens.sm)) {
                            Text(
                                text = "What you get",
                                style = typography.headingSmall,
                                color = colors.textPrimary
                            )

                            FeatureRow(
                                icon = Icons.Default.Bolt,
                                title = "Real-time updates",
                                description = "Ball-by-ball scores with live context"
                            )
                            FeatureRow(
                                icon = Icons.Default.Analytics,
                                title = "Win probability",
                                description = "Model-powered match trends and momentum"
                            )
                            FeatureRow(
                                icon = Icons.Default.EmojiEvents,
                                title = "Achievements",
                                description = "Track milestones and notable moments"
                            )
                            FeatureRow(
                                icon = Icons.Default.Favorite,
                                title = "Favorites",
                                description = "Follow teams and get focused updates"
                            )
                        }
                    }
                }

                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        useGoldBorder = false,
                        contentPadding = PaddingValues(dimens.md)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(dimens.xs),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Built for cricket fans",
                                style = typography.headingSmall,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Crafted with clarity, speed, and passion for the game.",
                                style = typography.bodySmall,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(dimens.xs))
                            Text(
                                text = "2024",
                                style = typography.bodySmall,
                                color = colors.textMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.sm),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.goldPrimary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(dimens.sm + dimens.xs)
        )
        Column(verticalArrangement = Arrangement.spacedBy(dimens.xxs)) {
            Text(
                text = title,
                style = typography.bodyMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
}
