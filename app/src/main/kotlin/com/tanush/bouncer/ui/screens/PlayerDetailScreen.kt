package com.tanush.bouncer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.components.PerformanceRadarChart
import com.tanush.bouncer.ui.components.FormIndicator
import com.tanush.bouncer.ui.components.AchievementBadges
import com.tanush.bouncer.ui.components.PlayerAchievement
import com.tanush.bouncer.ui.components.AchievementTier
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.components.glass.GlassCard
import coil.request.ImageRequest
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.BrandGradients

/**
 * Player data model for the detail screen
 */
data class PlayerDetails(
    val name: String,
    val role: String,
    val imageUrl: String?,
    val country: String? = null,
    val age: Int? = null,
    val battingStyle: String? = null,
    val bowlingStyle: String? = null,
    val isCaptain: Boolean = false,
    val isWicketKeeper: Boolean = false,
    // Batting stats
    val battingStats: BattingStats? = null,
    // Bowling stats
    val bowlingStats: BowlingStats? = null
)

data class BattingStats(
    val matches: Int = 0,
    val innings: Int = 0,
    val runs: Int = 0,
    val highScore: String = "-",
    val average: Double = 0.0,
    val strikeRate: Double = 0.0,
    val hundreds: Int = 0,
    val fifties: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0
)

data class BowlingStats(
    val matches: Int = 0,
    val innings: Int = 0,
    val overs: Double = 0.0,
    val wickets: Int = 0,
    val bestBowling: String = "-",
    val average: Double = 0.0,
    val economy: Double = 0.0,
    val strikeRate: Double = 0.0,
    val fiveWickets: Int = 0,
    val tenWickets: Int = 0
)

