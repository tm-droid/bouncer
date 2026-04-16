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
fun StatisticsTab_Extracted(match: CricketMatch, scorecard: Scorecard?) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text(text = "STATS", color = Gold400, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) }
        item { MatchStatsOverviewCard(match, scorecard) }

        scorecard?.innings?.firstOrNull()?.let { innings ->
            item { Spacer(modifier = Modifier.height(8.dp)); Text(text = "BATTING LEADERS", color = ChartBlue, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp) }
            item { BattingLeadersCard(innings) }
        }

        scorecard?.innings?.firstOrNull()?.let { innings ->
            if (innings.bowlers.isNotEmpty()) {
                item { Spacer(modifier = Modifier.height(8.dp)); Text(text = "BOWLING LEADERS", color = StatusSuccess, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp) }
                item { BowlingLeadersCard(innings) }
            }
        }

        scorecard?.innings?.firstOrNull()?.let { innings ->
            if (innings.partnerships.isNotEmpty()) {
                item { Spacer(modifier = Modifier.height(8.dp)); Text(text = "PARTNERSHIP BREAKDOWN", color = Gold400, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp) }
                item { PartnershipStatsCard(innings) }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
