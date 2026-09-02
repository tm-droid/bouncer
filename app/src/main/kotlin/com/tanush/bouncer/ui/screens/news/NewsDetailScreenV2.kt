package com.tanush.bouncer.ui.screens.news

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.ShimmerBox
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.viewmodels.NewsViewModel

/**
 * NewsDetailScreenV2 — Full article detail view.
 * Shows hero image, headline, author/source, and scraped body paragraphs.
 */
@Composable
fun NewsDetailScreenV2(
    viewModel: NewsViewModel,
    onBack: () -> Unit,
) {
    val selectedArticle by viewModel.selectedArticle.collectAsStateWithLifecycle()
    val articleBody by viewModel.articleBody.collectAsStateWithLifecycle()
    val isLoadingArticle by viewModel.isLoadingArticle.collectAsStateWithLifecycle()
    val articleBodyError by viewModel.articleBodyError.collectAsStateWithLifecycle()

    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val article = selectedArticle

    Scaffold(
        topBar = {
            BouncerTopBarV2(
                title = stringResource(R.string.news_article),
                showBack = true,
                onBack = {
                    viewModel.clearSelectedArticle()
                    onBack()
                },
            )
        },
        containerColor = colors.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = spacing.xxl),
        ) {
            if (article == null) {
                item(key = "no_article") {
                    EmptyStateV2(
                        icon = Icons.Outlined.Newspaper,
                        title = stringResource(R.string.news_article_unavailable),
                        subtitle = stringResource(R.string.news_this_story_may_have_expired_head),
                    )
                }
                return@LazyColumn
            }

            // Hero image
            if (article.imageUrl != null) {
                item(key = "hero_image") {
                    AsyncImage(
                        model = article.imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            // Headline + meta
            item(key = "headline") {
                Column(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.lg)) {
                    Text(
                        text = article.headline,
                        style = typography.headlineLarge,
                        color = colors.onBackground,
                    )
                    Spacer(Modifier.height(spacing.sm))
                    Text(
                        text = "${article.source} • ${article.timeAgo}",
                        style = typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    if (article.context != null) {
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = article.context,
                            style = typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            item(key = "divider") {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = spacing.lg),
                    color = colors.divider,
                )
            }

            // Intro paragraph
            item(key = "intro") {
                Text(
                    text = article.intro,
                    style = typography.bodyLarge,
                    color = colors.onBackground,
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
                )
            }

            // Loading shimmer for body
            if (isLoadingArticle) {
                items(5, key = { "body_shimmer_$it" }) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.lg, vertical = spacing.xs),
                        height = 16.dp,
                    )
                }
            }

            // Body failed to load — honest error with retry, not fake body text
            val bodyError = articleBodyError
            if (bodyError != null && !isLoadingArticle && articleBody.isEmpty()) {
                item(key = "body_error") {
                    EmptyStateV2(
                        icon = Icons.Outlined.Newspaper,
                        title = stringResource(R.string.news_couldn_t_load_the_full_story),
                        subtitle = bodyError,
                        onRetry = viewModel::retryArticleBody,
                    )
                }
            }

            // Body paragraphs
            items(articleBody, key = { "para_${it.hashCode()}" }) { paragraph ->
                Text(
                    text = paragraph,
                    style = typography.bodyMedium,
                    color = colors.onBackground,
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                )
            }
        }
    }
}
