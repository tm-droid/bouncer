package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.components.glass.GlassCardSmall
import com.tanush.bouncer.ui.components.glass.GlassChipRow
import com.tanush.bouncer.ui.components.glass.GlassIconButton
import com.tanush.bouncer.ui.components.glass.GlassSecondaryButton
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.staggeredFadeIn

// =============================================================================
// Sample Data
// =============================================================================

data class NewsArticle(
    val id: String,
    val category: String,
    val headline: String,
    val subtitle: String,
    val author: String,
    val timeAgo: String,
    val readCount: String,
    val isTopStory: Boolean = false,
    val isLive: Boolean = false
)

private val sampleArticles = listOf(
    NewsArticle(
        id = "1",
        category = "Breaking",
        headline = "India Clinch Series 3-1 With Dominant Win at The Gabba",
        subtitle = "Jasprit Bumrah's five-wicket haul seals a historic victory for India on Australian soil, silencing the home crowd.",
        author = "Ravi Shastri",
        timeAgo = "15 mins ago",
        readCount = "45.2k reads",
        isTopStory = true
    ),
    NewsArticle(
        id = "2",
        category = "Live Blog",
        headline = "ENG vs AUS 3rd Test — Day 2 Session 3 Live Updates",
        subtitle = "Australia trail by 112 runs with 6 wickets remaining.",
        author = "Nasser Hussain",
        timeAgo = "Live",
        readCount = "32.1k reads",
        isLive = true
    ),
    NewsArticle(
        id = "3",
        category = "Transfer",
        headline = "Rashid Khan Signs Record IPL Deal With Gujarat Titans",
        subtitle = "The Afghan leg-spinner becomes the highest-paid overseas player in IPL history.",
        author = "Harsha Bhogle",
        timeAgo = "2h ago",
        readCount = "28.7k reads"
    ),
    NewsArticle(
        id = "4",
        category = "Analysis",
        headline = "Why Bazball Is Changing the Way We Think About Test Cricket",
        subtitle = "England's aggressive approach is redefining what it means to play for a draw.",
        author = "Michael Atherton",
        timeAgo = "4h ago",
        readCount = "18.3k reads"
    ),
    NewsArticle(
        id = "5",
        category = "Interview",
        headline = "Exclusive: Pat Cummins on Captaincy, Burnout, and the Ashes",
        subtitle = "The Australian skipper opens up about the mental toll of leading across all formats.",
        author = "Adam Collins",
        timeAgo = "6h ago",
        readCount = "12.5k reads"
    ),
    NewsArticle(
        id = "6",
        category = "Series",
        headline = "ICC Confirms Dates for 2026 Champions Trophy in Pakistan",
        subtitle = "All eight teams confirmed with Pakistan hosting the event for the first time since 1996.",
        author = "ESPNcricinfo Staff",
        timeAgo = "8h ago",
        readCount = "9.8k reads"
    )
)

private val categoryChips = listOf("All", "Live", "Transfer", "Analysis", "Interviews", "Series")

// =============================================================================
// News Screen
// =============================================================================

@Composable
fun NewsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    val filteredArticles = remember(selectedCategoryIndex) {
        if (selectedCategoryIndex == 0) {
            sampleArticles
        } else {
            val category = categoryChips[selectedCategoryIndex]
            sampleArticles.filter { article ->
                when (category) {
                    "Live" -> article.isLive || article.category == "Live Blog"
                    "Interviews" -> article.category == "Interview"
                    else -> article.category.equals(category, ignoreCase = true)
                }
            }
        }
    }

    val topStory = filteredArticles.firstOrNull { it.isTopStory }
    val regularArticles = filteredArticles.filter { !it.isTopStory }

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
                if (topStory != null) {
                    item(key = "hero_${topStory.id}") {
                        HeroArticleCard(
                            article = topStory,
                            modifier = Modifier.staggeredFadeIn(0)
                        )
                    }
                }

                // Regular articles
                itemsIndexed(regularArticles, key = { _, it -> it.id }) { index, article ->
                    RegularArticleCard(
                        article = article,
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
                            text = "Load More",
                            onClick = { /* placeholder */ }
                        )
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
    article: NewsArticle,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(24.dp),
        useGoldBorder = true,
        contentPadding = PaddingValues(0.dp)
    ) {
        // Gradient overlay from bottom for text readability
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        )

        // "Top Story" badge — top end
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

        // Live indicator if applicable
        if (article.isLive) {
            LiveBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(dimens.md)
            )
        }

        // Bottom text content
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(dimens.lg)
        ) {
            Text(
                text = article.category.uppercase(),
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
                text = article.subtitle,
                style = typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(dimens.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = article.author,
                    style = typography.labelSmall,
                    color = colors.goldSubtle
                )
                Spacer(modifier = Modifier.width(dimens.sm))
                Text(
                    text = "·",
                    style = typography.labelSmall,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.width(dimens.sm))
                Text(
                    text = article.timeAgo,
                    style = typography.labelSmall,
                    color = colors.textMuted
                )
            }
        }
    }
}

// =============================================================================
// Regular Article Card
// =============================================================================

@Composable
private fun RegularArticleCard(
    article: NewsArticle,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(dimens.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimens.md)
        ) {
            // Thumbnail placeholder
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.glassBackgroundHover),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (article.isLive) Icons.AutoMirrored.Outlined.TrendingUp else Icons.Outlined.Newspaper,
                    contentDescription = null,
                    tint = colors.goldPrimary.copy(alpha = 0.4f),
                    modifier = Modifier.size(dimens.iconSize.lg)
                )
            }

            // Text content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Category + time row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryTag(
                        text = article.category,
                        isLive = article.isLive
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(dimens.xs))
                    Text(
                        text = article.readCount,
                        style = typography.labelSmall,
                        color = colors.textMuted
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
