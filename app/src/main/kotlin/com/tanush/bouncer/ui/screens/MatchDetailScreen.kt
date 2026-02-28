package com.tanush.bouncer.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.ProbabilityEvent
import com.tanush.bouncer.ui.components.ProbabilityGraphView
import com.tanush.bouncer.ui.components.WhatIfScenarioDialog
import com.tanush.bouncer.ui.components.glass.*
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.NotificationHelper
import com.tanush.bouncer.ui.components.InAppBanner

@Composable
fun MatchDetailScreen(matchId: String) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    
    // Demo/mock data for probability events
    val baseEvents = remember {
        (1..120).map {
            ProbabilityEvent(
                timestamp = java.util.Date(),
                ball = it,
                over = (it / 6) + 1,
                probability = 50 + (Math.sin(it / 10.0) * 20),
                eventType = ProbabilityEvent.EventType.OTHER,
                description = "Ball $it",
                impact = if (it % 30 == 0) 10.0 else 0.0
            )
        }
    }
    var showWhatIf by remember { mutableStateOf(false) }
    var predictiveOverlay by remember { mutableStateOf<List<Pair<Int, Double>>?>(null) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    GlassBloomBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(dimens.screenHorizontal)
        ) {
            Text(
                text = "MATCH PROBABILITY",
                style = typography.headingLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 2.sp,
                modifier = Modifier.staggeredFadeIn(0)
            )
            
            Text(
                text = "ID: $matchId".uppercase(),
                color = colors.goldPrimary,
                style = typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .staggeredFadeIn(1)
            )
            
            Spacer(modifier = Modifier.height(dimens.spacing.xl))
            
            Box(modifier = Modifier.staggeredFadeIn(2)) {
                ProbabilityGraphView(
                    events = baseEvents,
                    firstInningsEndBall = 60,
                    currentMatchScore = "Team A 120/3 (15.0)",
                    latestEventDescription = "Last ball: 4 runs",
                    matchResult = "",
                    predictiveOverlay = predictiveOverlay
                )
            }
            
            Spacer(modifier = Modifier.height(dimens.spacing.lg))
            
            GlassPrimaryButton(
                text = "Simulate What-If",
                onClick = { showWhatIf = true },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .staggeredFadeIn(3)
            )
        }
        if (showWhatIf) {
            WhatIfScenarioDialog(
                onSimulate = { scenario ->
                    // Simple mock: extend the probability curve based on scenario
                    val last = baseEvents.last()
                    val nextBall = last.ball + 1
                    val nextProb = when (scenario) {
                        "WICKET" -> (last.probability - 10.0).coerceAtLeast(0.0)
                        "BOUNDARY" -> (last.probability + 8.0).coerceAtMost(100.0)
                        "DOT_BALL" -> (last.probability - 2.0).coerceIn(0.0, 100.0)
                        else -> last.probability
                    }
                    predictiveOverlay = baseEvents.map { it.ball to it.probability } + listOf(nextBall to nextProb)
                    // Show in-app banner
                    bannerMessage = when (scenario) {
                        "WICKET" -> "Simulated: Wicket — probability shifted down"
                        "BOUNDARY" -> "Simulated: Boundary — probability shifted up"
                        "DOT_BALL" -> "Simulated: Dot ball — slight shift"
                        else -> "Simulated event"
                    }
                    // Send a system notification as well
                    NotificationHelper.notify(ctx, nextBall, "Match Simulation", bannerMessage ?: "Event simulated")
                    // Hide dialog
                    showWhatIf = false
                    // Clear banner after a short delay (launch from coroutine scope since this is a non-composable callback)
                    scope.launch {
                        kotlinx.coroutines.delay(3500)
                        bannerMessage = null
                    }
                },
                onDismiss = { showWhatIf = false }
            )
        }

        bannerMessage?.let { msg ->
            InAppBanner(message = msg, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
