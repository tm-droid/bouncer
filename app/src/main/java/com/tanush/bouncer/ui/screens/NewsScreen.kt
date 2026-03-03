package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.tanush.bouncer.models.NewsArticleData
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.components.glass.GlassCardSmall
import com.tanush.bouncer.ui.components.glass.GlassChipRow
import com.tanush.bouncer.ui.components.glass.GlassIconButton
import com.tanush.bouncer.ui.components.glass.GlassSecondaryButton
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.staggeredFadeIn
import com.tanush.bouncer.viewmodels.NewsViewModel

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
                Text(
                    text = "NEWS",
                    style = typography.headingLarge,
                    color = colors.goldPrimary,
                    letterSpacing = typography.labelLarge.letterSpacing
                )
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
                        // Loading shimmer state
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading latest cricket news...",
                                style = typography.bodyMedium,
                                color = colors.textMuted
                            )
                        }
                    }
                    error != null && articles.isEmpty() -> {
                        // Error state
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Couldn't load news",
                                    style = typography.headingSmall,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(dimens.sm))
                                Text(
                                    text = error ?: "",
                                    style = typography.bodySmall,
                                    color = colors.textMuted
                                )
                                Spacer(modifier = Modifier.height(dimens.md))
                                GlassSecondaryButton(
                                    text = "Retry",
                                    onClick = { newsViewModel.refreshNews() }
                                )
                            }
                        }
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
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        useGoldBorder = true,
        contentPadding = PaddingValues(0.dp)
    ) {
        // Background image from Cricbuzz
        if (article.imageUrl != null) {
            AsyncImage(
                model = article.imageUrl,
                contentDescription = article.headline,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(24.dp))
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

        // "TOP STORY" badge — top end
        GlassCardSmall(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(dimens.md),
            useGoldBorder = true
        ) {
            Text(
                text = "TOP STORY",
                style = typography.labelSmall,
                color = colors.goldPrimary
            )
        }

        // Bottom text content
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(dimens.lg)
        ) {
            Text(
                text = (article.storyType.ifBlank { "NEWS" }).uppercase(),
                style = typography.labelMedium,
                color = colors.goldPrimary
            )
            Spacer(modifier = Modifier.height(dimens.xs))
            Text(
                text = article.headline,
                style = typography.headingMedium,
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
                        color = colors.goldSubtle
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
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(dimens.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.md)
        ) {
            // Thumbnail from Cricbuzz
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.glassBackgroundHover),
                contentAlignment = Alignment.Center
            ) {
                if (article.imageUrl != null) {
                    AsyncImage(
                        model = article.imageUrl,
                        contentDescription = article.headline,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = "🏏",
                        fontSize = 28.sp
                    )
                }
            }

            // Text content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Category + time row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryTag(
                        text = article.storyType.ifBlank { "News" },
                        isLive = false
                    )
                    Spacer(modifier = Modifier.width(dimens.sm))
                    Text(
                        text = article.timeAgo,
                        style = typography.labelSmall,
                        color = colors.textMuted
                    )
                }
                Spacer(modifier = Modifier.height(dimens.xs))
                Text(
                    text = article.headline,
                    style = typography.headingSmall,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(dimens.xs))
                if (article.source.isNotBlank()) {
                    Text(
                        text = article.source,
                        style = typography.labelSmall,
                        color = colors.goldSubtle
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
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    val bgColor = if (isLive) colors.statusLive.copy(alpha = 0.15f) else colors.goldPrimary.copy(alpha = 0.12f)
    val textColor = if (isLive) colors.statusLive else colors.goldPrimary

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

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.statusLive.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
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
