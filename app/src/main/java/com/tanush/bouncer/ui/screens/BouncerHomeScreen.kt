package com.tanush.bouncer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.BouncerBottomNav
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.animations.staggeredFadeIn
import com.tanush.bouncer.ui.components.MatchCard
import com.tanush.bouncer.ui.components.NavItem
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.components.glass.GlassCardSmall
import com.tanush.bouncer.ui.components.glass.GlassHeroCard
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.BouncerTheme
import com.tanush.bouncer.ui.theme.TeamPrimaryColors
import com.tanush.bouncer.ui.theme.WindowSizeClass
import com.tanush.bouncer.ui.theme.rememberWindowSizeClass
import com.tanush.bouncer.ui.components.glass.TeamLogoCircle
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import com.tanush.bouncer.viewmodels.NewsViewModel
import com.tanush.bouncer.models.NewsArticleData
import kotlinx.coroutines.delay

// Constants
private val CarouselCardWidth = 320.dp

/**
 * ViewModel-based BouncerHomeScreen for backward compatibility with existing navigation.
 * This overload collects state from the ViewModel and delegates to the stateless composable.
 *
 * @param viewModel The LiveActivityViewModel that provides match data
 * @param onMatchClick Callback when a match is clicked, receives match ID as String
 * @param modifier Modifier for the screen
 */
@Composable
fun BouncerHomeScreen(
    viewModel: LiveActivityViewModel,
    onMatchClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val matches by viewModel.allMatchesForHome.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val windowSizeClass = rememberWindowSizeClass()

    BouncerHomeScreen(
        matches = matches,
        onMatchClick = { match -> onMatchClick(match.id) },
        onNavigate = { /* Navigation handled by parent */ },
        isLoading = isLoading || isRefreshing,
        onRefresh = { viewModel.refreshMatches() },
        modifier = modifier,
        windowSize = windowSizeClass,
        showBottomNav = false // Parent handles bottom nav
    )
}

