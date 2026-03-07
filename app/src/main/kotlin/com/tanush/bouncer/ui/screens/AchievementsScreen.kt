package com.tanush.bouncer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.Achievement
import com.tanush.bouncer.models.AchievementKind
import com.tanush.bouncer.ui.components.BouncerImportance
import com.tanush.bouncer.ui.components.GlassCard
import com.tanush.bouncer.ui.components.RadarChart
import com.tanush.bouncer.ui.components.SparkleOverlay
import com.tanush.bouncer.ui.components.StaggeredFadeIn
import com.tanush.bouncer.ui.components.celebrations.ConfettiBurst
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import kotlinx.coroutines.delay
import io.sentry.Sentry

// Achievement category for grouping
enum class AchievementCategory(val title: String, val color: @Composable () -> Color) {
    BATTING("BATTING", { Color(0xFF4FC3F7) }),
    BOWLING("BOWLING", { Color(0xFF81C784) }),
    TEAM("TEAM", { Color(0xFFFFB74D) }),
    SPECIAL("SPECIAL", { BouncerDesignSystem.colors.goldPrimary })
}

private fun Achievement.category(): AchievementCategory {
    try {
        return when (id) {
            AchievementKind.RUNS_30, AchievementKind.RUNS_50, AchievementKind.RUNS_75,
            AchievementKind.RUNS_100, AchievementKind.RUNS_150, AchievementKind.RUNS_200,
            AchievementKind.RUNS_250, AchievementKind.RUNS_300, AchievementKind.FASTEST_FIFTY,
            AchievementKind.FASTEST_HUNDRED, AchievementKind.MOST_SIXES -> AchievementCategory.BATTING
        
            AchievementKind.WICKETS_3, AchievementKind.WICKETS_4, AchievementKind.WICKETS_5,
            AchievementKind.WICKETS_10, AchievementKind.MAIDEN_OVER, AchievementKind.HAT_TRICK,
            AchievementKind.BEST_ECONOMY -> AchievementCategory.BOWLING
        
            AchievementKind.TEAM_200, AchievementKind.TEAM_10_WKT_WIN,
            AchievementKind.SUCCESSFUL_CHASE -> AchievementCategory.TEAM
        
            else -> AchievementCategory.SPECIAL
        }
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    unlockedAchievements: Set<AchievementKind>,
    onBackClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val achievements = remember { 
        Achievement.all.map { it.copy(unlocked = unlockedAchievements.contains(it.id)) }
    }
    
    // Group achievements by category
    val groupedAchievements = remember(achievements) {
        achievements.groupBy { it.category() }
    }
    
    // Category stats for progress summary
    val categoryStats = remember(groupedAchievements) {
        AchievementCategory.entries.map { category ->
            val categoryAchievements = groupedAchievements[category] ?: emptyList()
            Triple(category, categoryAchievements.count { it.unlocked }, categoryAchievements.size)
        }
    }

    Scaffold(
        containerColor = colors.backgroundBase,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "THE TROPHY ROOM",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Back", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.IosShare, "Share", tint = colors.goldPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        GlassBloomBackground(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 100.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Stats with enhanced progress
                item(span = { GridItemSpan(2) }) {
                    StaggeredFadeIn(index = 0) {
                        EliteTrophyProgressCard(achievements.count { it.unlocked }, achievements.size)
                    }
                }
                
                // Category Progress Summary (iOS Parity)
                item(span = { GridItemSpan(2) }) {
                    StaggeredFadeIn(index = 1) {
                        CategoryProgressSummary(categoryStats)
                    }
                }

                // Category sections with achievements
                var gridIndex = 2
                AchievementCategory.entries.forEach { category ->
                    val categoryAchievements = groupedAchievements[category] ?: emptyList()
                    if (categoryAchievements.isNotEmpty()) {
                        val headerIdx = gridIndex++
                        item(span = { GridItemSpan(2) }) {
                            StaggeredFadeIn(index = headerIdx) {
                                CategoryHeader(
                                    category = category,
                                    unlockedCount = categoryAchievements.count { it.unlocked },
                                    totalCount = categoryAchievements.size
                                )
                            }
                        }
                        
                        categoryAchievements.forEachIndexed { i, achievement ->
                            val achieveIdx = gridIndex + i
                            item {
                                StaggeredFadeIn(index = achieveIdx) {
                                    EliteMedalCard(achievement, category.color())
                                }
                            }
                        }
                        gridIndex += categoryAchievements.size
                    }
                }
                
                // Career Radar Section
                val radarIdx = gridIndex
                item(span = { GridItemSpan(2) }) {
                    StaggeredFadeIn(index = radarIdx) {
                        Column(modifier = Modifier.padding(vertical = 24.dp)) {
                            Text(
                                "CAREER RADAR", 
                                color = colors.textSecondary, 
                                fontSize = 11.sp, 
                                fontWeight = FontWeight.Bold, 
                                letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            CareerRadarChartCard()
                        }
                    }
                }
            }
            
            // Insights FAB
            FloatingActionButton(
                onClick = {},
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                containerColor = colors.goldPrimary,
                contentColor = Color.Black,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Insights, null)
            }
        }
    }
}

