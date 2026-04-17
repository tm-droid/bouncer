package com.tanush.bouncer.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.tanush.bouncer.models.NewsArticleData
import com.tanush.bouncer.ui.components.BouncerNewsSkeleton
import com.tanush.bouncer.ui.components.EmptyStateView
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.components.glass.GlassCardSmall
import com.tanush.bouncer.ui.components.glass.GlassChipRow
import com.tanush.bouncer.ui.components.glass.GlassIconButton
import com.tanush.bouncer.ui.components.glass.GlassSecondaryButton
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.staggeredFadeIn
import com.tanush.bouncer.viewmodels.NewsViewModel
import com.tanush.bouncer.ui.theme.GoldPrimary

private val categoryChips = listOf("All", "Latest", "Spotlight", "Trending")

// =============================================================================
// News Screen
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    onBackClick: () -> Unit,
    newsViewModel: NewsViewModel,
    onArticleClick: (NewsArticleData) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    val articles by newsViewModel.articles.collectAsStateWithLifecycle()
    val isLoading by newsViewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by newsViewModel.isRefreshing.collectAsStateWithLifecycle()
    val error by newsViewModel.error.collectAsStateWithLifecycle()

    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    val filteredArticles = remember(selectedCategoryIndex, articles) {
        if (selectedCategoryIndex == 0) {
            articles
        } else {
            val category = categoryChips[selectedCategoryIndex]
            articles.filter { article ->
                article.storyType.contains(category, ignoreCase = true)
            }
        }
    }

    val heroArticle = filteredArticles.firstOrNull()
    val regularArticles = filteredArticles.drop(1)

    val pullToRefreshState = rememberPullToRefreshState()

    GlassBloomBackground(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── Header ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = dimens.screenHorizontalPadding,
                        vertical = dimens.sm
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    onClick = onBackClick,
                    contentDescription = "Back"
                )
                Spacer(modifier = Modifier.width(dimens.md))
                Column(verticalArrangement = Arrangement.spacedBy(dimens.xxs)) {
                    Text(
                        text = "News",
                        style = typography.headingLarge,
                        color = GoldPrimary
                    )
                    Text(
                        text = "${filteredArticles.size} stories",
                        style = typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            }

            // ── Category Chips ──────────────────────────────────────
            GlassChipRow(
                items = categoryChips,
                selectedIndex = selectedCategoryIndex,
                onSelect = { selectedCategoryIndex = it },
                modifier = Modifier.padding(
                    horizontal = dimens.screenHorizontalPadding,
                    vertical = dimens.sm
                )
            )

            // ── Content ─────────────────────────────────────────────
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { newsViewModel.refreshNews() },
                state = pullToRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    isLoading && articles.isEmpty() -> {
                        // Loading shimmer skeletons
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = dimens.screenHorizontalPadding,
                                end = dimens.screenHorizontalPadding,
                                top = dimens.sm,
                                bottom = dimens.bottomNavHeight + dimens.lg
                            ),
                            verticalArrangement = Arrangement.spacedBy(dimens.cardGap)
                        ) {
                            // Hero skeleton
                            item {
                                GlassCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(280.dp),
                                    shape = RoundedCornerShape(dimens.radius.xxl),
                                    useGoldBorder = true,
                                    contentPadding = PaddingValues(dimens.md)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Bottom
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.3f)
                                                .height(16.dp)
                                                .clip(RoundedCornerShape(dimens.radius.xs))
                                                .background(
                                                    GoldPrimary.copy(alpha = 0.12f)
                                                )
                                        )
                                        Spacer(modifier = Modifier.height(dimens.sm))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.9f)
                                                .height(20.dp)
                                                .clip(RoundedCornerShape(dimens.radius.xs))
                                                .background(
                                                    colors.surface2
                                                )
                                        )
                                        Spacer(modifier = Modifier.height(dimens.xs))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.6f)
                                                .height(14.dp)
                                                .clip(RoundedCornerShape(dimens.radius.xs))
                                                .background(
                                                    colors.surface2
                                                )
                                        )
                                    }
                                }
                            }
                            // Regular article skeletons
                            items(5) {
                                BouncerNewsSkeleton()
                            }
                        }
                    }
                    error != null && articles.isEmpty() -> {
                        EmptyStateView(
                            icon = Icons.Outlined.SportsCricket,
                            title = "Couldn't load news",
                            message = error ?: "Please try again in a moment.",
                            actionLabel = "Retry",
                            onAction = { newsViewModel.refreshNews() },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(dimens.xxl)
                        )
                    }
                    filteredArticles.isEmpty() -> {
                        // Empty state for no matching articles
                        EmptyStateView(
                            icon = Icons.Outlined.SportsCricket,
                            title = "No news articles",
                            message = if (selectedCategoryIndex == 0)
                                "Cricket news will appear here. Pull to refresh."
                            else
                                "No articles match \"${categoryChips[selectedCategoryIndex]}\". Try a different category.",
                            modifier = Modifier.padding(dimens.xxl)
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = dimens.screenHorizontalPadding,
                                end = dimens.screenHorizontalPadding,
                                top = dimens.sm,
                                bottom = dimens.bottomNavHeight + dimens.lg
                            ),
                            verticalArrangement = Arrangement.spacedBy(dimens.cardGap)
                        ) {
                            // Hero article
                            if (heroArticle != null) {
                                item(key = "hero_${heroArticle.id}") {
                                    HeroArticleCard(
                                        article = heroArticle,
                                        onClick = { onArticleClick(heroArticle) },
                                        modifier = Modifier.staggeredFadeIn(0)
                                    )
                                }
                            }

                            // Regular articles
                            itemsIndexed(
                                regularArticles,
                                key = { _, it -> it.id }
                            ) { index, article ->
                                RegularArticleCard(
                                    article = article,
                                    onClick = { onArticleClick(article) },
                                    modifier = Modifier.staggeredFadeIn(index + 1)
                                )
                            }

                            // Load More
                            item(key = "load_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = dimens.lg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    GlassSecondaryButton(
                                        text = "Refresh",
                                        onClick = { newsViewModel.refreshNews() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Hero Article Card
// =============================================================================

@Composable
private fun HeroArticleCard(
    article: NewsArticleData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    // Press feedback animation
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = 400f),
        label = "heroCardScale"
    )

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            },
        shape = RoundedCornerShape(dimens.radius.xxl),
        useGoldBorder = true,
        contentPadding = PaddingValues(0.dp)
    ) {
        // Background image with crossfade and shimmer placeholder
        if (article.imageUrl != null) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(article.imageUrl)
                    .crossfade(400)
                    .build(),
                contentDescription = article.headline,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(dimens.radius.xxl))
                    .graphicsLayer { alpha = 0.45f },
                contentScale = ContentScale.Crop
            )
        }

        // Gradient overlay from bottom for text readability
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // "BREAKING" or "TOP STORY" badge — top end with gold accent for breaking
        val isBreaking = article.storyType.contains("breaking", ignoreCase = true) ||
                        article.headline.contains("breaking", ignoreCase = true)
        
        GlassCardSmall(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(dimens.md),
            useGoldBorder = true,
            borderOpacity = if (isBreaking) 0.8f else 0.3f
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isBreaking) {
                    Box(
                        modifier = Modifier
                            .size(dimens.statusDotSmall)
                            .clip(CircleShape)
                            .background(colors.statusLive)
                    )
                }
                Text(
                    text = if (isBreaking) "BREAKING" else "TOP STORY",
                    style = typography.labelSmall.copy(
                        fontWeight = if (isBreaking) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isBreaking) colors.goldPrimary else colors.goldPrimary.copy(alpha = 0.8f)
                )
            }
        }

        // Bottom text content with enhanced typography
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(dimens.lg)
        ) {
            // Category with better styling
            Text(
                text = (article.storyType.ifBlank { "NEWS" }).uppercase(),
                style = typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = typography.labelMedium.letterSpacing
                ),
                color = hierarchy.secondary.primary
            )
            Spacer(modifier = Modifier.height(dimens.xs))
            
            // Headline with bold weight
            Text(
                text = article.headline,
                style = typography.headingMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(dimens.xs))
            Text(
                text = article.intro,
                style = typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(dimens.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(dimens.xs))
                Text(
                    text = article.timeAgo,
                    style = typography.labelSmall,
                    color = colors.textMuted
                )
                if (article.source.isNotBlank()) {
                    Spacer(modifier = Modifier.width(dimens.sm))
                    Text(
                        text = "·",
                        style = typography.labelSmall,
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.width(dimens.sm))
                    Text(
                        text = article.source,
                        style = typography.labelSmall,
                        color = hierarchy.secondary.tint
                    )
                }
            }
        }
    }
}

