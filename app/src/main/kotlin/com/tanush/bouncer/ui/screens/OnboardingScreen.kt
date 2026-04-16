package com.tanush.bouncer.ui.screens

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.launch

private data class OnboardingPageData(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val highlights: List<String>
)

private val onboardingPages = listOf(
    OnboardingPageData(
        icon = Icons.Default.SportsCricket,
        title = "Welcome to Bouncer",
        description = "Your premium cricket companion with live scores and clean match intelligence.",
        highlights = listOf("Live ball-by-ball updates", "Fast, readable match views")
    ),
    OnboardingPageData(
        icon = Icons.Default.Insights,
        title = "Live Intelligence",
        description = "Get momentum shifts and concise match context in real time.",
        highlights = listOf("Win-probability insights", "Instant key-moment summaries")
    ),
    OnboardingPageData(
        icon = Icons.Default.Favorite,
        title = "Personalized Feed",
        description = "Follow teams and tournaments you care about the most.",
        highlights = listOf("Quick favorite setup", "Focused content, less noise")
    ),
    OnboardingPageData(
        icon = Icons.Default.Notifications,
        title = "Smart Alerts",
        description = "Stay notified for wickets, milestones, and match starts.",
        highlights = listOf("Readable notification cards", "Alerts tuned to your interests")
    ),
    OnboardingPageData(
        icon = Icons.Default.RocketLaunch,
        title = "You’re all set",
        description = "Start exploring matches, squads, and insights.",
        highlights = listOf("Fast navigation", "Consistent visual language")
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val isLastPage = pagerState.currentPage == onboardingPages.lastIndex

    GlassBloomBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = dimens.screenHorizontal, vertical = dimens.sm),
                horizontalArrangement = Arrangement.End
            ) {
                if (!isLastPage) {
                    TextButton(onClick = {
                        saveOnboardingComplete(context)
                        onComplete()
                    }) {
                        Text(
                            text = "Skip",
                            style = typography.bodyMedium,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                OnboardingPage(
                    data = onboardingPages[page],
                    isPrimary = page == pagerState.currentPage
                )
            }

            Row(
                modifier = Modifier.padding(vertical = dimens.md),
                horizontalArrangement = Arrangement.spacedBy(dimens.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(onboardingPages.size) { index ->
                    val isSelected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .height(dimens.xs)
                            .width(if (isSelected) dimens.lg else dimens.sm)
                            .background(
                                color = if (isSelected) colors.goldPrimary else colors.glassBorder,
                                shape = RoundedCornerShape(dimens.radius.pill)
                            )
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.screenHorizontal)
                    .padding(bottom = dimens.md),
                horizontalArrangement = Arrangement.spacedBy(dimens.sm)
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(dimens.xxl),
                        border = BorderStroke(1.dp, colors.glassBorder),
                        shape = RoundedCornerShape(dimens.radius.md),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
                    ) {
                        Text(text = "Back", style = typography.bodyMedium)
                    }
                }

                Button(
                    onClick = {
                        if (isLastPage) {
                            saveOnboardingComplete(context)
                            Sentry.addBreadcrumb(
                                Breadcrumb().apply {
                                    category = "onboarding"
                                    message = "User completed onboarding"
                                    level = SentryLevel.INFO
                                }
                            )
                            onComplete()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = if (pagerState.currentPage == 0) {
                        Modifier
                            .fillMaxWidth()
                            .height(dimens.xxl)
                    } else {
                        Modifier
                            .weight(1f)
                            .height(dimens.xxl)
                    },
                    shape = RoundedCornerShape(dimens.radius.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.goldPrimary,
                        contentColor = colors.backgroundBase
                    )
                ) {
                    Text(
                        text = if (isLastPage) "Get Started" else "Continue",
                        style = typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun OnboardingPage(
    data: OnboardingPageData,
    isPrimary: Boolean
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.screenHorizontal),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassCard(
            modifier = Modifier.size(dimens.xxxl + dimens.md),
            useGoldBorder = isPrimary,
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = data.icon,
                    contentDescription = null,
                    tint = colors.goldPrimary,
                    modifier = Modifier.size(dimens.lg)
                )
            }
        }

        Spacer(modifier = Modifier.height(dimens.lg))

        Text(
            text = data.title,
            style = typography.headingLarge,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(dimens.sm))

        Text(
            text = data.description,
            style = typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(dimens.lg))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            useGoldBorder = false,
            contentPadding = PaddingValues(dimens.md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.sm)) {
                data.highlights.forEach { highlight ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(dimens.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(dimens.xs)
                                .background(colors.goldPrimary, RoundedCornerShape(dimens.radius.pill))
                        )
                        Text(
                            text = highlight,
                            style = typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

private fun saveOnboardingComplete(context: Context) {
    context.getSharedPreferences("bouncer_prefs", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("onboarding_complete", true)
        .apply()
}
