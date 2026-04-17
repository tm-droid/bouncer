package com.tanush.bouncer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.BouncerImportance
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.components.GlassHero
import com.tanush.bouncer.ui.components.PulsingBadge
import com.tanush.bouncer.ui.components.RadarChart
import com.tanush.bouncer.ui.components.StaggeredFadeIn
import com.tanush.bouncer.ui.components.celebrations.ConfettiBurst
import com.tanush.bouncer.ui.components.celebrations.MilestoneBadge
import com.tanush.bouncer.ui.components.celebrations.SparkParticles
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.theme.*

/**
 * Achievements Screen
 * Achievement showcase with radar charts and progress tracking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrophyRoomScreen(
    onBackClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val scrollState = rememberScrollState()

    GlassBloomBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Achievements",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.padding(start = 8.dp).clip(CircleShape).background(colors.glassSurface)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "Back", tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                        }
                    },
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = hierarchy.secondary.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.backgroundBase.copy(alpha = 0.8f))
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Hero Stats Card
                StaggeredFadeIn(index = 0) {
                    HeroProgressCardTechnical()
                }

                // Medal Collection
                StaggeredFadeIn(index = 1) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(modifier = Modifier.size(4.dp, 18.dp).background(hierarchy.secondary.primary, RoundedCornerShape(2.dp)))
                                Text("MEDAL COLLECTION", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                            }
                            Text("Filter", color = hierarchy.secondary.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                TrophyItemCard(
                                    modifier = Modifier.weight(1f),
                                    title = "The Centurion",
                                    description = "Score 100 runs in a single innings.",
                                    tier = "GOLD",
                                    icon = Icons.Default.SportsCricket,
                                    tierColor = hierarchy.secondary.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                TrophyItemCard(
                                    modifier = Modifier.weight(1f),
                                    title = "The Hat-Trick",
                                    description = "Take 3 wickets in 3 consecutive balls.",
                                    tier = "SILVER",
                                    icon = Icons.Default.SportsBaseball,
                                    tierColor = colors.textSecondary
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                TrophyItemCard(
                                    modifier = Modifier.weight(1f),
                                    title = "Five-Wicket Haul",
                                    description = "Take 5 wickets in a single match.",
                                    tier = "BRONZE",
                                    icon = Icons.Default.Stadium,
                                    tierColor = hierarchy.secondary.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                TrophyItemCard(
                                    modifier = Modifier.weight(1f),
                                    title = "Golden Duck",
                                    description = "Get out on the first ball faced.",
                                    tier = "LOCKED",
                                    icon = Icons.Default.Pets,
                                    isLocked = true
                                )
                            }
                        }
                    }
                }

                // Career Radar
                StaggeredFadeIn(index = 2) {
                    CareerRadarSectionTechnical()
                }
            }
        }
        
        // Refined FAB
        Box(modifier = Modifier.fillMaxSize()) {
            FloatingActionButton(
                onClick = {},
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 100.dp)
                    .size(60.dp),
                containerColor = colors.goldPrimary,
                contentColor = colors.backgroundBase,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(12.dp)
            ) {
                Icon(Icons.Default.Insights, contentDescription = "Insights", modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun HeroProgressCardTechnical() {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        // Ambient gold dust particles behind the hero card
        SparkParticles(
            modifier = Modifier.matchParentSize(),
            sparkCount = 12,
            colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.secondary, hierarchy.secondary.primary),
            isActive = true
        )

        GlassHero(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("ACHIEVEMENT PROGRESSION", color = hierarchy.secondary.primary, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.5.sp)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("12", color = colors.textPrimary, fontSize = 56.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
                    Text("/ 50", color = colors.textMuted, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.glassSurface)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = buildAnnotatedString {
                            append("STATUS: ")
                            withStyle(style = SpanStyle(color = hierarchy.secondary.primary, fontWeight = FontWeight.Black)) {
                                append("RISING STAR")
                            }
                        },
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(colors.glassSurface)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.24f)
                            .fillMaxHeight()
                            .background(hierarchy.secondary.primary)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrophyItemCard(
    modifier: Modifier,
    title: String,
    description: String,
    tier: String,
    icon: ImageVector,
    tierColor: Color = TextPrimary,
    isLocked: Boolean = false
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    var showConfetti by remember { mutableStateOf(false) }

    // Trigger confetti once for unlocked cards on first composition
    LaunchedEffect(isLocked) {
        if (!isLocked) {
            showConfetti = true
        }
    }

    Box(modifier = modifier) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { /* Detail view */ },
            importance = if (isLocked) BouncerImportance.MINIMAL else BouncerImportance.STANDARD,
            cornerRadius = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.glassSurface)
                        .border(0.5.dp, colors.glassSurface, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isLocked) {
                        val infiniteTransition = rememberInfiniteTransition(label = "medal")
                        val pulse by infiniteTransition.animateFloat(
                            initialValue = 0.8f, targetValue = 1.2f,
                            animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
                            label = "pulse"
                        )
                        Box(modifier = Modifier.size(60.dp).blur(20.dp).background(tierColor.copy(alpha = 0.1f * pulse), CircleShape))
                    }

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .then(
                                if (!isLocked) Modifier.background(tierColor.copy(alpha = 0.1f), CircleShape).border(1.dp, tierColor.copy(alpha = 0.3f), CircleShape)
                                else Modifier.border(1.dp, colors.glassSurface, CircleShape)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            null,
                            tint = if (!isLocked) tierColor else colors.textMuted,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    if (isLocked) {
                        Icon(Icons.Default.Lock, null, tint = colors.textMuted, modifier = Modifier.size(16.dp).align(Alignment.BottomEnd).padding(8.dp))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title.uppercase(), color = if (isLocked) colors.textMuted else colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    if (!isLocked && tier in listOf("GOLD", "SILVER", "BRONZE")) {
                        PulsingBadge(text = tier)
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.glassSurface)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(tier, color = colors.textMuted, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                    }
                    Text(description, color = colors.textSecondary, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Confetti burst overlay for unlocked trophies
        if (!isLocked) {
            ConfettiBurst(
                modifier = Modifier.matchParentSize(),
                burstPoint = Offset(100f, 100f),
                particleCount = 30,
                colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.secondary, colors.statusInfo),
                isActive = showConfetti,
                onComplete = { showConfetti = false }
            )
        }
    }
}

@Composable
private fun CareerRadarSectionTechnical() {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors

    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(4.dp, 18.dp).background(hierarchy.secondary.primary, RoundedCornerShape(2.dp)))
                Text("CAREER RADAR", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }
            PulsingBadge(text = "SYNCED")
        }

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            importance = BouncerImportance.STANDARD,
            cornerRadius = 32.dp
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                Box(modifier = Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                    RadarChart(
                        data = mapOf(
                            "POWER" to 90f,
                            "TIMING" to 70f,
                            "SPEED" to 85f,
                            "FIELDING" to 60f,
                            "STAMINA" to 80f,
                            "CONTROL" to 95f
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("PEAK METRIC", color = colors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text("POWER HITTING", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("PROJECTED", color = colors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Text("+12.4% GAIN", color = hierarchy.secondary.primary, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
