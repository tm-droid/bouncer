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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*

/**
 * Premium player card with stats
 */
@Composable
fun PremiumPlayerCard(
    name: String,
    role: String,
    imageUrl: String? = null,
    primaryStat: String = "",
    primaryStatLabel: String = "",
    secondaryStat: String = "",
    secondaryStatLabel: String = "",
    isOnStrike: Boolean = false,
    isCaptain: Boolean = false,
    isKeeper: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "playerCard")
    
    val strikeGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "strikeGlow"
    )
    
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border"
    )

    PremiumGlassCard(
        modifier = modifier,
        onClick = onClick,
        accentColor = if (isOnStrike) BoundarySix else ShinyGoldDeep
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Player Avatar
            Box {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .then(
                            if (isOnStrike) {
                                Modifier.shadow(
                                    elevation = 8.dp,
                                    shape = CircleShape,
                                    spotColor = BoundarySix.copy(alpha = strikeGlow)
                                )
                            } else Modifier
                        )
                        .border(
                            2.dp,
                            if (isOnStrike) {
                                Brush.linearGradient(
                                    colors = listOf(
                                        BoundarySix.copy(alpha = strikeGlow),
                                        ShinyGoldDeep.copy(alpha = strikeGlow * 0.5f)
                                    )
                                )
                            } else {
                                Brush.linearGradient(
                                    colors = listOf(
                                        ShinyGoldLight.copy(alpha = borderAlpha),
                                        ShinyGoldDeep.copy(alpha = borderAlpha * 0.5f)
                                    )
                                )
                            },
                            CircleShape
                        )
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    if (imageUrl != null) {
                        CoilImage(
                            model = imageUrl,
                            contentDescription = name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name.split(" ").map { it.first() }.take(2).joinToString(""),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = ShinyGoldDeep
                            )
                        }
                    }
                }
                
                // Strike indicator
                if (isOnStrike) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .graphicsLayer { alpha = strikeGlow }
                            .background(BoundarySix, CircleShape)
                            .border(1.5.dp, Color.White.copy(alpha = 0.05f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "*",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = BlackBackground
                        )
                    }
                }
            }
            
            // Player Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Captain badge
                    if (isCaptain) {
                        Box(
                            modifier = Modifier
                                .background(ShinyGoldDeep, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "C",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlackBackground
                            )
                        }
                    }
                    
                    // Keeper badge
                    if (isKeeper) {
                        Box(
                            modifier = Modifier
                                .background(WicketBlue, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "WK",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                
                Text(
                    text = role,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
            }
            
            // Stats
            if (primaryStat.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = primaryStat,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isOnStrike) BoundarySix else TextPrimary
                    )
                    if (primaryStatLabel.isNotEmpty()) {
                        Text(
                            text = primaryStatLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextTertiary
                        )
                    }
                }
            }
            
            if (secondaryStat.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = secondaryStat,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    if (secondaryStatLabel.isNotEmpty()) {
                        Text(
                            text = secondaryStatLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextTertiary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Mini scoreboard display
 */
@Composable
fun MiniScoreboard(
    teamName: String,
    teamLogo: String? = null,
    score: String,
    overs: String,
    runRate: String = "",
    isChasing: Boolean = false,
    target: Int? = null,
    requiredRate: String = "",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scoreboard")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    PremiumGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Team info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (teamLogo != null) {
                    CoilImage(
                        model = teamLogo,
                        contentDescription = teamName,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                    )
                }
                
                Text(
                    text = teamName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                
                if (isChasing && target != null) {
                    Box(
                        modifier = Modifier
                            .background(
                                WicketBlue.copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Target: $target",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WicketBlue
                        )
                    }
                }
            }
            
            // Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Main score
                Text(
                    text = score,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = ShinyGoldLight,
                    style = LocalTextStyle.current.copy(
                        shadow = Shadow(
                            color = ShinyGoldDeep.copy(alpha = 0.3f),
                            offset = Offset(0f, 2f),
                            blurRadius = 8f
                        )
                    )
                )
                
                Column(horizontalAlignment = Alignment.End) {
                    // Overs
                    Text(
                        text = "Overs: $overs",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    
                    // Run rate
                    if (runRate.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "CRR: $runRate",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextTertiary
                            )
                            
                            if (isChasing && requiredRate.isNotEmpty()) {
                                Text(
                                    text = "RRR: $requiredRate",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (requiredRate.toFloatOrNull() ?: 0f > 12f) 
                                        ErrorRed else ChartGreen
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Last wicket card
 */
@Composable
fun LastWicketCard(
    playerName: String,
    score: String,
    balls: String,
    howOut: String,
    bowlerName: String,
    modifier: Modifier = Modifier
) {
    PremiumGlassCard(
        modifier = modifier,
        accentColor = WicketRed
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Wicket icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(WicketRed.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SportsCricket,
                    contentDescription = null,
                    tint = WicketRed,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            // Player info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LAST WICKET",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = WicketRed,
                    letterSpacing = 1.sp
                )
                
                Text(
                    text = playerName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                Text(
                    text = howOut,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Score
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = score,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Text(
                    text = "$balls balls",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextTertiary
                )
            }
        }
    }
}

/**
 * Partnership card
 */
@Composable
fun PartnershipCard(
    runs: Int,
    balls: Int,
    player1Name: String,
    player1Runs: Int,
    player1Balls: Int,
    player2Name: String,
    player2Runs: Int,
    player2Balls: Int,
    modifier: Modifier = Modifier
) {
    val player1Contribution = if (runs > 0) player1Runs.toFloat() / runs else 0.5f
    
    val animatedContribution by animateFloatAsState(
        targetValue = player1Contribution,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "contribution"
    )

    PremiumGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PARTNERSHIP",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$runs",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = ShinyGoldDeep
                    )
                    Text(
                        text = "($balls)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
            }
            
            // Contribution bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier
                        .weight(animatedContribution.coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(ChartGreen)
                )
                Box(
                    modifier = Modifier
                        .weight((1f - animatedContribution).coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(WicketBlue)
                )
            }
            
            // Player contributions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = player1Name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "$player1Runs",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ChartGreen
                        )
                        Text(
                            text = "($player1Balls)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextTertiary
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = player2Name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "$player2Runs",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = WicketBlue
                        )
                        Text(
                            text = "($player2Balls)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextTertiary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Current over display
 */
@Composable
fun CurrentOverDisplay(
    balls: List<String>,
    overNumber: Int,
    bowlerName: String = "",
    modifier: Modifier = Modifier
) {
    PremiumGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "OVER $overNumber",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
                
                if (bowlerName.isNotEmpty()) {
                    Text(
                        text = bowlerName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextTertiary
                    )
                }
            }
            
            // Ball-by-ball
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                balls.forEach { ball ->
                    BallDisplay(ball = ball)
                }
                
                // Empty slots for remaining balls
                repeat((6 - balls.size).coerceAtLeast(0)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                CardBorder.copy(alpha = 0.2f),
                                CircleShape
                            )
                            .border(
                                1.dp,
                                CardBorder.copy(alpha = 0.3f),
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun BallDisplay(ball: String) {
    val backgroundColor = when {
        ball == "W" -> WicketRed
        ball == "6" -> BoundarySix
        ball == "4" -> BoundaryFour
        ball.contains("WD") || ball.contains("NB") -> WarningOrange
        ball == "0" -> DotBall
        else -> Color.White.copy(alpha = 0.08f)
    }
    
    val textColor = when {
        ball == "W" || ball == "6" || ball == "4" -> BlackBackground
        ball.contains("WD") || ball.contains("NB") -> BlackBackground
        else -> TextPrimary
    }
    
    Box(
        modifier = Modifier
            .size(36.dp)
            .shadow(
                elevation = if (ball in listOf("W", "6", "4")) 8.dp else 0.dp,
                shape = CircleShape,
                spotColor = backgroundColor.copy(alpha = 0.5f)
            )
            .background(backgroundColor, CircleShape)
            .border(
                1.dp,
                if (ball in listOf("W", "6", "4")) 
                    Color.White.copy(alpha = 0.3f) 
                else CardBorder.copy(alpha = 0.3f),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = ball,
            fontSize = if (ball.length > 1) 10.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

/**
 * Run rate graph mini display
 */
@Composable
fun RunRateMini(
    currentRate: Float,
    requiredRate: Float? = null,
    modifier: Modifier = Modifier
) {
    val maxRate = maxOf(currentRate, requiredRate ?: 0f, 10f)
    
    val animatedCurrent by animateFloatAsState(
        targetValue = currentRate / maxRate,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "currentRate"
    )
    
    val animatedRequired by animateFloatAsState(
        targetValue = (requiredRate ?: 0f) / maxRate,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "requiredRate"
    )

    PremiumGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "RUN RATE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            
            // Current rate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CRR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextTertiary,
                    modifier = Modifier.width(30.dp)
                )
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CardBorder.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedCurrent)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ChartGreen)
                    )
                }
                
                Text(
                    text = String.format("%.2f", currentRate),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ChartGreen,
                    modifier = Modifier.width(40.dp)
                )
            }
            
            // Required rate
            if (requiredRate != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RRR",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextTertiary,
                        modifier = Modifier.width(30.dp)
                    )
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(CardBorder.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedRequired)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (requiredRate > 12f) ErrorRed
                                    else if (requiredRate > 8f) WarningOrange
                                    else WicketBlue
                                )
                        )
                    }
                    
                    Text(
                        text = String.format("%.2f", requiredRate),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (requiredRate > 12f) ErrorRed
                            else if (requiredRate > 8f) WarningOrange
                            else WicketBlue,
                        modifier = Modifier.width(40.dp)
                    )
                }
            }
        }
    }
}
