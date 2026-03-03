package com.tanush.bouncer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanush.bouncer.ui.components.PremiumMatchListCard
import com.tanush.bouncer.ui.components.MatchCardSkeleton
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.viewmodels.LiveActivityViewModel

// =============================================================================
// Private composables for AllMatchesScreen premium design
// =============================================================================

/**
 * Gold gradient divider between sections — matches the mockup's etched gold line.
 */
@Composable
private fun GoldGradientDivider(
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    0.3f to colors.goldPrimary.copy(alpha = 0.4f),
                    0.5f to colors.goldPrimary.copy(alpha = 0.6f),
                    0.7f to colors.goldPrimary.copy(alpha = 0.4f),
                    1.0f to Color.Transparent
                )
            )
    )
}

/**
 * Section header with etched gold gradient text — "Live Now", "Upcoming", "Recent".
 * Matches mockup's Real-Time / Upcoming section headers with gold accent.
 */
@Composable
private fun SectionHeader(
    title: String,
    showLiveDot: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showLiveDot) {
            val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.statusLive.copy(alpha = pulseAlpha))
            )
        }
        Text(
            text = title.uppercase(),
            color = colors.goldPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.etchedText()
        )
    }
}

/**
 * Glass pill filter chip matching the mockup's glass-pill design.
 * Active: gold background with black text.
 * Inactive: glass surface with muted white text.
 */
@Composable
private fun GlassFilterPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) colors.goldPrimary else colors.glassSurface,
        animationSpec = tween(250),
        label = "pillBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) colors.backgroundBase else colors.textSecondary,
        animationSpec = tween(250),
        label = "pillText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colors.goldPrimary.copy(alpha = 0.6f) else colors.glassBorder,
        animationSpec = tween(250),
        label = "pillBorder"
    )

    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

// =============================================================================
// AllMatchesScreen — Premium Futuristic Glass Design
// =============================================================================

/**
 * AllMatchesScreen - Full list of all cricket matches with filtering.
 * Premium futuristic glass design with gold accents, stagger animations,
 * and section-based layout matching Analyst Feed mockup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllMatchesScreen(
    viewModel: LiveActivityViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val selectedStatus by viewModel.selectedStatus.collectAsState()
    val currentMatches by viewModel.currentMatches.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Analyst Feed",
                            color = colors.goldPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.etchedText()
                        )
                        Text(
                            text = "${currentMatches.size} matches available",
                            color = colors.textMuted,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .background(colors.glassSurface)
                            .border(1.dp, colors.goldPrimary.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.goldPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshMatches() },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.glassSurface)
                            .border(1.dp, colors.goldPrimary.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = colors.goldPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.backgroundBase.copy(alpha = 0.95f)
                )
            )
        }
    ) { padding ->
        GlassBloomBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .drawBehind {
                    // Gold bloom effect at the top — matches mockup's bg-bloom
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.goldPrimary.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(size.width / 2f, 0f),
                            radius = size.width * 0.8f
                        ),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.width * 0.8f
                    )
                },
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Filter Pills — mockup glass-pill row
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(listOf("Live", "Upcoming", "Recent")) { status ->
                        GlassFilterPill(
                            text = status,
                            isSelected = selectedStatus == status,
                            onClick = { viewModel.setAndFetchMatches(status) }
                        )
                    }
                }
            }

            // Gold gradient divider after filters
            item {
                GoldGradientDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Section header with etched gold text
            item {
                SectionHeader(
                    title = when (selectedStatus) {
                        "Live" -> "Live Now"
                        "Upcoming" -> "Upcoming"
                        else -> "Recent"
                    },
                    showLiveDot = selectedStatus == "Live",
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Match count sub-header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$selectedStatus Matches".uppercase(),
                        color = colors.textTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${currentMatches.size} results",
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // Match list with stagger animation
            if (isLoading && currentMatches.isEmpty()) {
                items(5) { index ->
                    Box(modifier = Modifier.staggeredFadeIn(index = index)) {
                        MatchCardSkeleton()
                    }
                }
            } else if (currentMatches.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.SportsCricket,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                "No matches found",
                                color = colors.textSecondary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Pull down to refresh or try a different filter",
                                color = colors.textMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(currentMatches, key = { _, match -> match.id }) { index, match ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(
                            animationSpec = tween(
                                durationMillis = 400,
                                delayMillis = index * 60,
                                easing = FastOutSlowInEasing
                            )
                        ) + slideInVertically(
                            animationSpec = tween(
                                durationMillis = 400,
                                delayMillis = index * 60,
                                easing = FastOutSlowInEasing
                            ),
                            initialOffsetY = { it / 4 }
                        )
                    ) {
                        PremiumMatchListCard(
                            match = match,
                            onClick = { /* Navigate to match detail */ }
                        )
                    }
                }
            }

            // Gold gradient divider at bottom
            item {
                GoldGradientDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
        }
    }
}
