package com.tanush.bouncer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.launch

// ==========================================
// BOUNCER – Onboarding Screen v2.0
// Design Spec: android/design/BOUNCER_DESIGN_SYSTEM.md §7.1
//
// 3-page pager: Live Intelligence → Your Teams → Stay Informed
// 320dp illustration area, parallax 0.5x
// Gold page dots, primary Continue / Get Started buttons
// bg-base with subtle radial gold glow
// ==========================================

private data class OnboardingPage(
    val headline: String,
    val body: String,
    val icon: ImageVector,
    val iconTint: Color
)

private val pages = listOf(
    OnboardingPage(
        headline = "Live Cricket Intelligence",
        body = "Real-time scores, AI-powered insights, and ball-by-ball analysis from Cricbuzz and ESPNCricinfo.",
        icon = Icons.Default.SportsCricket,
        iconTint = Gold400
    ),
    OnboardingPage(
        headline = "Follow Your Teams",
        body = "Set up your favourite teams and never miss a match. Get personalized notifications for boundaries, wickets, and milestones.",
        icon = Icons.Default.Groups,
        iconTint = Blue400
    ),
    OnboardingPage(
        headline = "Stay Informed",
        body = "Enable notifications for live updates, win probability shifts, and AI-generated insights.",
        icon = Icons.Default.Notifications,
        iconTint = Gold400
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBase)
    ) {
        // Subtle radial gold glow from center (spec: gold at 3%)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Gold400.copy(alpha = 0.03f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.8f
                )
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip button — top right, ghost style
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.space2),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onComplete) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }

            // Illustration area: 320dp, parallax offset
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val parallaxOffset = pageOffset * 0.5f // Illustration moves at 0.5x speed

                OnboardingPageContent(
                    page = pages[page],
                    parallaxFraction = parallaxOffset
                )
            }

            // Page indicator — 3 dots, 8dp each, 8dp spacing
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = Spacing.space4)
            ) {
                repeat(pages.size) { index ->
                    val isActive = pagerState.currentPage == index
                    val dotColor by animateColorAsState(
                        targetValue = if (isActive) Gold400 else Surface3,
                        animationSpec = tween(AnimationDuration.fast),
                        label = "dot_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                    )
                }
            }

            // Continue / Get Started button — full width minus 16dp margins
            Button(
                onClick = {
                    if (isLastPage) onComplete()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenHorizontal)
                    .height(56.dp),
                shape = RoundedCornerShape(CornerRadius.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold400,
                    contentColor = TextInverse
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = if (isLastPage) "Get Started" else "Continue",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(Spacing.space5))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    parallaxFraction: Float
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Illustration container — abstract icon in diamond with parallax
        Box(
            modifier = Modifier
                .size(180.dp)
                .graphicsLayer { translationX = parallaxFraction * 120f }
                .drawBehind {
                    rotate(45f) {
                        drawRoundRect(
                            color = Surface1,
                            size = size * 0.7f,
                            topLeft = androidx.compose.ui.geometry.Offset(
                                size.width * 0.15f, size.height * 0.15f
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx())
                        )
                        drawRoundRect(
                            color = Gold400.copy(alpha = 0.12f),
                            size = size * 0.7f,
                            topLeft = androidx.compose.ui.geometry.Offset(
                                size.width * 0.15f, size.height * 0.15f
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = page.iconTint
            )
        }

        Spacer(modifier = Modifier.height(Spacing.space6))

        // Headline — h1, text-primary, centered
        Text(
            text = page.headline,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.01).sp
        )

        Spacer(modifier = Modifier.height(Spacing.space3))

        // Body — body-lg, text-secondary, centered, max 280dp
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.widthIn(max = 280.dp)
        )
    }
}
