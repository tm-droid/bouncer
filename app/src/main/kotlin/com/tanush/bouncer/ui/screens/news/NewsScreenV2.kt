package com.tanush.bouncer.ui.screens.news

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.models.NewsArticleData
import com.tanush.bouncer.ui.components.v2.BouncerCard
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.NewsImage
import com.tanush.bouncer.ui.components.v2.NewsRowSkeleton
import com.tanush.bouncer.ui.components.v2.NewsRowV2
import com.tanush.bouncer.ui.components.v2.PullRefreshWrapperV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.viewmodels.NewsViewModel
import com.tanush.bouncer.viewmodels.StaleHeadlines

/**
 * NewsScreenV2 — News tab with featured article hero + list.
 */
@Composable
fun NewsScreenV2(
    viewModel: NewsViewModel,
    onArticleClick: (NewsArticleData) -> Unit,
    onSearchClick: (() -> Unit)? = null,
) {
    val articles by viewModel.articles.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val staleHeadlines by viewModel.staleHeadlines.collectAsStateWithLifecycle()

    val colors = BouncerThemeV2.colors

    // Refresh failed while headlines are on screen: keep the stale list, say so.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        if (error != null && articles.isNotEmpty()) {
            snackbarHostState.showSnackbar("Couldn't refresh headlines — showing earlier stories.")
        }
    }

    Scaffold(
        topBar = { NewsTopBar(onSearchClick) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colors.background,
    ) { innerPadding ->
        PullRefreshWrapperV2(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshNews() },
            modifier = Modifier.padding(innerPadding),
        ) {
            val spacing = BouncerThemeV2.spacing
            val emptySubtitle = stringResource(R.string.news_pull_down_to_refresh_and_try)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = BOTTOM_BAR_CLEARANCE),
            ) {
                newsItems(
                    state = NewsListState(articles, isLoading, error, staleHeadlines),
                    emptySubtitle = emptySubtitle,
                    rowPadding = Modifier.padding(horizontal = spacing.lg),
                    onArticleClick = onArticleClick,
                    onRetry = { viewModel.refreshNews() },
                )
            }
        }
    }
}

@Composable
private fun NewsTopBar(onSearchClick: (() -> Unit)?) {
    BouncerTopBarV2(
        title = stringResource(R.string.news_news),
        actions = {
            if (onSearchClick != null) {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.cd_search))
                }
            }
        },
    )
}

/** Everything the news list renders from, as one value. */
private class NewsListState(
    val articles: List<NewsArticleData>,
    val isLoading: Boolean,
    val error: String?,
    val staleHeadlines: StaleHeadlines?,
)

private fun LazyListScope.newsItems(
    state: NewsListState,
    emptySubtitle: String,
    rowPadding: Modifier,
    onArticleClick: (NewsArticleData) -> Unit,
    onRetry: () -> Unit,
) {
    val articles = state.articles

    // Loading
    if (state.isLoading && articles.isEmpty()) {
        // News-shaped and still. These were match-row skeletons, so first paint jumped.
        items(SKELETON_ROWS, key = { "skeleton_$it" }) { NewsRowSkeleton() }
        return
    }

    // Empty or error state.
    //
    // The message comes straight from the ViewModel, which builds it from the HTTP status. This
    // used to re-derive it by substring-matching the message for "403" and "429" — codes the
    // repository had already collapsed into one fixed string, so every failure rendered as
    // "Something went wrong". Retry is offered in BOTH branches: an empty tab with no way out was
    // this bug's most visible symptom.
    if (articles.isEmpty()) {
        item(key = "empty") {
            EmptyStateV2(title = state.error ?: "No news yet", subtitle = emptySubtitle, onRetry = onRetry)
        }
        return
    }

    // Headlines are the server's cache because the live source was unreachable. Say how old they
    // are — presenting them as current is the failure this replaces.
    state.staleHeadlines?.let { stale ->
        item(key = "stale_banner") { StaleHeadlinesBanner(ageMs = stale.ageMs, modifier = rowPadding) }
    }

    // The lead story, then every other as a row. One background, split by dividers.
    val lead = articles.first()
    item(key = "lead_${lead.id}") {
        LeadStory(article = lead, onClick = { onArticleClick(lead) })
        NewsDivider()
    }
    items(articles.drop(1), key = { "article_${it.id}" }) { article ->
        NewsRowV2(
            headline = article.headline,
            byline = article.byline,
            imageUrl = article.imageUrlAt(THUMBNAIL_PX, THUMBNAIL_PX),
            onClick = { onArticleClick(article) },
        )
        NewsDivider()
    }
}

@Composable
private fun NewsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = BouncerThemeV2.spacing.lg),
        color = BouncerThemeV2.colors.divider,
    )
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

/** The first story: its photograph at full width, then the headline. Not a card. */
@Composable
private fun LeadStory(article: NewsArticleData, onClick: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.lg),
    ) {
        if (article.imageUrl != null) {
            NewsImage(
                url = article.imageUrlAt(HERO_PX_WIDTH, HERO_PX_HEIGHT),
                modifier = Modifier.fillMaxWidth().aspectRatio(HERO_ASPECT),
            )
            Spacer(Modifier.height(spacing.md))
        }
        Text(
            text = article.headline,
            style = typography.headlineMedium,
            color = colors.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (article.byline.isNotBlank()) {
            Spacer(Modifier.height(spacing.xs))
            Text(text = article.byline, style = typography.bodySmall, color = colors.textSecondary)
        }
    }
}

// A 72dp thumbnail at 4x density. The stored URL asks for 2048x1152.
private const val THUMBNAIL_PX = 288
internal const val HERO_PX_WIDTH = 1080
internal const val HERO_PX_HEIGHT = 608
internal const val HERO_ASPECT = 16f / 9f
private const val SKELETON_ROWS = 6
private val BOTTOM_BAR_CLEARANCE = 80.dp
