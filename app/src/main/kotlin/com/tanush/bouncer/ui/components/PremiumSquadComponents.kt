package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Enhanced player card with premium styling
 */
@Composable
fun PremiumPlayerCard(
    playerName: String,
    playerRole: PlayerRole,
    playerImage: String?,
    teamColor: Color = ShinyGoldDeep,
    isCaptain: Boolean = false,
    isWicketkeeper: Boolean = false,
    isPlaying: Boolean = true,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isHovered by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "playerCard")
    val borderGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "playerBorderGlow"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "cardScale"
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isHovered) 16.dp else 8.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = teamColor.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = if (isCaptain) BorderStroke(
            2.dp, 
            Brush.linearGradient(
                colors = listOf(
                    ShinyGoldDeep.copy(alpha = borderGlow),
                    ShinyGoldBronze,
                    ShinyGoldDeep.copy(alpha = borderGlow)
                )
            )
        ) else null
    ) {
        Column(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = teamColor.copy(alpha = 0.2f)),
                    onClick = {
                        isHovered = !isHovered
                        onClick()
                    }
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Player image with badges
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                // Glow effect
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                teamColor.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        ),
                        radius = size.maxDimension * 0.6f
                    )
                }
                
                // Player image
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .border(2.dp, teamColor.copy(alpha = 0.5f), CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    CardDarkBlue,
                                    teamColor.copy(alpha = 0.1f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!playerImage.isNullOrEmpty()) {
                        CoilImage(
                            model = playerImage,
                            contentDescription = playerName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = teamColor.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // Captain badge
                if (isCaptain) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.TopEnd)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(ShinyGoldDeep, ShinyGoldBronze)
                                ),
                                CircleShape
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "C",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
                
                // Wicketkeeper badge
                if (isWicketkeeper) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.BottomEnd)
                            .background(CardDarkBlue, CircleShape)
                            .border(1.dp, teamColor.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "WK",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = teamColor
                        )
                    }
                }
                
                // Not playing indicator
                if (!isPlaying) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Not Playing",
                            tint = WicketRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            // Player name
            Text(
                text = playerName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            // Role badge
            RoleBadge(role = playerRole, color = teamColor)
        }
    }
}

enum class PlayerRole {
    BATSMAN, BOWLER, ALL_ROUNDER, WICKET_KEEPER_BATSMAN
}

@Composable
private fun RoleBadge(role: PlayerRole, color: Color) {
    val (icon, label) = when (role) {
        PlayerRole.BATSMAN -> Icons.Default.SportsCricket to "BAT"
        PlayerRole.BOWLER -> Icons.Default.FiberManualRecord to "BOWL"
        PlayerRole.ALL_ROUNDER -> Icons.Default.SwapHoriz to "ALL"
        PlayerRole.WICKET_KEEPER_BATSMAN -> Icons.Default.Shield to "WK-BAT"
    }
    
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = color
        )
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = color
        )
    }
}

/**
 * Squad grid layout with roles
 */
