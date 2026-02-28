package com.tanush.bouncer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.models.Achievement
import com.tanush.bouncer.models.AchievementKind
import com.tanush.bouncer.ui.theme.*

// Achievement category for grouping
enum class AchievementCategory(val title: String, val color: @Composable () -> Color) {
    BATTING("BATTING", { Color(0xFF4FC3F7) }),
    BOWLING("BOWLING", { Color(0xFF81C784) }),
    TEAM("TEAM", { Color(0xFFFFB74D) }),
    SPECIAL("SPECIAL", { GoldDark })
}

private fun Achievement.category(): AchievementCategory {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    unlockedAchievements: Set<AchievementKind>,
    onBackClick: () -> Unit
) {
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
        containerColor = BlackBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "THE TROPHY ROOM",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.IosShare, "Share", tint = GoldDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 100.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Stats with enhanced progress
                item(span = { GridItemSpan(2) }) {
                    EliteTrophyProgressCard(achievements.count { it.unlocked }, achievements.size)
                }
                
                // Category Progress Summary (iOS Parity)
                item(span = { GridItemSpan(2) }) {
                    CategoryProgressSummary(categoryStats)
                }

                // Category sections with achievements
                AchievementCategory.entries.forEach { category ->
                    val categoryAchievements = groupedAchievements[category] ?: emptyList()
                    if (categoryAchievements.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            CategoryHeader(
                                category = category,
                                unlockedCount = categoryAchievements.count { it.unlocked },
                                totalCount = categoryAchievements.size
                            )
                        }
                        
                        items(categoryAchievements) { achievement ->
                            EliteMedalCard(achievement, category.color())
                        }
                    }
                }
                
                // Career Radar Section
                item(span = { GridItemSpan(2) }) {
                    Column(modifier = Modifier.padding(vertical = 24.dp)) {
                        Text(
                            "CAREER RADAR", 
                            color = TextSecondary, 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Bold, 
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        CareerRadarChartCard()
                    }
                }
            }
            
            // Insights FAB
            FloatingActionButton(
                onClick = {},
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                containerColor = GoldDark,
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, GoldHairline)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("TOTAL PROGRESS", color = GoldDark, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(unlocked.toString(), color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
                Text("/ $total", color = TextTertiary, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
            }
            
            // Elite Status based on progress
            val eliteStatus = when {
                unlocked == total -> "🏆 Legend"
                unlocked >= total * 0.75 -> "⭐️ Champion"
                unlocked >= total * 0.5 -> "🔥 Rising Star"
                unlocked >= total * 0.25 -> "💪 Contender"
                else -> "🌱 Rookie"
            }
            Text("Elite Status: $eliteStatus", color = TextSecondary, fontSize = 13.sp)
            
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
                Box(modifier = Modifier.fillMaxWidth(unlocked.toFloat() / total).fillMaxHeight().background(GoldDark).shadow(8.dp, spotColor = GoldDark))
            }
        }
    }
}

@Composable
private fun CategoryProgressSummary(categoryStats: List<Triple<AchievementCategory, Int, Int>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f))
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
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = category.title,
                        color = TextMuted,
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
private fun EliteMedalCard(achievement: Achievement, accentColor: Color = GoldDark) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, if (achievement.unlocked) accentColor.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .then(if (!achievement.unlocked) Modifier.border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp)) else Modifier),
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
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(80.dp).border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape))
                        Icon(Icons.Default.Lock, null, tint = TextMuted, modifier = Modifier.size(24.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(4.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(achievement.title, color = if (achievement.unlocked) Color.White else TextTertiary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (achievement.unlocked) {
                    Surface(color = accentColor, shape = RoundedCornerShape(4.dp)) {
                        Text("UNLOCKED", color = Color.Black, fontSize = 7.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
            }
            Text(achievement.description, color = TextTertiary, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun CareerRadarChartCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, GoldHairline)
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                CareerRadarChart(
                    metrics = listOf(
                        "BATTING" to 0.90f,
                        "BOWLING" to 0.70f,
                        "FIELDING" to 0.85f,
                        "STRATEGY" to 0.60f,
                        "AGGR." to 0.80f,
                        "CONSISTENCY" to 0.95f
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("TOP SKILL", color = TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("Batting Power", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("POTENTIAL", color = TextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("+15% Growth", color = ShinyGoldDeep, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