/**
 * Immersive home dashboard matching the futuristic glass mockup design.
 *
 * Layout:
 * 1. HomeHeader - Bouncer branding + notification icon
 * 2. LiveMatchCarousel - Horizontal pager with snap scrolling
 * 3. TrendingNewsSection - Hero news card
 * 4. UpcomingMatchesList - Compact match cards
 * 5. Floating bottom nav
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BouncerHomeScreen(
    matches: List<CricketMatch>,
    onMatchClick: (CricketMatch) -> Unit,
    onNavigate: (NavItem) -> Unit,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    windowSize: WindowSizeClass = WindowSizeClass.COMPACT,
    selectedNavItem: NavItem = NavItem.HOME,
    showBottomNav: Boolean = true
) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    val pullToRefreshState = rememberPullToRefreshState()

    // Adjust grid/padding based on window size
    val horizontalPadding = if (windowSize == WindowSizeClass.EXPANDED) dimens.spacing.xxl else dimens.screenHorizontalPadding
    val listModifier = if (windowSize == WindowSizeClass.EXPANDED) {
        Modifier.fillMaxWidth(0.9f)
    } else {
        Modifier.fillMaxSize()
    }

    // Split matches into live and upcoming
    val liveMatches by remember(matches) {
        derivedStateOf { matches.filter { it.isLive } }
    }
    val upcomingMatches by remember(matches) {
        derivedStateOf { matches.filter { !it.isLive }.take(5) }
    }

    GlassBloomBackground(
        modifier = modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = isLoading && matches.isNotEmpty(),
                onRefresh = onRefresh,
                state = pullToRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                androidx.compose.animation.Crossfade(
                    targetState = isLoading && matches.isEmpty(),
                    animationSpec = tween(500, easing = FastOutSlowInEasing),
                    label = "homeContentCrossfade"
                ) { isInitialLoading ->
                    if (isInitialLoading) {
                        HomeScreenSkeleton()
                    } else {
                        LazyColumn(
                            modifier = listModifier,
                            contentPadding = PaddingValues(bottom = BouncerDesignSystem.dimens.bottomNavHeight + BouncerDesignSystem.dimens.spacing.md)
                        ) {
                            // Header
                            item {
                                HomeHeader(
                                    onNotificationClick = { /* TODO */ },
                                    modifier = Modifier.padding(
                                        horizontal = horizontalPadding,
                                        vertical = dimens.spacing.md
                                    )
                                )
                            }

                            // Live Match Carousel
                            if (liveMatches.isNotEmpty()) {
                                item {
                                    LiveMatchCarousel(
                                        matches = liveMatches,
                                        onMatchClick = onMatchClick,
                                        isExpanded = windowSize != WindowSizeClass.COMPACT
                                    )
                                }
                            }

                            // Trending News Section
                            item {
                                TrendingNewsSection(
                                    modifier = Modifier.padding(
                                        horizontal = horizontalPadding,
                                        vertical = dimens.spacing.md
                                    )
                                )
                            }

                            // Upcoming Matches Header
                            if (upcomingMatches.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = stringResource(R.string.section_more_matches),
                                        modifier = Modifier.padding(
                                            horizontal = horizontalPadding,
                                            vertical = dimens.spacing.sm
                                        )
                                    )
                                }

                                // Upcoming Matches List
                                itemsIndexed(
                                    items = upcomingMatches,
                                    key = { _, match -> match.id }
                                ) { index, match ->
                                    MatchCard(
                                        match = match,
                                        onClick = { onMatchClick(match) },
                                        isRefreshing = isLoading && matches.isNotEmpty(),
                                        modifier = Modifier
                                            .staggeredFadeIn(index = index + 5) // Offset for items above
                                            .padding(
                                                horizontal = horizontalPadding,
                                                vertical = dimens.spacing.xs
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
// ... remainder of file unchanged

            // Floating Bottom Nav (optional when parent handles it)
            if (showBottomNav) {
                BouncerBottomNav(
                    selectedItem = selectedNavItem,
                    onItemSelected = onNavigate,
                    onCenterClick = { onNavigate(NavItem.CENTER_ACTION) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun HomeScreenSkeleton() {
    val dimens = BouncerDesignSystem.dimens
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = dimens.bottomNavHeight + dimens.spacing.md)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.screenHorizontalPadding, vertical = dimens.spacing.md)
                    .height(40.dp)
                    .clip(RoundedCornerShape(dimens.radius.md))
                    .shimmerEffect()
            )
        }
        item {
            CarouselSkeleton()
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.screenHorizontalPadding, vertical = dimens.spacing.md)
                    .height(200.dp)
                    .clip(RoundedCornerShape(dimens.radius.xl))
                    .shimmerEffect()
            )
        }
        items(3) {
            MatchCardSkeleton(
                modifier = Modifier.padding(
                    horizontal = dimens.screenHorizontalPadding,
                    vertical = dimens.spacing.xs
                )
            )
        }
    }
}

// =============================================================================
// HomeHeader
// =============================================================================

@Composable
private fun HomeHeader(
    onNotificationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo / Brand
        Text(
            text = "Bouncer",
            style = typography.headingLarge,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.drawBehind {
                // Subtle gold underline accent
                drawLine(
                    color = colors.goldPrimary.copy(alpha = 0.6f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width * 0.6f, size.height),
                    strokeWidth = dimens.xxs.toPx()
                )
            }
        )

        // Notification bell
        GlassCard(
            modifier = Modifier.size(dimens.fabSizeSmall),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            borderOpacity = 0.15f
        ) {
            IconButton(
                onClick = onNotificationClick,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Notifications",
                    tint = colors.goldPrimary.copy(alpha = 0.8f),
                    modifier = Modifier.size(BouncerDesignSystem.dimens.iconSize.md)
                )
            }
        }
    }
}