@Composable
private fun EliteTrophyProgressCard(unlocked: Int, total: Int) {
    val colors = BouncerDesignSystem.colors
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        importance = BouncerImportance.CRITICAL
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("TOTAL PROGRESS", color = colors.goldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(unlocked.toString(), color = colors.textPrimary, fontSize = 42.sp, fontWeight = FontWeight.Black)
                Text("/ $total", color = colors.textTertiary, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
            }
            
            // Elite Status based on progress
            val eliteStatus = when {
                unlocked == total -> "Legend"
                unlocked >= total * 0.75 -> "Champion"
                unlocked >= total * 0.5 -> "Rising Star"
                unlocked >= total * 0.25 -> "Contender"
                else -> "Rookie"
            }
            Text("Elite Status: $eliteStatus", color = colors.textSecondary, fontSize = 13.sp)
            
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(colors.glassSurface)) {
                Box(modifier = Modifier.fillMaxWidth(unlocked.toFloat() / total).fillMaxHeight().background(colors.goldPrimary).shadow(8.dp, spotColor = colors.goldPrimary))
            }
        }
    }
}

@Composable
private fun CategoryProgressSummary(categoryStats: List<Triple<AchievementCategory, Int, Int>>) {
    val colors = BouncerDesignSystem.colors
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            categoryStats.forEach { (category, unlocked, total) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Category icon
                    val icon = when (category) {
                        AchievementCategory.BATTING -> Icons.Default.SportsCricket
                        AchievementCategory.BOWLING -> Icons.Default.Circle
                        AchievementCategory.TEAM -> Icons.Default.Groups
                        AchievementCategory.SPECIAL -> Icons.Default.Star
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                category.color().copy(alpha = 0.1f),
                                CircleShape
                            )
                            .border(0.5.dp, category.color().copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = category.title,
                            tint = category.color(),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "$unlocked/$total",
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = category.title,
                        color = colors.textMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    category: AchievementCategory,
    unlockedCount: Int,
    totalCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(category.color(), CircleShape)
            )
            Text(
                text = category.title,
                color = category.color(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }
        
        Surface(
            color = category.color().copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "$unlockedCount of $totalCount",
                color = category.color(),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun EliteMedalCard(achievement: Achievement, accentColor: Color) {
    val colors = BouncerDesignSystem.colors
    var showConfetti by remember { mutableStateOf(false) }
    var cardCenter by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(Unit) {
        if (achievement.unlocked) {
            showConfetti = true
            delay(2500L)
            showConfetti = false
        }
    }

    Box {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    cardCenter = Offset(coords.size.width / 2f, coords.size.height / 2f)
                },
            importance = if (achievement.unlocked) BouncerImportance.STANDARD else BouncerImportance.MINIMAL
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .then(if (!achievement.unlocked) Modifier.border(1.dp, colors.glassSurface, RoundedCornerShape(12.dp)) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (achievement.unlocked) {
                        Box(
                            modifier = Modifier.size(80.dp).background(
                                Brush.linearGradient(
                                    listOf(
                                        accentColor,
                                        accentColor.copy(alpha = 0.8f),
                                        accentColor.copy(alpha = 0.6f)
                                    )
                                ),
                                CircleShape
                            ).shadow(20.dp, CircleShape, spotColor = accentColor.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(achievement.icon, fontSize = 32.sp)
                        }
                        // Sparkle overlay on unlocked medal icon area
                        SparkleOverlay(modifier = Modifier.matchParentSize())
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Box(modifier = Modifier.size(80.dp).border(1.dp, colors.glassSurface, CircleShape))
                            Icon(Icons.Default.Lock, null, tint = colors.textMuted, modifier = Modifier.size(24.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(4.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(achievement.title, color = if (achievement.unlocked) colors.textPrimary else colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (achievement.unlocked) {
                        Surface(color = accentColor, shape = RoundedCornerShape(4.dp)) {
                            Text("UNLOCKED", color = Color.Black, fontSize = 7.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(achievement.description, color = colors.textTertiary, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // Confetti burst overlay for unlocked achievements
        if (showConfetti && cardCenter != Offset.Zero) {
            ConfettiBurst(
                modifier = Modifier.matchParentSize(),
                burstPoint = cardCenter,
                isActive = true,
                particleCount = 30
            )
        }
    }
}

@Composable
private fun CareerRadarChartCard() {
    val colors = BouncerDesignSystem.colors
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        importance = BouncerImportance.STANDARD
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            RadarChart(
                data = mapOf(
                    "BATTING" to 90f,
                    "BOWLING" to 70f,
                    "FIELDING" to 85f,
                    "STRATEGY" to 60f,
                    "AGGR." to 80f,
                    "CONSISTENCY" to 95f
                ),
                modifier = Modifier.size(220.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = colors.glassSurface)
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("TOP SKILL", color = colors.textTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("Batting Power", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("POTENTIAL", color = colors.textTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("+15% Growth", color = colors.goldPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
