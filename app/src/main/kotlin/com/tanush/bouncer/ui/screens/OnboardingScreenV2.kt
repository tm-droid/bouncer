package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector, val title: String, val description: String, val highlights: List<String>,
)

private val pages = listOf(
    OnboardingPage(Icons.Default.SportsCricket, "Welcome to Bouncer", "Your premium cricket companion with live scores and match intelligence.", listOf("Live ball-by-ball updates", "Fast, readable match views")),
    OnboardingPage(Icons.Default.Insights, "Live Intelligence", "Get momentum shifts and match context in real time.", listOf("Win-probability insights", "Key-moment summaries")),
    OnboardingPage(Icons.Default.Favorite, "Personalized Feed", "Follow teams and tournaments you care about.", listOf("Quick favorite setup", "Focused content")),
    OnboardingPage(Icons.Default.Notifications, "Smart Alerts", "Stay notified for wickets, milestones, and match starts.", listOf("Readable notifications", "Tuned to your interests")),
    OnboardingPage(Icons.Default.RocketLaunch, "You're All Set", "Start exploring matches, squads, and insights.", listOf("Fast navigation", "Consistent design")),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreenV2(onComplete: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isLastPage = pagerState.currentPage == pages.size - 1

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).padding(spacing.lg),
    ) {
        // Skip button row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!isLastPage) {
                TextButton(onClick = onComplete) {
                    Text("Skip", color = colors.textSecondary)
                }
            }
        }

        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            val p = pages[page]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(p.icon, null, Modifier.size(72.dp), tint = colors.primary)
                Spacer(Modifier.height(spacing.xl))
                Text(p.title, style = typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.onSurface, textAlign = TextAlign.Center)
                Spacer(Modifier.height(spacing.sm))
                Text(p.description, style = typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(spacing.lg))
                p.highlights.forEach { h ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = colors.primary)
                        Spacer(Modifier.padding(start = spacing.sm))
                        Text(h, style = typography.bodyMedium, color = colors.onSurface)
                    }
                    Spacer(Modifier.height(spacing.xs))
                }
            }
        }

        // Page indicators
        Row(
            Modifier.fillMaxWidth().padding(vertical = spacing.lg),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(pages.size) { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (i == pagerState.currentPage) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (i == pagerState.currentPage) colors.primary else colors.surfaceVariant),
                )
            }
        }

        // Next / Get Started button
        Button(
            onClick = {
                if (isLastPage) onComplete()
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
        ) {
            Text(if (isLastPage) "Get Started" else "Next", fontWeight = FontWeight.Bold)
        }
    }
}