// =============================================================================
// LiveMatchCarousel
// =============================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveMatchCarousel(
    matches: List<CricketMatch>,
    onMatchClick: (CricketMatch) -> Unit,
    isExpanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { matches.size })
    val dimens = BouncerDesignSystem.dimens
    val cardWidth = if (isExpanded) 480.dp else CarouselCardWidth
    val horizontalPadding = if (isExpanded) dimens.spacing.xxl else dimens.spacing.lg

    // Auto-scroll for live matches
    LaunchedEffect(pagerState, matches.size) {
        if (matches.size > 1) {
            while (true) {
                delay(7000) // Slightly longer delay for immersive reading
                if (!pagerState.isScrollInProgress) {
                    val nextPage = (pagerState.currentPage + 1) % matches.size
                    pagerState.animateScrollToPage(
                        page = nextPage,
                        animationSpec = tween(800, easing = FastOutSlowInEasing)
                    )
                }
            }
        }
    }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            pageSpacing = dimens.spacing.md,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            LiveMatchCarouselCard(
                match = matches[page],
                onClick = { onMatchClick(matches[page]) },
                modifier = Modifier
                    .width(cardWidth)
                    .height(dimens.heroCardHeight)
                    .graphicsLayer {
                        // Subtle 3D scale effect based on pager scroll
                        val pageOffset = (
                            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        ).coerceIn(-1f, 1f)
                        val scale = 1f - (kotlin.math.abs(pageOffset) * 0.05f)
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - (kotlin.math.abs(pageOffset) * 0.3f)
                    }
            )
        }

        // Page indicators matching _14/code.html dots
        if (matches.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = dimens.spacing.md),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(matches.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    PageIndicatorDot(
                        isSelected = isSelected,
                        modifier = Modifier.padding(horizontal = dimens.spacing.xs)
                    )
                }
            }
        }
    }
}

@Composable
private fun PageIndicatorDot(
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val width = if (isSelected) 16.dp else 6.dp
    val color = if (isSelected) colors.goldPrimary else colors.textTertiary.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .height(6.dp)
            .width(width)
            .clip(CircleShape)
            .background(color)
            .then(
                if (isSelected) Modifier.drawBehind {
                    drawCircle(
                        color = colors.goldPrimary.copy(alpha = 0.4f),
                        radius = size.maxDimension
                    )
                } else Modifier
            )
    )
}

