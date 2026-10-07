package com.tanush.bouncer.ui.screens.news

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tanush.bouncer.R
import com.tanush.bouncer.models.NewsArticleData
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.NewsImage
import com.tanush.bouncer.ui.components.v2.ShimmerBox
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.viewmodels.NewsViewModel
import io.sentry.Sentry

/**
 * One article: its photograph and credit, what kind of story it is, the headline, who published it
 * and when, then the text in one reading style.
 */
@Composable
fun NewsDetailScreenV2(
    viewModel: NewsViewModel,
    onBack: () -> Unit,
) {
    val article by viewModel.selectedArticle.collectAsStateWithLifecycle()
    val articleBody by viewModel.articleBody.collectAsStateWithLifecycle()
    val isLoadingArticle by viewModel.isLoadingArticle.collectAsStateWithLifecycle()
    val articleBodyError by viewModel.articleBodyError.collectAsStateWithLifecycle()
    val spacing = BouncerThemeV2.spacing

    Scaffold(
        topBar = {
            BouncerTopBarV2(
                title = stringResource(R.string.news_article),
                showBack = true,
                onBack = {
                    viewModel.clearSelectedArticle()
                    onBack()
                },
                actions = { article?.let { ArticleActions(it) } },
            )
        },
        containerColor = BouncerThemeV2.colors.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = spacing.xxl),
        ) {
            val shown = article
            if (shown == null) {
                item(key = "no_article") {
                    EmptyStateV2(
                        icon = Icons.Outlined.Newspaper,
                        title = stringResource(R.string.news_article_unavailable),
                        subtitle = stringResource(R.string.news_this_story_may_have_expired_head),
                    )
                }
                return@LazyColumn
            }
            articleHeader(shown)
            articleBody(
                body = ArticleBodyState(articleBody, isLoadingArticle, articleBodyError),
                onRetry = viewModel::retryArticleBody,
            )
        }
    }
}

/** Share the story, or open it where it was published. The link existed and nothing offered it. */
@Composable
private fun ArticleActions(article: NewsArticleData) {
    val context = LocalContext.current
    IconButton(onClick = { context.shareArticle(article) }) {
        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.news_share))
    }
    IconButton(onClick = { context.openOriginal(article) }) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.news_open_original))
    }
}

private fun Context.shareArticle(article: NewsArticleData) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, article.headline)
        putExtra(Intent.EXTRA_TEXT, "${article.headline}\n${article.articleUrl}")
    }
    startActivity(Intent.createChooser(send, null))
}

private fun Context.openOriginal(article: NewsArticleData) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.articleUrl)))
    } catch (e: ActivityNotFoundException) {
        // A device with no browser. Nothing to open it in, and not a reason to crash the screen.
        Sentry.captureException(e)
    }
}

private fun LazyListScope.articleHeader(article: NewsArticleData) {
    if (article.imageUrl != null) {
        item(key = "hero_image") { ArticlePhoto(article) }
    }
    item(key = "headline") { ArticleHeadline(article) }
    item(key = "divider") {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = BouncerThemeV2.spacing.lg),
            color = BouncerThemeV2.colors.divider,
        )
    }
    if (article.intro.isNotBlank()) {
        item(key = "intro") { Paragraph(article.intro, FontWeight.Medium) }
    }
}

@Composable
private fun ArticlePhoto(article: NewsArticleData) {
    val spacing = BouncerThemeV2.spacing
    Column(modifier = Modifier.padding(start = spacing.lg, end = spacing.lg, top = spacing.lg)) {
        NewsImage(
            url = article.imageUrlAt(HERO_PX_WIDTH, HERO_PX_HEIGHT),
            modifier = Modifier.fillMaxWidth().aspectRatio(HERO_ASPECT),
        )
        // The caption and the credit were parsed off every story and never shown.
        val credit = listOfNotNull(article.imageCaption, article.imageCredit).joinToString(" · ")
        if (credit.isNotBlank()) {
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = credit,
                style = BouncerThemeV2.typography.bodySmall,
                color = BouncerThemeV2.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ArticleHeadline(article: NewsArticleData) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Column(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.lg)) {
        // What kind of story it is, and the series or match it belongs to.
        val kicker = listOfNotNull(article.storyType.takeIf { it.isNotBlank() }, article.context)
            .joinToString(" · ")
        if (kicker.isNotBlank()) {
            Text(text = kicker, style = typography.labelMedium, color = colors.textSecondary)
            Spacer(Modifier.height(spacing.xs))
        }
        Text(text = article.headline, style = typography.headlineLarge, color = colors.onBackground)
        // The date as well as the age: "2w ago" is all a fortnight-old story used to say.
        val published = listOfNotNull(article.byline.takeIf { it.isNotBlank() }, article.publishedDate())
            .joinToString(" · ")
        if (published.isNotBlank()) {
            Spacer(Modifier.height(spacing.sm))
            Text(text = published, style = typography.bodyMedium, color = colors.textSecondary)
        }
    }
}

/** The text of the story, in whichever state it is in. */
private class ArticleBodyState(val paragraphs: List<String>, val isLoading: Boolean, val error: String?)

private fun LazyListScope.articleBody(body: ArticleBodyState, onRetry: () -> Unit) {
    if (body.isLoading) {
        items(BODY_SKELETON_LINES, key = { "body_shimmer_$it" }) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BouncerThemeV2.spacing.lg, vertical = BouncerThemeV2.spacing.xs),
                height = 16.dp,
            )
        }
    }

    // Body failed to load — honest error with retry, not fake body text
    if (body.error != null && !body.isLoading && body.paragraphs.isEmpty()) {
        item(key = "body_error") {
            EmptyStateV2(
                icon = Icons.Outlined.Newspaper,
                title = stringResource(R.string.news_couldn_t_load_the_full_story),
                subtitle = body.error,
                onRetry = onRetry,
            )
        }
    }

    // Keyed on position. Keyed on the text, an article that repeats a paragraph ("Brief
    // scores", a pull quote) threw "Key … was already used" and took the screen down.
    itemsIndexed(body.paragraphs, key = { index, _ -> "para_$index" }) { _, paragraph -> Paragraph(paragraph) }
}

/** One paragraph. The standfirst and the body share a size; the standfirst is a weight heavier. */
@Composable
private fun Paragraph(text: String, weight: FontWeight? = null) {
    val spacing = BouncerThemeV2.spacing
    Text(
        text = text,
        style = BouncerThemeV2.typography.bodyLarge,
        fontWeight = weight,
        color = BouncerThemeV2.colors.onBackground,
        modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
    )
}

private const val BODY_SKELETON_LINES = 5
