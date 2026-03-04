package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.tanush.bouncer.ui.components.glass.GlassIconButton
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.viewmodels.NewsViewModel

// =============================================================================
// News Detail Screen — Full Article Body
// =============================================================================

@Composable
fun NewsDetailScreen(
    article: NewsArticleData,
    newsViewModel: NewsViewModel,
    onBackClick: () -> Unit,
    onOpenInBrowser: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    val articleBody by newsViewModel.articleBody.collectAsStateWithLifecycle()
    val isLoadingArticle by newsViewModel.isLoadingArticle.collectAsStateWithLifecycle()

    // Fetch article body on enter
    LaunchedEffect(article.id) {
        newsViewModel.selectArticle(article)
    }

    GlassBloomBackground(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── Header Bar ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = dimens.screenHorizontalPadding,
                        vertical = dimens.sm
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    onClick = onBackClick,
                    contentDescription = "Back"
                )

                Text(
                    text = article.storyType.uppercase().ifBlank { "NEWS" },
                    style = typography.labelLarge,
                    color = colors.goldPrimary
                )

                GlassIconButton(
                    icon = Icons.Outlined.OpenInBrowser,
                    onClick = { onOpenInBrowser(article.articleUrl) },
                    contentDescription = "Open in browser"
                )
            }

            // ── Scrollable Content ──────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = dimens.bottomNavHeight + dimens.lg)
            ) {
                // ── Hero Image ──────────────────────────────────────
                if (article.imageUrl != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .padding(horizontal = dimens.screenHorizontalPadding)
                            .clip(RoundedCornerShape(20.dp))
                    ) {
                        AsyncImage(
                            model = article.imageUrl,
                            contentDescription = article.headline,
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Crop
                        )

                        // Subtle gradient overlay
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.4f)
                                        )
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(dimens.md))
                }

                // ── Headline ────────────────────────────────────────
                Text(
                    text = article.headline,
                    style = typography.headingLarge.copy(
                        fontSize = 22.sp,
                        lineHeight = 28.sp
                    ),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = dimens.screenHorizontalPadding)
                )

                Spacer(modifier = Modifier.height(dimens.sm))

                // ── Meta row — source · time ────────────────────────
                Row(
                    modifier = Modifier.padding(horizontal = dimens.screenHorizontalPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (article.source.isNotBlank()) {
                        Text(
                            text = article.source,
                            style = typography.labelMedium,
                            color = colors.goldSubtle,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(dimens.sm))
                        Text(
                            text = "·",
                            style = typography.labelMedium,
                            color = colors.textMuted
                        )
                        Spacer(modifier = Modifier.width(dimens.sm))
                    }
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = article.timeAgo,
                        style = typography.labelMedium,
                        color = colors.textMuted
                    )
                }

                Spacer(modifier = Modifier.height(dimens.sm))

                // ── Summary ─────────────────────────────────────────
                if (article.intro.isNotBlank()) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.screenHorizontalPadding),
                        useGoldBorder = true,
                        contentPadding = PaddingValues(dimens.md)
                    ) {
                        Text(
                            text = article.intro,
                            style = typography.bodyMedium.copy(
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = colors.textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(dimens.lg))
                }

                // ── Article Body ────────────────────────────────────
                when {
                    isLoadingArticle -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading article...",
                                style = typography.bodyMedium,
                                color = colors.textMuted
                            )
                        }
                    }
                    !articleBody.isNullOrEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dimens.screenHorizontalPadding),
                            verticalArrangement = Arrangement.spacedBy(dimens.md)
                        ) {
                            articleBody?.forEach { paragraph ->
                                Text(
                                    text = paragraph,
                                    style = typography.bodyMedium.copy(
                                        lineHeight = 24.sp
                                    ),
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }
                    else -> {
                        // Fallback — show summary only
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Tap 'Open in browser' to read the full article",
                                style = typography.bodySmall,
                                color = colors.textMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(dimens.xl))
            }
        }
    }
}
