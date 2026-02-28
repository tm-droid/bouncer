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
import coil.request.ImageRequest
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
                        color = GoldDark
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BlackBackground)
        ) {
            // Premium animated background
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Top glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GoldDark.copy(alpha = 0.1f),
                            GoldDark.copy(alpha = 0.02f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, 0f),
                        radius = size.width * 0.9f
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.5f, 0f)
                )
                // Bottom accent
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ChartBlue.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.8f, size.height),
                        radius = size.width * 0.5f
                    ),
                    radius = size.width * 0.5f,
                    center = Offset(size.width * 0.8f, size.height)
                )
                // Purple accent
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ChartPurple.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        center = Offset(0f, size.height * 0.6f),
                        radius = size.width * 0.4f
                    ),
                    radius = size.width * 0.4f,
                    center = Offset(0f, size.height * 0.6f)
                )
            }

            if (isLoading) {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = GoldDark,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "Loading player data...",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else if (player != null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Player header card
                    item {
                        PlayerHeaderCard(
                            player = player!!,
                            shimmerOffset = shimmerOffset,
                            pulseScale = pulseScale,
                            context = context
                        )
                    }
                    
                    // Quick info
                    item {
                        PlayerQuickInfo(player = player!!)
                    }
                    
                    // Batting stats
                    if (player!!.battingStats != null) {
                        item {
                            SectionTitle(title = "BATTING STATISTICS", icon = Icons.Default.SportsCricket)
                        }
                        item {
                            BattingStatsCard(stats = player!!.battingStats!!)
                        }
                    }
                    
                    // Bowling stats
                    if (player!!.bowlingStats != null) {
                        item {
                            SectionTitle(title = "BOWLING STATISTICS", icon = Icons.Default.Album)
                        }
                        item {
                            BowlingStatsCard(stats = player!!.bowlingStats!!)
                        }
                    }
                    
                    // Performance radar chart
                    item {
                        SectionTitle(title = "PERFORMANCE OVERVIEW", icon = Icons.Default.Radar)
                    }
                    item {
                        PerformanceRadarCard(
                            battingStats = player!!.battingStats,
                            bowlingStats = player!!.bowlingStats
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = ShinyGoldDeep,
            modifier = Modifier.size(18.dp)
        )
        Text(
            title,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            CardBorder.copy(alpha = 0.3f),
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = ShinyGoldDeep.copy(alpha = 0.25f)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                colors = listOf(
                    ShinyGoldLight.copy(alpha = 0.4f * shimmerOffset),
                    ShinyGoldDeep.copy(alpha = 0.2f),
                    Color.Transparent
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Player image with glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                    .size(140.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                ShinyGoldDeep.copy(alpha = 0.2f),
                                ShinyGoldDeep.copy(alpha = 0.05f),
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
                            spotColor = ShinyGoldDeep.copy(alpha = 0.4f)
                        )
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .border(
                            2.dp,
                            Brush.linearGradient(
                                colors = listOf(
                                    ShinyGoldLight.copy(alpha = 0.6f),
                                    ShinyGoldDeep,
                                    ShinyGoldMid
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
                            tint = ShinyGoldDeep,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                // Captain/WK badges
                if (player.isCaptain || player.isWicketKeeper) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 5.dp, y = 5.dp)
                            .background(
                                if (player.isCaptain) ShinyGoldDeep else ChartPurple,
                                CircleShape
                            )
                            .border(2.dp, Color.White.copy(alpha = 0.05f), CircleShape)
                            .padding(8.dp)
                    ) {
                        Text(
                            if (player.isCaptain) "C" else "WK",
                            color = if (player.isCaptain) BlackBackground else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Player name
            Text(
                text = player.name,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.5).sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Role badge
            Surface(
                color = ShinyGoldDeep.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, ShinyGoldDeep.copy(alpha = 0.3f))
            ) {
                Text(
                    text = player.role.uppercase(),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    color = ShinyGoldDeep,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }
            
            if (player.country != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = player.country,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PlayerQuickInfo(player: PlayerDetails) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.5.dp, CardBorder.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = ShinyGoldDeep,
            modifier = Modifier.size(20.dp)
        )
        Text(
            value,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            color = TextTertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BattingStatsCard(stats: BattingStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = BoundarySix.copy(alpha = 0.15f)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(0.5.dp, BoundarySix.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // Main stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "Matches", value = stats.matches.toString(), color = TextPrimary)
                StatItem(label = "Runs", value = stats.runs.toString(), color = BoundarySix, large = true)
                StatItem(label = "HS", value = stats.highScore, color = TextPrimary)
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = CardBorder.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(20.dp))
            
            // Secondary stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "Average", value = String.format("%.1f", stats.average), color = SuccessGreen)
                StatItem(label = "SR", value = String.format("%.1f", stats.strikeRate), color = ChartBlue)
                StatItem(label = "100s", value = stats.hundreds.toString(), color = ChartPurple)
                StatItem(label = "50s", value = stats.fifties.toString(), color = ShinyGoldDeep)
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = CardBorder.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(20.dp))
            
            // Boundary stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BoundaryStatItem(label = "4s", value = stats.fours.toString(), color = BoundaryFour)
                BoundaryStatItem(label = "6s", value = stats.sixes.toString(), color = BoundarySix)
            }
        }
    }
}

@Composable
private fun BowlingStatsCard(stats: BowlingStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = WicketBlue.copy(alpha = 0.15f)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(0.5.dp, WicketBlue.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // Main stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "Matches", value = stats.matches.toString(), color = TextPrimary)
                StatItem(label = "Wickets", value = stats.wickets.toString(), color = WicketRed, large = true)
                StatItem(label = "Best", value = stats.bestBowling, color = TextPrimary)
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = CardBorder.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(20.dp))
            
            // Secondary stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "Average", value = String.format("%.1f", stats.average), color = SuccessGreen)
                StatItem(label = "Economy", value = String.format("%.1f", stats.economy), color = ChartBlue)
                StatItem(label = "SR", value = String.format("%.1f", stats.strikeRate), color = ChartPurple)
                StatItem(label = "5W", value = stats.fiveWickets.toString(), color = ShinyGoldDeep)
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
            color = TextTertiary,
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PerformanceRadarCard(
    battingStats: BattingStats?,
    bowlingStats: BowlingStats?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = ChartPurple.copy(alpha = 0.15f)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(0.5.dp, CardBorder.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Simple radar-style visualization
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .drawBehind {
                        val center = Offset(size.width / 2, size.height / 2)
                        val maxRadius = size.width / 2 - 20.dp.toPx()
                        
                        // Draw concentric circles
                        for (i in 1..4) {
                            drawCircle(
                                color = CardBorder.copy(alpha = 0.2f),
                                radius = maxRadius * i / 4,
                                center = center,
                                style = Stroke(1.dp.toPx())
                            )
                        }
                        
                        // Draw axes
                        val categories = 6
                        for (i in 0 until categories) {
                            val angle = (i * 2 * PI / categories) - PI / 2
                            val endX = center.x + (maxRadius * cos(angle)).toFloat()
                            val endY = center.y + (maxRadius * sin(angle)).toFloat()
                            drawLine(
                                color = CardBorder.copy(alpha = 0.3f),
                                start = center,
                                end = Offset(endX, endY),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        
                        // Draw performance polygon
                        val values = listOf(
                            (battingStats?.average ?: 0.0) / 60.0,
                            (battingStats?.strikeRate ?: 0.0) / 200.0,
                            (battingStats?.hundreds?.toDouble() ?: 0.0) / 20.0,
                            (bowlingStats?.wickets?.toDouble() ?: 0.0) / 150.0,
                            (60.0 - (bowlingStats?.average ?: 60.0)) / 60.0,
                            (10.0 - (bowlingStats?.economy ?: 10.0)) / 10.0
                        ).map { it.coerceIn(0.0, 1.0) }
                        
                        val path = Path()
                        values.forEachIndexed { i, value ->
                            val angle = (i * 2 * PI / categories) - PI / 2
                            val x = center.x + (maxRadius * value * cos(angle)).toFloat()
                            val y = center.y + (maxRadius * value * sin(angle)).toFloat()
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.close()
                        
                        drawPath(
                            path = path,
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ShinyGoldDeep.copy(alpha = 0.3f),
                                    ShinyGoldDeep.copy(alpha = 0.1f)
                                ),
                                center = center
                            )
                        )
                        drawPath(
                            path = path,
                            color = ShinyGoldDeep,
                            style = Stroke(2.dp.toPx())
                        )
                        
                        // Draw points
                        values.forEachIndexed { i, value ->
                            val angle = (i * 2 * PI / categories) - PI / 2
                            val x = center.x + (maxRadius * value * cos(angle)).toFloat()
                            val y = center.y + (maxRadius * value * sin(angle)).toFloat()
                            drawCircle(
                                color = ShinyGoldDeep,
                                radius = 6.dp.toPx(),
                                center = Offset(x, y)
                            )
                            drawCircle(
                                color = CardSurface,
                                radius = 3.dp.toPx(),
                                center = Offset(x, y)
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Center label
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "SKILL",
                        color = TextTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "INDEX",
                        color = TextTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LegendItem(label = "Average", color = ChartBlue)
                LegendItem(label = "Strike Rate", color = SuccessGreen)
                LegendItem(label = "Impact", color = ChartPurple)
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
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
            color = TextTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PlayerFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
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
                            ShinyGoldDeep.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    ),
                    RoundedCornerShape(50)
                )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Stats powered by Bouncer",
            color = TextTertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp
        )
    }
}
