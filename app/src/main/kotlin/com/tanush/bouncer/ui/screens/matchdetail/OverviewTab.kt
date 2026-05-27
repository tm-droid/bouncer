package com.tanush.bouncer.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.*
import com.tanush.bouncer.models.FallOfWicket as ModelFallOfWicket
import com.tanush.bouncer.network.CricBuzzScraper
import com.tanush.bouncer.services.LiveCommentaryManager
import com.tanush.bouncer.ui.components.*
import com.tanush.bouncer.ui.components.PowerPlayPhase
import com.tanush.bouncer.ui.components.premium.*
import com.tanush.bouncer.ui.components.premium.MomentumEvent
import com.tanush.bouncer.models.AchievementKind
import com.tanush.bouncer.ui.components.celebrations.*
import com.tanush.bouncer.ui.components.ToastType
import androidx.compose.ui.platform.LocalContext
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import io.sentry.Sentry
import io.sentry.Breadcrumb
import io.sentry.SentryLevel

@Composable
fun OverviewTab_Extracted(
    match: CricketMatch,
    batsmen: List<ScrapedBatsman>,
    bowler: ScrapedBowler?,
    recentBalls: List<String>,
    commentary: List<String>,
    liveCurrentRunRate: String?,
    liveRequiredRunRate: String?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(
                text = "SUMMARY",
                color = Gold400,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )
        }

        item {
            QuickMatchStatsBar(
                currentRunRate = liveCurrentRunRate ?: match.score?.currentRunRate ?: "--",
                requiredRunRate = liveRequiredRunRate ?: match.score?.requiredRunRate,
                partnership = "42(28)",
                lastWicket = "23(18)"
            )
        }

        item { EliteAiInsightBanner(commentary.firstOrNull()) }

        item { EliteImpactHeroCard() }

        item { EliteForecastCarousel() }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TechnicalQuickAction(modifier = Modifier.weight(1f), label = "FIELD MAP", icon = Icons.Outlined.ZoomOutMap, accentColor = Gold400)
                TechnicalQuickAction(modifier = Modifier.weight(1f), label = "HOTSPOTS", icon = Icons.Outlined.LocalFireDepartment, accentColor = ErrorRed)
                TechnicalQuickAction(modifier = Modifier.weight(1f), label = "TRAJECTORY", icon = Icons.Outlined.Timeline, accentColor = ChartBlue)
            }
        }

        item {
            PowerPlayStatsCard(teamName = match.teams.firstOrNull { it.isBatting }?.name ?: "Batting Team", phases = listOf(PowerPlayPhase("PP1", 1, 1, 6, 45, 1, 36, 6, 1, 12), PowerPlayPhase("PP2", 2, 7, 40, 180, 4, 204, 12, 2, 80)))
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val striker = batsmen.firstOrNull { it.isStriker } ?: batsmen.firstOrNull()
                striker?.let { MicroStatCard(modifier = Modifier.weight(1f), label = "STRIKER", name = it.name, value = it.runs, subValue = "(${it.balls})", icon = Icons.Default.SportsCricket, accentColor = Gold400) }
                bowler?.let { currentBowler -> MicroStatCard(modifier = Modifier.weight(1f), label = "BOWLER", name = currentBowler.name, value = "${currentBowler.wickets}/${currentBowler.runs}", subValue = "(${currentBowler.overs})", icon = Icons.Default.SportsBaseball, accentColor = ChartBlue) }
            }
        }

        item {
            EliteMomentumTracker(
                events = listOf(
                    MomentumEvent("18.4", "WICKET", "Bowled · 142kph Yorker", "W", Icons.Default.HeartBroken, WicketRed),
                    MomentumEvent("18.3", "MASSIVE SIX", "Pull Shot · 94 Meters", "6", Icons.Default.Flare, StatusSuccess)
                )
            )
        }

        item {
            val team1 = match.teams.firstOrNull()
            val team2 = match.teams.getOrNull(1)
            if (team1 != null && team2 != null) {
                WinPredictionGauge(team1Name = team1.name.take(3).uppercase(), team2Name = team2.name.take(3).uppercase(), team1Probability = 0.58f, team1Color = ChartBlue, team2Color = WicketRed)
            }
        }

        if (recentBalls.isNotEmpty()) item { EliteRecentTelemetry(recentBalls) }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}
