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
fun SquadsTab_Extracted(match: CricketMatch, scorecard: Scorecard?) {
    val team1 = match.teams.getOrNull(0)
    val team2 = match.teams.getOrNull(1)

    val team1Logo = TeamLogoManager.getBestLogoUrl(team1?.name, team1?.logoUrl)
    val team2Logo = TeamLogoManager.getBestLogoUrl(team2?.name, team2?.logoUrl)

    val team1Color = ChartBlue
    val team2Color = ErrorRed

    val team1Players = scorecard?.innings?.getOrNull(0)?.let { inning ->
        val batsmen = inning.batsmen.map { it.name }
        val bowlers = inning.bowlers.map { it.name }
        (batsmen + bowlers).distinct()
    } ?: emptyList()

    val team2Players = scorecard?.innings?.getOrNull(1)?.let { inning ->
        val batsmen = inning.batsmen.map { it.name }
        val bowlers = inning.bowlers.map { it.name }
        (batsmen + bowlers).distinct()
    } ?: emptyList()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.08f)).border(BorderWidth.hairline, Gold400.copy(alpha = 0.25f), RoundedCornerShape(20.dp)).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(team1Color.copy(alpha = 0.15f)).border(BorderWidth.hairline, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                        CoilImage(model = team1Logo, contentDescription = team1?.name, modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)))
                    }
                    Column {
                        Text(text = team1?.acronym ?: "T1", color = team1Color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        Text(text = "Playing XI", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
                    }
                }

                Box(modifier = Modifier.width(1.dp).height(32.dp).background(Color.White.copy(alpha = 0.1f)))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = team2?.acronym ?: "T2", color = team2Color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        Text(text = "Playing XI", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
                    }
                    Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(team2Color.copy(alpha = 0.15f)).border(BorderWidth.hairline, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                        CoilImage(model = team2Logo, contentDescription = team2?.name, modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)))
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = (team1?.name ?: "Team 1").uppercase(), color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(text = (team2?.name ?: "Team 2").uppercase(), color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }

        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.08f)).border(BorderWidth.hairline, Gold400.copy(alpha = 0.20f), RoundedCornerShape(24.dp))) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f).background(Brush.horizontalGradient(colors = listOf(team1Color.copy(alpha = 0.10f), team1Color.copy(alpha = 0.02f)))).padding(vertical = 8.dp)) {
                    if (team1Players.isEmpty()) Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text(text = "Loading...", color = TextMuted, fontSize = 11.sp) } else team1Players.forEachIndexed { index, player -> SplitPlayerRow(playerName = player, teamColor = team1Color, isLeftSide = true, playerIndex = index, totalPlayers = team1Players.size) }
                }

                Box(modifier = Modifier.width(1.dp).then(if (team1Players.isNotEmpty() || team2Players.isNotEmpty()) Modifier.height((maxOf(team1Players.size, team2Players.size, 1) * 48).dp) else Modifier.height(200.dp)).background(Brush.verticalGradient(colors = listOf(Color.Transparent, Gold400.copy(alpha = 0.40f), Gold400.copy(alpha = 0.50f), Gold400.copy(alpha = 0.40f), Color.Transparent))))

                Column(modifier = Modifier.weight(1f).background(Brush.horizontalGradient(colors = listOf(team2Color.copy(alpha = 0.02f), team2Color.copy(alpha = 0.10f)))).padding(vertical = 8.dp)) {
                    if (team2Players.isEmpty()) Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text(text = "Loading...", color = TextMuted, fontSize = 11.sp) } else team2Players.forEachIndexed { index, player -> SplitPlayerRow(playerName = player, teamColor = team2Color, isLeftSide = false, playerIndex = index, totalPlayers = team2Players.size) }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}