@Composable
private fun LiveMatchCarouselCard(
    match: CricketMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val teams = match.teams
    val team1 = teams.getOrNull(0)
    val team2 = teams.getOrNull(1)

    // Glass panel with stadium background as per _14/code.html
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp)) // Matching rounded-[2rem]
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(32.dp)
            )
            .background(colors.glassSurface)
    ) {
        // Stadium Background
        AsyncImage(
            model = "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?q=80&w=640",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = 0.15f } // Grayscale/dark effect
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimens.spacing.lg),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top row: Live badge + Series name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiveBadge()
                Text(
                    text = match.matchTitle.take(20).uppercase(),
                    style = typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f),
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Teams and scores (Grid-like layout from _14)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team 1
                TeamColumnCompact(
                    teamName = team1?.acronym?.ifEmpty { team1.name.take(3).uppercase() } ?: "TM1",
                    teamCode = team1?.acronym?.ifEmpty { team1.name } ?: "",
                    logoUrl = team1?.logoUrl,
                    accentColor = TeamPrimaryColors.getTeamColor(team1?.acronym ?: team1?.name ?: "")
                )

                // Main Score
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val battingTeam = teams.find { it.isBatting } ?: team1
                    
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = battingTeam?.score?.split("/")?.getOrNull(0) ?: "-",
                            style = typography.scoreDisplay.copy(fontSize = 32.sp),
                            color = colors.goldPrimary,
                            fontWeight = FontWeight.Black
                        )
                        if (battingTeam?.score?.contains("/") == true) {
                            Text(
                                text = "/" + battingTeam.score.split("/").getOrNull(1),
                                style = typography.headingSmall,
                                color = colors.goldPrimary.copy(alpha = 0.5f),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }

                    Text(
                        text = battingTeam?.overs?.let { "$it Overs" } ?: "",
                        style = typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                // Team 2
                TeamColumnCompact(
                    teamName = team2?.acronym?.ifEmpty { team2.name.take(3).uppercase() } ?: "TM2",
                    teamCode = team2?.acronym?.ifEmpty { team2.name } ?: "",
                    logoUrl = team2?.logoUrl,
                    accentColor = TeamPrimaryColors.getTeamColor(team2?.acronym ?: team2?.name ?: "")
                )
            }

            // Bottom: Progress Bar + Win Probability
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((match.winProbability.toFloat() / 100f).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(colors.goldPrimary)
                            .drawBehind {
                                drawRect(
                                    color = colors.goldPrimary.copy(alpha = 0.5f),
                                    size = size
                                )
                            }
                    )
                }

                Spacer(modifier = Modifier.height(dimens.spacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = match.currentStatus.take(35),
                        style = typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(team1?.acronym ?: "TM1")}: ${match.winProbability.toInt()}%",
                        style = typography.labelSmall,
                        color = colors.goldPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamColumnCompact(
    teamName: String,
    teamCode: String,
    logoUrl: String?,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val dimens = BouncerDesignSystem.dimens
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.sm)
    ) {
        TeamLogoCircle(
            teamCode = teamCode,
            logoUrl = logoUrl,
            size = 48.dp,
            showBorder = true,
            showGlow = true
        )
        Text(
            text = teamName,
            style = BouncerDesignSystem.typography.labelMedium,
            color = accentColor,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    val colors = BouncerDesignSystem.colors
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(BouncerDesignSystem.dimens.radius.pill))
            .background(colors.statusLive.copy(alpha = 0.2f))
            .border(
                width = 1.dp,
                color = colors.statusLive.copy(alpha = 0.3f),
                shape = RoundedCornerShape(BouncerDesignSystem.dimens.radius.pill)
            )
            .padding(
                horizontal = BouncerDesignSystem.dimens.spacing.sm,
                vertical = BouncerDesignSystem.dimens.spacing.xs
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BouncerDesignSystem.dimens.spacing.xs)
    ) {
        // Pulsing dot
        Box(
            modifier = Modifier
                .size(BouncerDesignSystem.dimens.spacing.xs)
                .clip(CircleShape)
                .background(colors.statusLive)
                .graphicsLayer { alpha = pulseAlpha }
                .drawBehind {
                    drawCircle(
                        color = colors.statusLive.copy(alpha = 0.5f * pulseAlpha),
                        radius = size.minDimension
                    )
                }
        )

        Text(
            text = "LIVE",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = colors.statusLive,
            letterSpacing = 2.sp
        )
    }
}

// =============================================================================
// TrendingNewsSection
// =============================================================================

