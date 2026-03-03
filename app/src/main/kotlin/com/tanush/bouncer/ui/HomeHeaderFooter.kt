package com.tanush.bouncer.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

// ==========================================
// BOUNCER – Home Header & Footer v3.0
// Design Spec: android/design/BOUNCER_DESIGN_SYSTEM.md
//
// Header: 64dp — "BOUNCER" branding + live badge
//   • 28sp ExtraBold gold text with subtle shimmer
//   • 13sp "Live Cricket Scores" tagline
//   • Right: red pulsing live count badge
//   • Minimal, no excessive decoration
//
// Footer: simple last-updated text, 11sp muted
// ==========================================

@Composable
fun GoldHeader(
    matchCount: Int = 0,
    selectedStatus: String = "Live"
) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    val infiniteTransition = rememberInfiniteTransition(label = "headerShimmer")

    // Subtle shimmer: gold text pulses between 85%-100% opacity
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "headerShimmerAlpha"
    )

    // Live badge pulse
    val livePulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveBadgePulse"
    )

    // Subtle ambient gold glow behind header
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.goldPrimary.copy(alpha = 0.10f * shimmerAlpha),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.2f, size.height * 0.5f),
                        radius = size.width * 0.5f
                    ),
                    radius = size.width * 0.5f,
                    center = Offset(size.width * 0.2f, size.height * 0.5f)
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimens.screenHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Left: BOUNCER branding (etched gold gradient) ──
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Bouncer",
                    style = TextStyle(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        brush = Brush.verticalGradient(
                            listOf(
                                colors.textPrimary,
                                colors.goldPrimary,
                                colors.goldDark
                            )
                        )
                    ),
                    modifier = Modifier.graphicsLayer { alpha = shimmerAlpha }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "LIVE CRICKET SCORES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textMuted.copy(alpha = 0.6f),
                    letterSpacing = 2.sp
                )
            }

            // ── Right: Live match count badge ──
            if (matchCount > 0) {
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = colors.statusLive.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .graphicsLayer { alpha = livePulse }
                                .background(colors.statusLive, CircleShape)
                        )
                        Text(
                            text = "$matchCount Live",
                            color = colors.statusLive,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// App Footer — minimal, last-updated text
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun AppFooter() {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = dimens.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Decorative gold hairline
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            colors.goldPrimary.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    RoundedCornerShape(50)
                )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Bouncer v1.2 · Cricket Analytics",
            color = colors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp
        )
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Empty Matches State — premium empty state
// Backward-compatible public composable
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun EmptyMatchesState(selectedStatus: String) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    val typography = BouncerDesignSystem.typography
    val infiniteTransition = rememberInfiniteTransition(label = "emptyPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emptyPulseAlpha"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emptyPulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.xxl, vertical = dimens.xxxl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.lg)
        ) {
            // Cricket icon with layered glow
            Box(contentAlignment = Alignment.Center) {
                // Outer glow ring
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                            alpha = pulseAlpha * 0.5f
                        }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    colors.goldPrimary.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Inner icon container
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(colors.glassBackground, CircleShape)
                        .border(1.dp, colors.goldPrimary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(com.tanush.bouncer.R.drawable.ic_cricket_ball),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = colors.goldPrimary.copy(alpha = 0.7f)
                    )
                }
            }

            Text(
                text = "No ${selectedStatus.lowercase()} matches",
                style = typography.headingMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                letterSpacing = 1.sp
            )

            Text(
                text = "Pull down to refresh or try adjusting your filters",
                style = typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = dimens.lg)
            )

            // Tip pill
            Surface(
                color = colors.glassBackground,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.glassBorder)
            ) {
                Text(
                    text = "Tip: Check 'Upcoming' for later today",
                    color = colors.goldPrimary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}