/**
 * Premium Player Detail Screen with animated visualizations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailScreen(
    playerName: String,
    playerRole: String,
    playerImageUrl: String?,
    isCaptain: Boolean = false,
    isWicketKeeper: Boolean = false,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    
    // Loading state - in real app, fetch from API
    var isLoading by remember { mutableStateOf(true) }
    var player by remember { mutableStateOf<PlayerDetails?>(null) }
    
    // Simulate loading player data
    LaunchedEffect(playerName) {
        kotlinx.coroutines.delay(800)
        // Generate mock stats for demo - in real app, fetch from API
        player = PlayerDetails(
            name = playerName,
            role = playerRole,
            imageUrl = playerImageUrl,
            country = "India", // Mock
            age = 28,
            battingStyle = "Right-handed",
            bowlingStyle = if (playerRole.contains("bowl", ignoreCase = true)) "Right-arm fast" else null,
            isCaptain = isCaptain,
            isWicketKeeper = isWicketKeeper,
            battingStats = BattingStats(
                matches = 85,
                innings = 82,
                runs = 3245,
                highScore = "142*",
                average = 45.6,
                strikeRate = 128.5,
                hundreds = 8,
                fifties = 22,
                fours = 312,
                sixes = 156
            ),
            bowlingStats = if (playerRole.contains("bowl", ignoreCase = true) || playerRole.contains("all", ignoreCase = true)) {
                BowlingStats(
                    matches = 85,
                    innings = 72,
                    overs = 456.2,
                    wickets = 98,
                    bestBowling = "5/23",
                    average = 28.4,
                    economy = 7.2,
                    strikeRate = 23.5,
                    fiveWickets = 2,
                    tenWickets = 0
                )
            } else null
        )
        isLoading = false
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "playerShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "PLAYER PROFILE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        style = androidx.compose.ui.text.TextStyle(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    TextPrimary,
                                    colors.goldPrimary,
                                    colors.goldDark
                                )
                            )
                        )
                    )
                },
                navigationIcon = {
                    Surface(
                        onClick = onBackClick,
                        shape = RoundedCornerShape(12.dp),
                        color = colors.glassSurface,
                        border = BorderStroke(0.5.dp, colors.goldDark.copy(alpha = 0.3f)),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = colors.goldLight,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        GlassBloomBackground(
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading) {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(dimens.md)
                    ) {
                        CircularProgressIndicator(
                            color = colors.goldPrimary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "Loading player data...",
                            color = colors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else if (player != null) {
                val p = player ?: return@GlassBloomBackground
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(dimens.md + dimens.xs),
                    verticalArrangement = Arrangement.spacedBy(dimens.md + dimens.xs)
                ) {
                    // Player header card
                    item {
                        PlayerHeaderCard(
                            player = p,
                            shimmerOffset = shimmerOffset,
                            pulseScale = pulseScale,
                            context = context
                        )
                    }
                    
                    // Quick info
                    item {
                        PlayerQuickInfo(player = p)
                    }
                    
                    // Batting stats
                    if (p.battingStats != null) {
                        item {
                            SectionTitle(title = "BATTING STATISTICS", icon = Icons.Default.SportsCricket)
                        }
                        item {
                            BattingStatsCard(stats = p.battingStats)
                        }
                    }

                    // Recent form
                    item {
                        SectionTitle(title = "RECENT FORM", icon = Icons.AutoMirrored.Filled.TrendingUp)
                    }
                    item {
                        FormIndicator(
                            recentScores = listOf(45, 12, 78, 0, 102),
                            showLabels = true
                        )
                    }
                    
                    // Bowling stats
                    if (p.bowlingStats != null) {
                        item {
                            SectionTitle(title = "BOWLING STATISTICS", icon = Icons.Default.Album)
                        }
                        item {
                            BowlingStatsCard(stats = p.bowlingStats)
                        }
                    }
                    
                    // Performance radar chart
                    item {
                        SectionTitle(title = "PERFORMANCE OVERVIEW", icon = Icons.Default.Radar)
                    }
                    item {
                        val battingAvg = p.battingStats?.average?.toFloat() ?: 0f
                        val matches = p.battingStats?.matches ?: 0
                        val bowlingAvg = p.bowlingStats?.average?.toFloat()
                        GlassCard(
                            useGoldBorder = true
                        ) {
                            PerformanceRadarChart(
                                battingSkill = (battingAvg / 60f * 100f).coerceIn(0f, 100f),
                                bowlingSkill = if (bowlingAvg != null) ((60f - bowlingAvg) / 60f * 100f).coerceIn(0f, 100f) else 0f,
                                fieldingSkill = 65f,
                                fitnessSkill = 70f,
                                experienceSkill = (matches / 100f * 100f).coerceIn(0f, 100f),
                                playerName = p.name,
                                primaryColor = colors.goldPrimary
                            )
                        }
                    }

                    // Achievements
                    item {
                        SectionTitle(title = "ACHIEVEMENTS", icon = Icons.Default.EmojiEvents)
                    }
                    item {
                        AchievementBadges(
                            achievements = listOf(
                                PlayerAchievement("Century King", "Scored 8 centuries", Icons.Default.Star, true, AchievementTier.GOLD),
                                PlayerAchievement("Run Machine", "3000+ career runs", Icons.AutoMirrored.Filled.TrendingUp, true, AchievementTier.PLATINUM),
                                PlayerAchievement("Boundary Master", "300+ fours", Icons.Default.FlashOn, true, AchievementTier.SILVER),
                                PlayerAchievement("Six Hitter", "150+ sixes", Icons.Default.Whatshot, true, AchievementTier.GOLD),
                                PlayerAchievement("Match Winner", "10 MOM awards", Icons.Default.EmojiEvents, false, AchievementTier.PLATINUM)
                            )
                        )
                    }
                    
                    // Footer
                    item {
                        PlayerFooter()
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, icon: ImageVector) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.sm + dimens.xxs),
        modifier = Modifier.padding(vertical = dimens.sm)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.goldPrimary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            style = androidx.compose.ui.text.TextStyle(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        colors.goldLight,
                        colors.goldPrimary,
                        colors.goldDark
                    )
                )
            )
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            colors.goldDark.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
private fun PlayerHeaderCard(
    player: PlayerDetails,
    shimmerOffset: Float,
    pulseScale: Float,
    context: android.content.Context
) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(dimens.lg + dimens.xs),
                spotColor = colors.goldDark.copy(alpha = 0.25f)
            ),
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(dimens.lg + dimens.xs),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                colors = listOf(
                    colors.goldLight.copy(alpha = 0.4f * shimmerOffset),
                    colors.goldDark.copy(alpha = 0.2f),
                    Color.Transparent
                )
            )
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Gold bloom radial gradient behind avatar
            Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.goldPrimary.copy(alpha = 0.12f),
                            colors.goldDark.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.3f),
                        radius = size.width * 0.6f
                    ),
                    radius = size.width * 0.6f,
                    center = Offset(size.width * 0.5f, size.height * 0.3f)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.lg + dimens.xs),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Player image with gold gradient border
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                        .size(140.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    colors.goldPrimary.copy(alpha = 0.2f),
                                    colors.goldDark.copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .shadow(
                                elevation = 20.dp,
                                shape = CircleShape,
                                spotColor = colors.goldDark.copy(alpha = 0.4f)
                            )
                            .background(colors.glassSurface, CircleShape)
                            .border(
                                2.dp,
                                Brush.linearGradient(
                                    colors = listOf(
                                        colors.goldLight,
                                        colors.goldPrimary,
                                        colors.goldDark
                                    )
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!player.imageUrl.isNullOrEmpty()) {
                            CoilImage(
                                model = ImageRequest.Builder(context)
                                    .data(player.imageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = player.name,
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = colors.goldPrimary,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    
                    // Captain/WK badges
                    if (player.isCaptain || player.isWicketKeeper) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 5.dp, y = 5.dp),
                            shape = CircleShape,
                            color = if (player.isCaptain) colors.goldPrimary else colors.glassSurface,
                            border = BorderStroke(1.5.dp, colors.goldDark.copy(alpha = 0.5f))
                        ) {
                            Text(
                                if (player.isCaptain) "C" else "WK",
                                color = if (player.isCaptain) colors.backgroundBase else colors.textPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(dimens.sm)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(dimens.md + dimens.xs))
                
                // Player name with etched gold gradient text
                Text(
                    text = player.name,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                TextPrimary,
                                colors.goldPrimary,
                                colors.goldDark
                            )
                        )
                    )
                )
                
                Spacer(modifier = Modifier.height(dimens.sm))
                
                // Role badge - pill shape with gold accent
                Surface(
                    color = colors.goldDark.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(0.5.dp, colors.goldDark.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = player.role.uppercase(),
                        modifier = Modifier.padding(horizontal = dimens.md, vertical = 6.dp),
                        color = colors.goldPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
                
                if (player.country != null) {
                    Spacer(modifier = Modifier.height(dimens.sm + dimens.xxs))
                    Text(
                        text = player.country,
                        color = colors.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerQuickInfo(player: PlayerDetails) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(dimens.md + dimens.xs),
        border = BorderStroke(0.5.dp, colors.goldDark.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.md + dimens.xs),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickInfoItem(
                label = "Age",
                value = player.age?.toString() ?: "-",
                icon = Icons.Default.Cake
            )
            QuickInfoItem(
                label = "Batting",
                value = player.battingStyle?.take(6) ?: "-",
                icon = Icons.Default.SportsCricket
            )
            if (player.bowlingStyle != null) {
                QuickInfoItem(
                    label = "Bowling",
                    value = player.bowlingStyle.take(6),
                    icon = Icons.Default.Album
                )
            }
        }
    }
}

@Composable
private fun QuickInfoItem(
    label: String,
    value: String,
    icon: ImageVector
) {
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.goldPrimary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            value,
            color = colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            color = colors.textTertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BattingStatsCard(stats: BattingStats) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(dimens.lg),
                spotColor = colors.goldDark.copy(alpha = 0.15f)
            ),
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(dimens.lg),
        border = BorderStroke(0.5.dp, colors.goldDark.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(dimens.lg)
        ) {
            // Main stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "Matches", value = stats.matches.toString(), color = colors.textPrimary)
                StatItem(label = "Runs", value = stats.runs.toString(), color = colors.goldPrimary, large = true)
                StatItem(label = "HS", value = stats.highScore, color = colors.textPrimary)
            }
            
            Spacer(modifier = Modifier.height(dimens.md))
            HorizontalDivider(color = colors.glassBorder)
            Spacer(modifier = Modifier.height(dimens.md))
            
            // Secondary stats - gold for key figures
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "Average", value = String.format("%.1f", stats.average), color = colors.goldPrimary)
                StatItem(label = "SR", value = String.format("%.1f", stats.strikeRate), color = colors.statusInfo)
                StatItem(label = "100s", value = stats.hundreds.toString(), color = colors.goldLight)
                StatItem(label = "50s", value = stats.fifties.toString(), color = colors.goldDark)
            }
            
            Spacer(modifier = Modifier.height(dimens.md))
            HorizontalDivider(color = colors.glassBorder)
            Spacer(modifier = Modifier.height(dimens.md))
            
            // Boundary stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BoundaryStatItem(label = "4s", value = stats.fours.toString(), color = colors.statusInfo)
                BoundaryStatItem(label = "6s", value = stats.sixes.toString(), color = colors.goldPrimary)
            }
        }
    }
}

@Composable
private fun BowlingStatsCard(stats: BowlingStats) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(dimens.lg),
                spotColor = colors.goldDark.copy(alpha = 0.15f)
            ),
        colors = CardDefaults.cardColors(containerColor = colors.glassSurface),
        shape = RoundedCornerShape(dimens.lg),
        border = BorderStroke(0.5.dp, colors.goldDark.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(dimens.lg)
        ) {
            // Main stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "Matches", value = stats.matches.toString(), color = colors.textPrimary)
                StatItem(label = "Wickets", value = stats.wickets.toString(), color = colors.statusLive, large = true)
                StatItem(label = "Best", value = stats.bestBowling, color = colors.textPrimary)
            }
            
            Spacer(modifier = Modifier.height(dimens.md))
            HorizontalDivider(color = colors.glassBorder)
            Spacer(modifier = Modifier.height(dimens.md))
            
            // Secondary stats - gold for key figures
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "Average", value = String.format("%.1f", stats.average), color = colors.goldPrimary)
                StatItem(label = "Economy", value = String.format("%.1f", stats.economy), color = colors.goldLight)
                StatItem(label = "SR", value = String.format("%.1f", stats.strikeRate), color = colors.statusInfo)
                StatItem(label = "5W", value = stats.fiveWickets.toString(), color = colors.goldDark)
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color,
    large: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            value,
            color = color,
            fontSize = if (large) 28.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (large) (-1).sp else (-0.5).sp
        )
        Text(
            label,
            color = colors.textTertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun BoundaryStatItem(
    label: String,
    value: String,
    color: Color
) {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.sm + dimens.xs)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.2f),
                            color.copy(alpha = 0.05f)
                        )
                    ),
                    CircleShape
                )
                .border(1.dp, color.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = color,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            value,
            color = colors.textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    val colors = BouncerDesignSystem.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            label,
            color = colors.textTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PlayerFooter() {
    val colors = BouncerDesignSystem.colors
    val dimens = BouncerDesignSystem.dimens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            colors.goldDark.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    ),
                    RoundedCornerShape(50)
                )
        )
        
        Spacer(modifier = Modifier.height(dimens.md))
        
        Text(
            text = "Stats powered by Bouncer",
            color = colors.textTertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp
        )
    }
}