@Composable
private fun TrendingNewsSection(
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    val newsViewModel: NewsViewModel = viewModel()
    val articles by newsViewModel.articles.collectAsStateWithLifecycle()

    val heroArticle = articles.firstOrNull()
    val secondaryArticle = articles.getOrNull(1)

    Column(modifier = modifier) {
        SectionHeader(
            title = "Trending News",
            actionText = "View All",
            onActionClick = { /* Navigate to News tab */ }
        )

        Spacer(modifier = Modifier.height(dimens.spacing.md))

        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.md)) {
            // Hero news card
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable { /* TODO: navigate to article */ },
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(0.dp),
                borderOpacity = 0.1f
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = heroArticle?.imageUrl
                            ?: "https://images.unsplash.com/photo-1531415074968-036ba1b575da?q=80&w=640",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.6f }
                    )

                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.9f)
                                    )
                                )
                            )
                    )

                    // Content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(dimens.spacing.md),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top Story badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.goldPrimary.copy(alpha = 0.2f))
                                .border(
                                    width = 1.dp,
                                    color = colors.goldPrimary.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .align(Alignment.End)
                        ) {
                            Text(
                                text = (heroArticle?.storyType?.uppercase() ?: "TOP STORY"),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.goldPrimary,
                                letterSpacing = 1.sp
                            )
                        }

                        Column {
                            Text(
                                text = heroArticle?.headline ?: "Loading latest news...",
                                style = typography.headingMedium,
                                color = colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(dimens.spacing.xs))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Schedule,
                                        contentDescription = null,
                                        tint = colors.textTertiary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = heroArticle?.timeAgo ?: "",
                                        style = typography.labelSmall,
                                        color = colors.textTertiary
                                    )
                                }
                                if (heroArticle?.source?.isNotBlank() == true) {
                                    Box(
                                        modifier = Modifier
                                            .size(3.dp)
                                            .clip(CircleShape)
                                            .background(colors.textTertiary.copy(alpha = 0.4f))
                                    )
                                    Text(
                                        text = heroArticle.source,
                                        style = typography.labelSmall,
                                        color = colors.textTertiary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Compact secondary news card
            if (secondaryArticle != null) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clickable { /* TODO: navigate to article */ },
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(0.dp),
                    borderOpacity = 0.08f
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left content
                        Column(
                            modifier = Modifier
                                .weight(1.5f)
                                .padding(dimens.spacing.md),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.3f))
                                    .border(0.5.dp, colors.goldPrimary.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = secondaryArticle.storyType.uppercase().ifBlank { "NEWS" },
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.goldPrimary,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Text(
                                text = secondaryArticle.headline,
                                style = typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = secondaryArticle.timeAgo,
                                style = typography.labelSmall.copy(fontSize = 10.sp),
                                color = colors.goldPrimary.copy(alpha = 0.8f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // Right image
                        Box(modifier = Modifier.weight(1f)) {
                            AsyncImage(
                                model = secondaryArticle.imageUrl
                                    ?: "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?q=80&w=300",
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.5f }
                            )
                        }
                    }
                }
            }
        }
    }
}


// =============================================================================
// SectionHeader
// =============================================================================

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BouncerDesignSystem.dimens.spacing.sm)
        ) {
            // Gold accent bar
            Box(
                modifier = Modifier
                    .width(BouncerDesignSystem.dimens.xxs)
                    .height(BouncerDesignSystem.dimens.spacing.md)
                    .clip(RoundedCornerShape(BouncerDesignSystem.dimens.xxs))
                    .background(colors.goldPrimary)
                    .drawBehind {
                        drawRect(
                            color = colors.goldPrimary.copy(alpha = 0.5f),
                            size = size
                        )
                    }
            )

            Text(
                text = title.uppercase(),
                style = typography.labelLarge,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                style = typography.labelSmall,
                color = colors.goldPrimary.copy(alpha = 0.7f),
                modifier = Modifier.clickable(onClick = onActionClick)
            )
        }
    }
}

// =============================================================================
// UpcomingMatchCard
// =============================================================================