@Composable
fun PremiumSquadGrid(
    players: List<SquadPlayer>,
    teamColor: Color = ShinyGoldDeep,
    onPlayerClick: (SquadPlayer) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val groupedPlayers = players.groupBy { it.role }
    
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Order: Batsmen, All-rounders, Wicket-keepers, Bowlers
        val roleOrder = listOf(
            PlayerRole.BATSMAN,
            PlayerRole.ALL_ROUNDER,
            PlayerRole.WICKET_KEEPER_BATSMAN,
            PlayerRole.BOWLER
        )
        
        roleOrder.forEach { role ->
            groupedPlayers[role]?.let { playersInRole ->
                item {
                    RoleSectionHeader(
                        role = role,
                        count = playersInRole.size,
                        color = teamColor
                    )
                }
                
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(playersInRole) { player ->
                            PremiumPlayerCard(
                                playerName = player.name,
                                playerRole = player.role,
                                playerImage = player.image,
                                teamColor = teamColor,
                                isCaptain = player.isCaptain,
                                isWicketkeeper = player.isWicketkeeper,
                                isPlaying = player.isPlaying,
                                onClick = { onPlayerClick(player) },
                                modifier = Modifier.width(120.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

data class SquadPlayer(
    val id: String,
    val name: String,
    val role: PlayerRole,
    val image: String? = null,
    val isCaptain: Boolean = false,
    val isWicketkeeper: Boolean = false,
    val isPlaying: Boolean = true
)

@Composable
private fun RoleSectionHeader(
    role: PlayerRole,
    count: Int,
    color: Color
) {
    val roleLabel = when (role) {
        PlayerRole.BATSMAN -> "Batsmen"
        PlayerRole.BOWLER -> "Bowlers"
        PlayerRole.ALL_ROUNDER -> "All-rounders"
        PlayerRole.WICKET_KEEPER_BATSMAN -> "Wicket-keepers"
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
            Text(
                text = roleLabel.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = Color.White
            )
        }
        
        Text(
            text = "$count players",
            fontSize = 11.sp,
            color = TextMuted
        )
    }
}

/**
 * Batsman performance card
 */
@Composable
fun BatsmanPerformanceCard(
    name: String,
    runs: Int,
    balls: Int,
    fours: Int,
    sixes: Int,
    strikeRate: Float,
    dismissalInfo: String?,
    isOnStrike: Boolean = false,
    isRetired: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "batsman")
    val strikePulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "strikePulse"
    )

    Surface(
        modifier = modifier
            .then(
                if (isOnStrike) {
                    Modifier.drawBehind {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    LiveGreen.copy(alpha = strikePulse * 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                    }
                } else Modifier
            )
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = if (isOnStrike) LiveGreen.copy(alpha = 0.2f) else Color.Transparent
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = if (isOnStrike) BorderStroke(
            1.dp,
            LiveGreen.copy(alpha = strikePulse * 0.6f)
        ) else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Name and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    if (isOnStrike) {
                        OnStrikeIndicator(pulseAlpha = strikePulse)
                    }
                }
                
                // Status badges
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (runs >= 100) {
                        MilestoneBadge("100", ShinyGoldDeep)
                    } else if (runs >= 50) {
                        MilestoneBadge("50", ShinyGoldMid)
                    }
                    
                    if (isRetired) {
                        Text(
                            text = "RETIRED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            modifier = Modifier
                                .background(CardBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            // Main stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Runs
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$runs",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = ShinyGoldDeep
                    )
                    Text(
                        text = "($balls)",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
                
                // Strike rate
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = String.format("%.2f", strikeRate),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            strikeRate >= 150 -> BoundarySix
                            strikeRate >= 100 -> BoundaryFour
                            strikeRate >= 80 -> TextSecondary
                            else -> DotBall
                        }
                    )
                    Text(
                        text = "SR",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
                
                // Boundaries
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BoundaryCounter(count = fours, label = "4s", color = BoundaryFour)
                    BoundaryCounter(count = sixes, label = "6s", color = BoundarySix)
                }
            }
            
            // Dismissal info
            dismissalInfo?.let {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    color = WicketRed.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun OnStrikeIndicator(pulseAlpha: Float) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .drawBehind {
                drawCircle(
                    color = LiveGreen.copy(alpha = pulseAlpha * 0.3f),
                    radius = size.maxDimension * 1.5f
                )
            }
            .background(LiveGreen, CircleShape)
    )
}

@Composable
private fun MilestoneBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(
                Brush.linearGradient(
                    colors = listOf(color, color.copy(alpha = 0.7f))
                ),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
    }
}

@Composable
private fun BoundaryCounter(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$count",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Text(
            text = label,
            fontSize = 9.sp,
            color = TextMuted
        )
    }
}

/**
 * Bowler performance card
 */
@Composable
fun BowlerPerformanceCard(
    name: String,
    overs: String,
    maidens: Int,
    runs: Int,
    wickets: Int,
    economy: Float,
    isBowling: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bowler")
    val bowlingPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bowlingPulse"
    )

    Surface(
        modifier = modifier
            .then(
                if (isBowling) {
                    Modifier.drawBehind {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    LiveBlue.copy(alpha = bowlingPulse * 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                    }
                } else Modifier
            )
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = if (isBowling) LiveBlue.copy(alpha = 0.2f) else Color.Transparent
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = if (isBowling) BorderStroke(
            1.dp,
            LiveBlue.copy(alpha = bowlingPulse * 0.6f)
        ) else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Name and bowling status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    if (isBowling) {
                        Box(
                            modifier = Modifier
                                .background(
                                    LiveBlue.copy(alpha = 0.15f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "BOWLING",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = LiveBlue,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
                
                // Wickets badges
                if (wickets >= 5) {
                    MilestoneBadge("5W", BoundarySix)
                } else if (wickets >= 3) {
                    MilestoneBadge("${wickets}W", BoundaryFour)
                }
            }
            
            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Wickets
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$wickets/$runs",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = if (wickets >= 3) BoundarySix else ShinyGoldDeep
                    )
                    Text(
                        text = "($overs ov)",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
                
                // Economy
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format("%.2f", economy),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            economy <= 4.0 -> SuccessGreen
                            economy <= 6.0 -> TextSecondary
                            economy <= 8.0 -> WarningYellow
                            else -> WicketRed
                        }
                    )
                    Text(
                        text = "ECON",
                        fontSize = 9.sp,
                        color = TextMuted,
                        letterSpacing = 0.5.sp
                    )
                }
                
                // Maidens
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$maidens",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Text(
                        text = "MAIDENS",
                        fontSize = 9.sp,
                        color = TextMuted,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

/**
 * Partnership details card
 */
@Composable
fun PartnershipCard(
    batsman1Name: String,
    batsman1Runs: Int,
    batsman1Balls: Int,
    batsman2Name: String,
    batsman2Runs: Int,
    batsman2Balls: Int,
    partnershipRuns: Int,
    partnershipBalls: Int,
    partnershipOvers: String? = null,
    modifier: Modifier = Modifier
) {
    val totalRuns = batsman1Runs + batsman2Runs
    val batsman1Percentage = if (partnershipRuns > 0) (batsman1Runs.toFloat() / partnershipRuns) else 0.5f
    
    val animatedPercentage by animateFloatAsState(
        targetValue = batsman1Percentage,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "partnershipPercentage"
    )

    Surface(
        modifier = modifier.shadow(
            elevation = 12.dp,
            shape = RoundedCornerShape(20.dp),
            spotColor = ShinyGoldDeep.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PARTNERSHIP",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = TextMuted
                )
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$partnershipRuns runs",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = ShinyGoldDeep
                    )
                    Text(
                        text = partnershipOvers ?: "$partnershipBalls balls",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
            
            // Progress bar showing contribution
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CardBorder)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedPercentage)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(ShinyGoldDeep, ShinyGoldMid)
                                )
                            )
                    )
                }
            }
            
            // Both batsmen
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Batsman 1
                Column {
                    Text(
                        text = batsman1Name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "$batsman1Runs",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = ShinyGoldDeep
                        )
                        Text(
                            text = "($batsman1Balls)",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
                
                // Batsman 2
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = batsman2Name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "$batsman2Runs",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = ShinyGoldMid
                        )
                        Text(
                            text = "($batsman2Balls)",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}
