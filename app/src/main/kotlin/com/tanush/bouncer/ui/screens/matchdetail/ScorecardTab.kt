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
fun ScorecardTab_Extracted(scorecard: Scorecard?) {
    if (scorecard == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Gold400)
        }
    } else {
        var selectedInnings by remember { mutableIntStateOf(0) }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.05f)).border(BorderWidth.hairline, Gold400.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(4.dp)) {
                    scorecard.innings.forEachIndexed { index, inning ->
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (selectedInnings == index) Gold400 else Color.Transparent).clickable { selectedInnings = index }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(inning.teamName.uppercase(), color = if (selectedInnings == index) Color.Black else TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            scorecard.innings.getOrNull(selectedInnings)?.let { inning ->
                item { EliteBattingTable(inning) }
                if (inning.bowlers.isNotEmpty()) item { EliteBowlingTable(inning) }
                if (inning.fallOfWickets.isNotEmpty()) item { EliteFowTable(inning.fallOfWickets) }
                if (inning.partnerships.isNotEmpty()) item { PartnershipBreakdownCard(teamName = inning.teamName, partnerships = inning.partnerships.map { PartnershipEntry(it.player1, it.contribution1.toIntOrNull() ?: 0, 0, it.player2, it.contribution2.toIntOrNull() ?: 0, 0, it.runs.toIntOrNull() ?: 0, it.balls.toIntOrNull() ?: 0, 0.0, 0) }) }
            }
        }
    }
}