// =============================================================================
// Regular Article Card
// =============================================================================

@Composable
private fun RegularArticleCard(
    article: NewsArticleData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    // Press feedback animation
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = 400f),
        label = "regularCardScale"
    )

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            },
        useGoldBorder = true,
        borderOpacity = 0.06f,
        contentPadding = PaddingValues(dimens.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.md)
        ) {
            // Thumbnail with crossfade
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.glassBackgroundHover),
                contentAlignment = Alignment.Center
            ) {
                if (article.imageUrl != null) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(article.imageUrl)
                            .crossfade(300)
                            .build(),
                        contentDescription = article.headline,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.SportsCricket,
                        contentDescription = null,
                        tint = BouncerDesignSystem.hierarchy.secondary.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Text content with enhanced typography
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Category + time row with better formatting
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryTag(
                        text = article.storyType.ifBlank { "News" },
                        isLive = false
                    )
                    Spacer(modifier = Modifier.width(dimens.sm))
                    Text(
                        text = "·",
                        style = typography.labelSmall,
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.width(dimens.sm))
                    Text(
                        text = formatTimeAgo(article.timeAgo),
                        style = typography.labelSmall,
                        color = colors.textMuted
                    )
                }
                Spacer(modifier = Modifier.height(dimens.xs))
                
                // Headline with bold weight for emphasis
                Text(
                    text = article.headline,
                    style = typography.headingSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(dimens.xs))
                
                // Source with subtle gold accent
                if (article.source.isNotBlank()) {
                    Text(
                        text = article.source,
                        style = typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = hierarchy.secondary.tint
                    )
                }
            }
        }
    }
}

// =============================================================================
// Helper Components
// =============================================================================

@Composable
private fun CategoryTag(
    text: String,
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    val bgColor = if (isLive) colors.statusLive.copy(alpha = 0.15f) else hierarchy.secondary.primary.copy(alpha = 0.12f)
    val textColor = if (isLive) colors.statusLive else hierarchy.secondary.primary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = typography.labelSmall,
            color = textColor
        )
    }
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val statusDotSize = BouncerDesignSystem.dimens.statusDotSmall

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.statusLive.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(statusDotSize)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.statusLive)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "LIVE",
                style = typography.labelSmall,
                color = colors.statusLive
            )
        }
    }
}

// =============================================================================
// Helper Functions
// =============================================================================

/**
 * Formats time ago string for better readability
 */
private fun formatTimeAgo(timeAgo: String): String {
    return when {
        timeAgo.contains("min", ignoreCase = true) -> timeAgo.replace("minutes", "m").replace("minute", "m")
        timeAgo.contains("hour", ignoreCase = true) -> timeAgo.replace("hours", "h").replace("hour", "h")
        timeAgo.contains("day", ignoreCase = true) -> timeAgo.replace("days", "d").replace("day", "d")
        else -> timeAgo
    }
}
