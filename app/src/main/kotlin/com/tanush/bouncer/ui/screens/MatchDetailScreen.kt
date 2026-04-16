package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.models.ProbabilityEvent
import com.tanush.bouncer.ui.components.InAppBanner
import com.tanush.bouncer.ui.components.ProbabilityGraphView
import com.tanush.bouncer.ui.components.WhatIfScenarioDialog
import com.tanush.bouncer.ui.components.glass.GlassPrimaryButton
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.utils.NotificationHelper
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import kotlinx.coroutines.launch

@Composable
fun MatchDetailScreen(
    matchId: String,
    match: CricketMatch? = null,
    liveActivityViewModel: LiveActivityViewModel? = null
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    // Collect real probability events from ViewModel, convert to models.ProbabilityEvent
    val vmEvents by liveActivityViewModel?.probabilityEvents?.collectAsState()
        ?: remember { mutableStateOf(emptyMap()) }
    val vmFirstInnings by liveActivityViewModel?.firstInningsEndBall?.collectAsState()
        ?: remember { mutableStateOf(emptyMap()) }

    val realEvents = vmEvents[matchId]
    val baseEvents = remember(realEvents) {
        if (!realEvents.isNullOrEmpty()) {
            realEvents.map { vmEvent ->
                ProbabilityEvent(
                    timestamp = vmEvent.timestamp,
                    ball = vmEvent.ball,
                    over = vmEvent.over,
                    probability = vmEvent.probability,
                    eventType = when (vmEvent.eventType) {
                        com.tanush.bouncer.viewmodels.ProbabilityEventType.WICKET -> ProbabilityEvent.EventType.WICKET
                        com.tanush.bouncer.viewmodels.ProbabilityEventType.BOUNDARY -> ProbabilityEvent.EventType.BOUNDARY
                        com.tanush.bouncer.viewmodels.ProbabilityEventType.DOT_BALL -> ProbabilityEvent.EventType.DOT_BALL
                        else -> ProbabilityEvent.EventType.OTHER
                    },
                    description = vmEvent.description,
                    impact = vmEvent.impact
                )
            }
        } else {
            // Single-point fallback from current match probability
            val prob = match?.calculateHeuristicProbability() ?: 50.0
            listOf(
                ProbabilityEvent(
                    timestamp = java.util.Date(),
                    ball = 1,
                    over = 1,
                    probability = prob,
                    eventType = ProbabilityEvent.EventType.OTHER,
                    description = "Current state",
                    impact = 0.0
                )
            )
        }
    }

    val firstInningsEndBall = vmFirstInnings[matchId] ?: 0

    // Build score text from match data
    val battingTeam = match?.teams?.firstOrNull { it.isBatting }
    val currentScore = if (battingTeam != null) {
        "${battingTeam.name} ${battingTeam.score ?: "--"} (${battingTeam.overs ?: "--"})"
    } else {
        "Match ID: $matchId"
    }
    val latestDesc = if (baseEvents.isNotEmpty()) baseEvents.last().description else "Awaiting data"

    var showWhatIf by remember { mutableStateOf(false) }
    var predictiveOverlay by remember { mutableStateOf<List<Pair<Int, Double>>?>(null) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimens.screenHorizontal, vertical = dimens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing.md)
        ) {
            Text(
                text = "Match Probability",
                style = typography.headingMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                text = currentScore,
                style = typography.bodySmall,
                color = colors.textSecondary
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(dimens.spacing.md)
            ) {
                ProbabilityGraphView(
                    events = baseEvents,
                    firstInningsEndBall = firstInningsEndBall,
                    currentMatchScore = currentScore,
                    latestEventDescription = latestDesc,
                    matchResult = "",
                    predictiveOverlay = predictiveOverlay
                )
            }

            GlassPrimaryButton(
                text = "Simulate What-If",
                onClick = { showWhatIf = true },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))
        }

        if (showWhatIf) {
            WhatIfScenarioDialog(
                onSimulate = { scenario ->
                    val last = baseEvents.last()
                    val nextBall = last.ball + 1
                    val nextProb = when (scenario) {
                        "WICKET" -> (last.probability - 10.0).coerceAtLeast(0.0)
                        "BOUNDARY" -> (last.probability + 8.0).coerceAtMost(100.0)
                        "DOT_BALL" -> (last.probability - 2.0).coerceIn(0.0, 100.0)
                        else -> last.probability
                    }
                    predictiveOverlay = baseEvents.map { it.ball to it.probability } + listOf(nextBall to nextProb)
                    bannerMessage = when (scenario) {
                        "WICKET" -> "Simulated: Wicket — probability shifted down"
                        "BOUNDARY" -> "Simulated: Boundary — probability shifted up"
                        "DOT_BALL" -> "Simulated: Dot ball — slight shift"
                        else -> "Simulated event"
                    }
                    NotificationHelper.notify(ctx, nextBall, "Match Simulation", bannerMessage ?: "Event simulated")
                    showWhatIf = false
                    scope.launch {
                        kotlinx.coroutines.delay(3500)
                        bannerMessage = null
                    }
                },
                onDismiss = { showWhatIf = false }
            )
        }

        bannerMessage?.let { msg ->
            InAppBanner(message = msg, modifier = Modifier.padding(top = dimens.sm))
        }
    }
}
