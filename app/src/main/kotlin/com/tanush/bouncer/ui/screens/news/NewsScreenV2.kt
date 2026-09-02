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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanush.bouncer.R
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
    val staleHeadlines by viewModel.staleHeadlines.collectAsStateWithLifecycle()

    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val featuredArticle = articles.firstOrNull()
    val otherArticles = if (articles.size > 1) articles.drop(1) else emptyList()

    // Refresh failed while headlines are on screen: keep the stale list, say so.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        if (error != null && articles.isNotEmpty()) {
            snackbarHostState.showSnackbar("Couldn't refresh headlines — showing earlier stories.")
        }
    }

    Scaffold(
        topBar = { BouncerTopBarV2(title = stringResource(R.string.news_news)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colors.background,
    ) { innerPadding ->
        PullRefreshWrapperV2(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshNews() },
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

                // Empty or error state.
                //
                // The message comes straight from the ViewModel, which builds it from the HTTP
                // status. This used to re-derive it by substring-matching the message for "403"
                // and "429" — codes the repository had already collapsed into one fixed string,
                // so every failure rendered as "Something went wrong". Retry is offered in BOTH
                // branches: an empty tab with no way out was this bug's most visible symptom.
                if (articles.isEmpty() && !isLoading) {
                    item(key = "empty") {
                        val error = error // capture delegated property to enable smart cast
                        EmptyStateV2(
                            title = error ?: "No news yet",
                            subtitle = stringResource(R.string.news_pull_down_to_refresh_and_try),
                            onRetry = { viewModel.refreshNews() },
                        )
                    }
                    return@LazyColumn
                }

                // Headlines are the server's cache because the live source was unreachable. Say
                // how old they are — presenting them as current is the failure this replaces.
                staleHeadlines?.let { stale ->
                    item(key = "stale_banner") {
                        StaleHeadlinesBanner(
                            ageMs = stale.ageMs,
                            modifier = Modifier.padding(horizontal = spacing.lg),
                        )
                    }
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
 * States plainly that the headlines below are the server's cache, and how old it is.
 *
 * [ageMs] is null when the server served a cache it could not date. That case still gets a banner —
 * "stale, age unknown" is honest; silently rendering it as current is what left five-day-old
 * headlines looking live.
 */
@Composable
private fun StaleHeadlinesBanner(
    ageMs: Long?,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    BouncerCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Text(
                text = ageMs?.let { "Headlines from ${describeAge(it)}" } ?: "Showing saved headlines",
                style = typography.bodyMedium,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = stringResource(R.string.news_the_live_news_source_is_unavailable),
                style = typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

private fun describeAge(ageMs: Long): String {
    val minutes = ageMs / MINUTE_MS
    val hours = ageMs / HOUR_MS
    val days = ageMs / DAY_MS
    return when {
        minutes < 1 -> "moments ago"
        minutes < 60 -> "$minutes ${plural(minutes, "minute")} ago"
        hours < 24 -> "$hours ${plural(hours, "hour")} ago"
        else -> "$days ${plural(days, "day")} ago"
    }
}

private fun plural(count: Long, unit: String) = if (count == 1L) unit else "${unit}s"

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
