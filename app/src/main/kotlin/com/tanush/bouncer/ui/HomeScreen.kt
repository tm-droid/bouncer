package com.tanush.bouncer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.core.CoilImage
import com.tanush.bouncer.ui.components.match.MatchCard
import com.tanush.bouncer.ui.components.core.MatchListSkeleton
import com.tanush.bouncer.ui.components.glass.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.GlassSurface
import com.tanush.bouncer.ui.theme.NeonError
import com.tanush.bouncer.ui.theme.StatusLive
import com.tanush.bouncer.ui.theme.TextPrimary
import com.tanush.bouncer.ui.theme.staggeredFadeIn
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import com.tanush.bouncer.viewmodels.LoadingState
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.utils.rememberHapticFeedback

// ==========================================
// BOUNCER – Home Screen v3.0
// Design Spec: android/design/BOUNCER_DESIGN_SYSTEM.md §7.2
//
// Layout:
//   64dp header (BOUNCER branding + live badge)
//   44dp segmented status tabs (animated indicator)
//   36dp category filter chips (horizontal scroll)
//   1dp divider
//   Sectioned match list:
//     • Live carousel (LazyRow) when live matches exist
//     • Upcoming section (vertical cards)
//     • Recent Results section (vertical cards)
//   Empty state / skeleton loaders
//   Pull-to-refresh with gold spinner
// ==========================================

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    viewModel: LiveActivityViewModel = viewModel(),
    onMatchClick: (CricketMatch) -> Unit,
    onMoreMatchesClick: () -> Unit,
    onAchievementsClick: () -> Unit,
    onLabClick: () -> Unit = {},
    onIntelligenceFeedClick: () -> Unit = {},
    onDevToolsClick: () -> Unit = {}
) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    val haptic = rememberHapticFeedback()
    val selectedStatus by viewModel.selectedStatus.collectAsState()
    val selectedCompetitionType by viewModel.selectedCompetitionType.collectAsState()
    val selectedGender by viewModel.selectedGender.collectAsState()
    val currentMatches by viewModel.currentMatches.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val loadingState by viewModel.loadingState.collectAsState()
    val aiCommentary by viewModel.aiCommentary.collectAsState()

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.refreshMatches() }
    )

    val listState = rememberLazyListState()

    // Derive active category from ViewModel state
    val selectedCategory = when {
        selectedGender == "Women" -> "Women"
        selectedCompetitionType != "All" -> selectedCompetitionType
        else -> "All"
    }

    // ── Categorize matches ──
    val liveMatches = currentMatches.filter { it.isLive }
    val upcomingMatches = currentMatches.filter {
        val s = it.status.lowercase()
        !it.isLive && (s.contains("upcoming") || s.contains("scheduled") || s.contains("not started"))
    }
    val recentMatches = currentMatches.filter {
        val s = it.status.lowercase()
        !it.isLive && (s.contains("complete") || s.contains("result") || s.contains("ended") || s.contains("won"))
    }

    GlassBloomBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            if (isLoading && currentMatches.isEmpty()) {
                // ── Skeleton loading state ──
                MatchListSkeleton(modifier = Modifier.padding(top = dimens.sm))
            } else if (loadingState is LoadingState.Error && currentMatches.isEmpty()) {
                // ── Error state (no cached data to show) ──
                ErrorMatchState(
                    errorMessage = (loadingState as LoadingState.Error).message,
                    onRetry = { viewModel.refreshMatches() }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── 1. Premium Header (64dp) ──
                    GoldHeader(
                        matchCount = liveMatches.size,
                        selectedStatus = selectedStatus
                    )

                    // ── 2. Status Segmented Control ──
                    StatusSegmentedControl(
                        statuses = listOf("Live", "Upcoming", "Recent", "Completed"),
                        selectedStatus = selectedStatus,
                        onStatusSelected = { status ->
                            haptic.click()
                            viewModel.setAndFetchMatches(status)
                        }
                    )

                    Spacer(modifier = Modifier.height(dimens.sm))

                    // ── 3. Category Filter Chips ──
                    CategoryChipRow(
                        selectedCategory = selectedCategory,
                        onCategorySelected = { category ->
                            haptic.click()
                            when (category) {
                                "All" -> {
                                    viewModel.setCompetitionType("All")
                                    viewModel.setGender("All")
                                }
                                "Women" -> {
                                    viewModel.setCompetitionType("All")
                                    viewModel.setGender("Women")
                                }
                                else -> {
                                    viewModel.setGender("All")
                                    viewModel.setCompetitionType(category)
                                }
                            }
                        }
                    )

                    // ── Divider (gold-tinted glass hairline) ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = dimens.sm)
                            .height(dimens.borderWidth)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        com.tanush.bouncer.ui.theme.DesignTokens.Color.goldPrimary().copy(alpha = 0.25f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // ── 4. Match Content (scrollable) ──
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = dimens.bottomNavHeight + dimens.spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(dimens.sm)
                    ) {
                        // ── Live Matches Carousel ──
                        if (liveMatches.isNotEmpty()) {
                            item(key = "live_section_header") {
                                MatchSectionHeader(
                                    title = "Live Now",
                                    count = liveMatches.size,
                                    showLiveDot = true
                                )
                            }
                            item(key = "live_carousel") {
                                LiveMatchesCarousel(
                                    matches = liveMatches,
                                    onMatchClick = onMatchClick
                                )
                            }
                        }

                        // ── Upcoming Section ──
                        if (upcomingMatches.isNotEmpty()) {
                            item(key = "upcoming_header") {
                                MatchSectionHeader(
                                    title = "Upcoming",
                                    count = upcomingMatches.size,
                                    onSeeAll = onMoreMatchesClick
                                )
                            }
                            itemsIndexed(
                                upcomingMatches,
                                key = { _, it -> "upcoming_${it.id}" }
                            ) { index, match ->
                                MatchCard(
                                    match = match,
                                    onClick = { onMatchClick(match) },
                                    liveAiCommentary = aiCommentary[match.id],
                                    modifier = Modifier
                                        .padding(horizontal = dimens.screenHorizontal)
                                        .staggeredFadeIn(index = index)
                                )
                            }
                        }

                        // ── Recent Results Section ──
                        if (recentMatches.isNotEmpty()) {
                            item(key = "recent_header") {
                                MatchSectionHeader(
                                    title = "Recent Results",
                                    count = recentMatches.size,
                                    onSeeAll = onMoreMatchesClick
                                )
                            }
                            itemsIndexed(
                                recentMatches,
                                key = { _, it -> "recent_${it.id}" }
                            ) { index, match ->
                                MatchCard(
                                    match = match,
                                    onClick = { onMatchClick(match) },
                                    liveAiCommentary = aiCommentary[match.id],
                                    modifier = Modifier
                                        .padding(horizontal = dimens.screenHorizontal)
                                        .staggeredFadeIn(index = index)
                                )
                            }
                        }

                        // ── Fallback: uncategorized matches ──
                        if (liveMatches.isEmpty() && upcomingMatches.isEmpty() &&
                            recentMatches.isEmpty() && currentMatches.isNotEmpty()
                        ) {
                            item(key = "fallback_header") {
                                MatchSectionHeader(
                                    title = "$selectedStatus Matches",
                                    count = currentMatches.size,
                                    onSeeAll = onMoreMatchesClick
                                )
                            }
                            itemsIndexed(
                                currentMatches,
                                key = { _, it -> "match_${it.id}" }
                            ) { index, match ->
                                MatchCard(
                                    match = match,
                                    onClick = { onMatchClick(match) },
                                    liveAiCommentary = aiCommentary[match.id],
                                    modifier = Modifier
                                        .padding(horizontal = dimens.screenHorizontal)
                                        .staggeredFadeIn(index = index)
                                )
                            }
                        }

                        // ── Empty State ──
                        if (currentMatches.isEmpty() && !isLoading) {
                            item(key = "empty_state") {
                                EmptyMatchState(
                                    selectedStatus = selectedStatus,
                                    onViewAll = onMoreMatchesClick
                                )
                            }
                        }

                        // ── Footer ──
                        item(key = "footer") {
                            AppFooter()
                        }
                    }
                }
            }

            // ── Gold-tinted pull-to-refresh ──
            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = colors.glassBackground,
                contentColor = colors.goldPrimary,
                scale = true
            )
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Status Segmented Control
// Animated pill indicator slides to active tab
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun StatusSegmentedControl(
    statuses: List<String>,
    selectedStatus: String,
    onStatusSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    val selectedIndex = statuses.indexOf(selectedStatus).coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenHorizontal)
            .padding(top = dimens.sm)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.glassBackground,
            border = BorderStroke(1.dp, colors.glassBorder)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(dimens.xs)
            ) {
                val tabWidth = maxWidth / statuses.size

                // Animated gold pill indicator
                val indicatorOffset by animateDpAsState(
                    targetValue = tabWidth * selectedIndex,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "tabIndicator"
                )

                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    colors.goldPrimary.copy(alpha = 0.18f),
                                    colors.goldPrimary.copy(alpha = 0.08f)
                                )
                            ),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            colors.goldPrimary.copy(alpha = 0.25f),
                            RoundedCornerShape(8.dp)
                        )
                )

                // Tab labels
                Row(modifier = Modifier.fillMaxSize()) {
                    statuses.forEachIndexed { index, status ->
                        val isSelected = index == selectedIndex
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) colors.goldPrimary else colors.textMuted,
                            animationSpec = tween(200),
                            label = "tabTextColor_$index"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onStatusSelected(status) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = status,
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                letterSpacing = if (isSelected) 0.5.sp else 0.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Category Filter Chips
// Horizontal scrollable: All, International, League, Domestic, Women
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun CategoryChipRow(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    val categories = listOf("All", "International", "League", "Domestic", "Women")

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = dimens.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(dimens.sm)
    ) {
        items(categories) { category ->
            val isSelected = category == selectedCategory

            val bgColor by animateColorAsState(
                targetValue = if (isSelected) colors.goldPrimary.copy(alpha = 0.14f) else Color.Transparent,
                animationSpec = tween(200),
                label = "chipBg_$category"
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) colors.goldPrimary.copy(alpha = 0.4f) else colors.glassBorder,
                animationSpec = tween(200),
                label = "chipBorder_$category"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) colors.goldPrimary else colors.textMuted,
                animationSpec = tween(200),
                label = "chipText_$category"
            )

            Surface(
                shape = RoundedCornerShape(50.dp),
                color = bgColor,
                border = BorderStroke(1.dp, borderColor),
                onClick = { onCategorySelected(category) }
            ) {
                Text(
                    text = category,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Live Matches Carousel — LazyRow of compact cards
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun LiveMatchesCarousel(
    matches: List<CricketMatch>,
    onMatchClick: (CricketMatch) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = BouncerDesignSystem.dimens
    // Shared pulse animation for gold borders
    val infiniteTransition = rememberInfiniteTransition(label = "carousel_border")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "carouselBorderAlpha"
    )

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = dimens.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(dimens.sm)
    ) {
        itemsIndexed(matches, key = { _, it -> "live_card_${it.id}" }) { index, match ->
            Box(modifier = Modifier.staggeredFadeIn(index = index)) {
                LiveMatchCarouselCard(
                    match = match,
                    borderAlpha = borderAlpha,
                    onClick = { onMatchClick(match) }
                )
            }
        }
    }
}

@Composable
private fun LiveMatchCarouselCard(
    match: CricketMatch,
    borderAlpha: Float,
    onClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)

    Surface(
        modifier = Modifier.width(320.dp),
        shape = RoundedCornerShape(24.dp),
        color = colors.glassBackground,
        border = BorderStroke(1.dp, colors.goldPrimary.copy(alpha = borderAlpha)),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(dimens.cardPadding)) {
            // ── Card header: title + live badge ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = match.matchTitle,
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(dimens.sm))
                LiveBadge()
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Teams & scores ──
            CarouselTeamRow(team = team1)
            Spacer(modifier = Modifier.height(10.dp))
            CarouselTeamRow(team = team2)

            // ── Status line ──
            if (match.currentStatus.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = match.currentStatus,
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CarouselTeamRow(team: CricketMatch.Team?) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Team logo + name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.sm),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(GlassSurface)
                    .border(
                        if (team?.isBatting == true) 1.5.dp else 0.5.dp,
                        if (team?.isBatting == true) colors.goldPrimary else colors.glassBorder,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                val logoUrl = TeamLogoManager.getBestLogoUrl(team?.name, team?.logoUrl, 56, 56)
                CoilImage(
                    model = logoUrl,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Text(
                text = team?.name ?: "TBD",
                color = if (team?.isBatting == true) colors.textPrimary else colors.textSecondary,
                fontWeight = if (team?.isBatting == true) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Score
        Text(
            text = team?.score ?: "",
            color = if (team?.isBatting == true) colors.goldPrimary else colors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun LiveBadge() {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "live_dot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveDotAlpha"
    )

    Surface(
        shape = RoundedCornerShape(50.dp),
        color = colors.statusLive.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer { alpha = dotAlpha }
                    .background(colors.statusLive, CircleShape)
            )
            Text(
                text = "LIVE",
                color = colors.statusLive,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Section Header — "Live Now" (3) with pulsing dot
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun MatchSectionHeader(
    title: String,
    count: Int = 0,
    showLiveDot: Boolean = false,
    onSeeAll: (() -> Unit)? = null
) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimens.screenHorizontal,
                vertical = dimens.md
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.sm)
        ) {
            // Gold accent bar (matches mockup: 1w × 4h gold bar)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                colors.goldPrimary,
                                colors.goldDark
                            )
                        ),
                        RoundedCornerShape(2.dp)
                    )
            )

            if (showLiveDot) {
                val infiniteTransition = rememberInfiniteTransition(label = "section_dot")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                    label = "sectionDotAlpha"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .background(colors.statusLive, CircleShape)
                )
            }

            Text(
                text = title.uppercase(),
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                    brush = Brush.linearGradient(
                        listOf(
                            TextPrimary,
                            colors.goldPrimary,
                            colors.goldDark
                        )
                    )
                )
            )

            if (count > 0) {
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = if (showLiveDot) colors.statusLive.copy(alpha = 0.10f) else GlassSurface
                ) {
                    Text(
                        text = "$count",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = if (showLiveDot) colors.statusLive else colors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Right-side action: 'See all' / chevron
        if (onSeeAll != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    onSeeAll()
                }
            ) {
                Text(
                    text = "See All",
                    color = colors.goldPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowForward,
                    contentDescription = "See All",
                    tint = colors.goldPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Empty State — No matches available
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun EmptyMatchState(
    selectedStatus: String,
    onViewAll: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    val typography = BouncerDesignSystem.typography
    val infiniteTransition = rememberInfiniteTransition(label = "empty_pulse")
    val iconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emptyIconAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenHorizontal)
            .padding(vertical = dimens.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.md)
    ) {
        // Decorative cricket icon in circle
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(colors.glassBackground, CircleShape)
                .border(1.dp, colors.glassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.SportsCricket,
                contentDescription = null,
                tint = colors.textMuted.copy(alpha = iconAlpha),
                modifier = Modifier.size(40.dp)
            )
        }

        Text(
            text = "No ${selectedStatus.lowercase()} matches",
            style = typography.headingMedium,
            color = colors.textSecondary,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "Pull down to refresh or try a different filter.",
            style = typography.bodySmall,
            color = colors.textMuted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(dimens.sm))

        GlassSecondaryButton(
            onClick = onViewAll,
            text = "View All Matches"
        )
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Error State — Network/fetch failure with retry
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun ErrorMatchState(
    errorMessage: String,
    onRetry: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    val typography = BouncerDesignSystem.typography
    val infiniteTransition = rememberInfiniteTransition(label = "error_pulse")
    val iconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "errorIconAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.screenHorizontal)
            .padding(vertical = dimens.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Error icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    StatusLive.copy(alpha = 0.10f),
                    CircleShape
                )
                .border(1.dp, StatusLive.copy(alpha = 0.20f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.WifiOff,
                contentDescription = "Error",
                tint = NeonError.copy(alpha = iconAlpha),
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(dimens.md))

        Text(
            text = "Something went wrong",
            style = typography.headingMedium,
            color = colors.textSecondary,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(dimens.sm))

        Text(
            text = errorMessage,
            style = typography.bodySmall,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = dimens.md)
        )

        Spacer(modifier = Modifier.height(dimens.lg))

        // Retry button with gold styling
        Surface(
            shape = RoundedCornerShape(50.dp),
            color = colors.goldPrimary.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, colors.goldPrimary.copy(alpha = 0.4f)),
            onClick = onRetry
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = colors.goldPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Try Again",
                    color = colors.goldPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