@Composable
private fun UpcomingMatchCard(
    match: CricketMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val teams = match.teams
    val team1 = teams.getOrNull(0)
    val team2 = teams.getOrNull(1)

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable(onClick = if (isRefreshing) ({}) else onClick),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(0.dp),
        borderOpacity = 0.08f
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimens.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Team Logos and Names
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.md)
            ) {
                // Overlapping circles as per _14
                Box(modifier = Modifier.width(50.dp)) {
                    // Team 2 (back)
                    TeamLogoCircle(
                        teamCode = team2?.acronym?.ifEmpty { team2.name } ?: "",
                        logoUrl = team2?.logoUrl,
                        size = 32.dp,
                        showBorder = true,
                        showGlow = false,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                    // Team 1 (front)
                    TeamLogoCircle(
                        teamCode = team1?.acronym?.ifEmpty { team1.name } ?: "",
                        logoUrl = team1?.logoUrl,
                        size = 32.dp,
                        showBorder = true,
                        showGlow = false,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .shadow(4.dp, CircleShape)
                    )
                }

                Column {
                    Text(
                        text = "${team1?.acronym?.ifEmpty { team1.name.take(3) } ?: "TM1"} vs ${team2?.acronym?.ifEmpty { team2.name.take(3) } ?: "TM2"}",
                        style = typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${match.matchType} • ${match.venue.take(20)}",
                        style = typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color.White.copy(alpha = 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Time remaining pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, colors.goldPrimary.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = match.matchTime.ifEmpty { "04h : 30m" },
                    style = typography.labelSmall,
                    color = colors.goldPrimary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// =============================================================================
// Skeletons
// =============================================================================

@Composable
private fun CarouselSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(horizontal = BouncerDesignSystem.dimens.spacing.lg)
            .fillMaxWidth()
            .height(BouncerDesignSystem.dimens.heroCardHeight)
            .clip(RoundedCornerShape(BouncerDesignSystem.dimens.xxxl))
            .background(Color.White.copy(alpha = 0.05f))
            .shimmerEffect()
    )
}

@Composable
private fun MatchCardSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BouncerDesignSystem.dimens.spacing.xxxl)
            .clip(RoundedCornerShape(BouncerDesignSystem.dimens.radius.md))
            .background(Color.White.copy(alpha = 0.05f))
            .shimmerEffect()
    )
}

@Composable
fun Modifier.shimmerEffect(): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val colors = BouncerDesignSystem.colors

    return this.drawBehind {
        val shimmerWidth = size.width * 0.6f
        val offset = shimmerOffset * (size.width + shimmerWidth) - shimmerWidth

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    colors.goldPrimary.copy(alpha = 0.12f), // Premium gold tint
                    Color.Transparent
                ),
                start = Offset(offset, 0f),
                end = Offset(offset + shimmerWidth, size.height)
            )
        )
    }
}

// =============================================================================
// Preview
// =============================================================================

@Preview(showBackground = true, backgroundColor = 0xFF08080A)
@Composable
private fun BouncerHomeScreenPreview() {
    val sampleMatches = listOf(
        CricketMatch(
            id = "1",
            seriesId = "s1",
            matchTitle = "T20 World Cup",
            matchType = "T20",
            status = "live",
            teams = listOf(
                CricketMatch.Team(
                    name = "India",
                    score = "142/3",
                    status = "batting",
                    isBatting = true,
                    logoUrl = null,
                    acronym = "IND",
                    overs = "16.2"
                ),
                CricketMatch.Team(
                    name = "West Indies",
                    score = "128/8",
                    status = "bowled",
                    isBatting = false,
                    logoUrl = null,
                    acronym = "WI",
                    overs = "20.0"
                )
            ),
            venue = "Eden Gardens",
            date = "2024-06-15",
            matchTime = "14:30",
            score = null,
            currentStatus = "India needs 32 runs in 22 balls",
            info = ""
        ),
        CricketMatch(
            id = "2",
            seriesId = "s2",
            matchTitle = "Ashes Series",
            matchType = "Test",
            status = "upcoming",
            teams = listOf(
                CricketMatch.Team("Australia", "", "", false, null, "AUS"),
                CricketMatch.Team("England", "", "", false, null, "ENG")
            ),
            venue = "Lord's, London",
            date = "2024-06-20",
            matchTime = "02:45",
            score = null,
            currentStatus = "Starts in 2 days",
            info = ""
        )
    )

    BouncerTheme(dynamicColor = false) {
        BouncerHomeScreen(
            matches = sampleMatches,
            onMatchClick = {},
            onNavigate = {},
            isLoading = false,
            onRefresh = {}
        )
    }
}
