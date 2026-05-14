package com.tanush.bouncer.ui.screens.news

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanush.bouncer.models.NewsArticleData
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.NewsCardV2
import com.tanush.bouncer.ui.components.v2.PullRefreshWrapperV2
import com.tanush.bouncer.ui.components.v2.ShimmerMatchCard
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.viewmodels.NewsViewModel

/**
 * NewsScreenV2 — News tab with featured article hero + list.
 */
@Composable
fun NewsScreenV2(
    viewModel: NewsViewModel,
    onArticleClick: (NewsArticleData) -> Unit,
) {
    val articles by viewModel.articles.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val featuredArticle = articles.firstOrNull()
    val otherArticles = if (articles.size > 1) articles.drop(1) else emptyList()

    Scaffold(
        topBar = { BouncerTopBarV2(title = "News") },
        containerColor = colors.background,
    ) { innerPadding ->
        PullRefreshWrapperV2(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                // Loading
                if (isLoading && articles.isEmpty()) {
                    items(4, key = { "shimmer_$it" }) {
                        ShimmerMatchCard(
                            modifier = Modifier.padding(horizontal = spacing.lg),
                        )
                    }
                    return@LazyColumn
                }

                // Empty or error state
                if (articles.isEmpty() && !isLoading) {
                    item(key = "empty") {
                        if (error != null) {
                            val friendlyError = when {
                                error!!.contains("403") -> "News source temporarily unavailable"
                                error!!.contains("429") -> "Too many requests — try again in a moment"
                                error!!.contains("timeout", ignoreCase = true) || error!!.contains("connect", ignoreCase = true) ->
                                    "Network connection issue"
                                else -> "Something went wrong"
                            }
                            EmptyStateV2(
                                title = friendlyError,
                                subtitle = "Pull down to refresh and try again",
                            )
                        } else {
                            EmptyStateV2(
                                title = "No news yet",
                                subtitle = "Pull to refresh for the latest cricket headlines",
                            )
                        }
                    }
                    return@LazyColumn
                }

                // Featured article (hero card)
                if (featuredArticle != null) {
                    item(key = "featured_${featuredArticle.id}") {
                        FeaturedNewsCard(
                            article = featuredArticle,
                            onClick = { onArticleClick(featuredArticle) },
                            modifier = Modifier.padding(horizontal = spacing.lg),
                        )
                    }
                }

                // Regular article list
                items(otherArticles, key = { "article_${it.id}" }) { article ->
                    NewsCardV2(
                        headline = article.headline,
                        source = article.source,
                        timeAgo = article.timeAgo,
                        imageUrl = article.imageUrl,
                        onClick = { onArticleClick(article) },
                        modifier = Modifier.padding(horizontal = spacing.lg),
                    )
                }

                // Bottom spacing
                item(key = "bottom_spacer") {
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }
}

/**
 * Featured/hero news card — large image top, headline below.
 */
@Composable
private fun FeaturedNewsCard(
    article: NewsArticleData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    BouncerCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            if (article.imageUrl != null) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(modifier = Modifier.padding(spacing.lg)) {
                Text(
                    text = article.headline,
                    style = typography.headlineMedium,
                    color = colors.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = "${article.source} • ${article.timeAgo}",
                    style = typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
