package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.*
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsOverviewScreen(onBackClick: () -> Unit) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "ANALYTICS HUB",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                        color = hierarchy.secondary.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        GlassBloomBackground(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(40.dp)
            ) {
                // Section 1 — Career Radar Chart
                StaggeredFadeIn(index = 0) {
                    CareerRadarSection()
                }

                // Section 2 — Player Impact Ratings
                StaggeredFadeIn(index = 1) {
                    PlayerImpactSection()
                }

                // Section 3 — Historical Trends
                StaggeredFadeIn(index = 2) {
                    TrendsSection()
                }

                // Section 4 — Run Rate Speedometer
                StaggeredFadeIn(index = 3) {
                    RunRateSection()
                }

                // Section 5 — Match Momentum
                StaggeredFadeIn(index = 4) {
                    MomentumSection()
                }

                // Section 6 — Game Overview
                StaggeredFadeIn(index = 5) {
                    GameOverviewSection()
                }
            }
        }
    }
}

// ─── Section 1: Career Radar ─────────────────────────────────────

@Composable
private fun CareerRadarSection() {
    val colors = BouncerDesignSystem.colors

    GlassCard(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth(),
        importance = BouncerImportance.CRITICAL
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "CAREER PERFORMANCE RADAR",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(32.dp))

            RadarChart(
                data = mapOf(
                    "Batting" to 85f,
                    "Bowling" to 72f,
                    "Fielding" to 94f,
                    "Strategy" to 68f,
                    "Pace" to 78f
                ),
                modifier = Modifier.size(220.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PulsingBadge("Elite Power")
                PulsingBadge("Pro Timing")
                PulsingBadge("A-Grade Spin")
            }
        }
    }
}

// ─── Section 2: Player Impact Ratings ────────────────────────────

@Composable
private fun PlayerImpactSection() {
    val colors = BouncerDesignSystem.colors

    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "TOP ANALYSTS",
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold
        )

        PlayerImpactRating(
            playerName = "Virat Kohli",
            impactScore = 9.4f,
            stats = "Avg 53.4 | SR 138.2"
        )
        PlayerImpactRating(
            playerName = "Steve Smith",
            impactScore = 8.7f,
            stats = "Avg 48.1 | SR 129.5"
        )
        PlayerImpactRating(
            playerName = "Kane Williamson",
            impactScore = 8.2f,
            stats = "Avg 45.6 | SR 125.3"
        )
    }
}

// ─── Section 3: Historical Trends ────────────────────────────────

@Composable
private fun TrendsSection() {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors

    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "MARKET TRENDS",
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                StatCard(
                    label = "Avg Score",
                    value = "168.4",
                    trend = 4.2f
                )
                Spacer(modifier = Modifier.height(8.dp))
                MiniSparkline(
                    data = listOf(152f, 160f, 155f, 163f, 168f, 170f, 168.4f),
                    color = hierarchy.secondary.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                StatCard(
                    label = "Wicket Rate",
                    value = "7.2",
                    trend = -1.1f
                )
                Spacer(modifier = Modifier.height(8.dp))
                MiniSparkline(
                    data = listOf(8.0f, 7.8f, 7.5f, 7.6f, 7.3f, 7.1f, 7.2f),
                    color = colors.statusLive
                )
            }
        }
    }
}

// ─── Section 4: Run Rate Speedometer ─────────────────────────────

@Composable
private fun RunRateSection() {
    val colors = BouncerDesignSystem.colors

    GlassCard(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth(),
        importance = BouncerImportance.STANDARD
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "RUN RATE ANALYSIS",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(24.dp))

            RunRateSpeedometer(
                currentRunRate = 8.4f,
                requiredRunRate = 9.2f,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

// ─── Section 5: Match Momentum ───────────────────────────────────

@Composable
private fun MomentumSection() {
    val colors = BouncerDesignSystem.colors

    GlassCard(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth(),
        importance = BouncerImportance.STANDARD
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "MATCH MOMENTUM",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(24.dp))

            MomentumIndicator(
                momentum = 0.35f,
                team1Name = "India",
                team2Name = "Australia"
            )
        }
    }
}

// ─── Section 6: Game Overview ────────────────────────────────────

@Composable
private fun GameOverviewSection() {
    val colors = BouncerDesignSystem.colors

    GlassCard(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth(),
        importance = BouncerImportance.CRITICAL
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "WIN PROBABILITY",
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(24.dp))

            PremiumWinProbabilityRing(
                team1Name = "India",
                team2Name = "Australia",
                team1Probability = 0.62f,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
