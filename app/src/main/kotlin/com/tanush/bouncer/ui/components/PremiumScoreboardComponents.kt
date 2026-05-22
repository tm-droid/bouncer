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

@Composable
private fun RoleBadge(text: String, background: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun StatColumn(
    value: String,
    label: String,
    valueSize: Int,
    valueColor: Color,
    labelSize: Int,
    valueWeight: FontWeight = FontWeight.Black,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
    modifier: Modifier = Modifier
) {
    Column(horizontalAlignment = horizontalAlignment, modifier = modifier) {
        Text(
            text = value,
            fontSize = valueSize.sp,
            fontWeight = valueWeight,
            color = valueColor
        )
        if (label.isNotEmpty()) {
            Text(
                text = label,
                fontSize = labelSize.sp,
                fontWeight = FontWeight.Medium,
                color = BouncerDesignSystem.colors.textTertiary
            )
        }
    }
}

@Composable
private fun PlayerContribution(
    name: String,
    runs: Int,
    balls: Int,
    runsColor: Color,
    horizontalAlignment: Alignment.Horizontal
) {
    val colors = BouncerDesignSystem.colors
    Column(horizontalAlignment = horizontalAlignment) {
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "$runs",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = runsColor
            )
            Text(
                text = "($balls)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textTertiary
            )
        }
    }
}

@Composable
private fun RateRow(label: String, fraction: Float, value: Float, barColor: Color, textColor: Color) {
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textTertiary,
            modifier = Modifier.width(30.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.glassBorder.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
        }
        Text(
            text = String.format("%.2f", value),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.width(40.dp)
        )
    }
}

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

    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy

    PremiumGlassCard(
        modifier = modifier,
        onClick = onClick,
        accentColor = if (isOnStrike) hierarchy.secondary.primary else hierarchy.secondary.primary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .then(
                            if (isOnStrike) {
                                Modifier.shadow(
                                    elevation = 8.dp,
                                    shape = CircleShape,
                                    spotColor = hierarchy.secondary.primary.copy(alpha = strikeGlow)
                                )
                            } else Modifier
                        )
                        .border(
                            2.dp,
                            if (isOnStrike) {
                                Brush.linearGradient(
                                    colors = listOf(
                                        hierarchy.secondary.primary.copy(alpha = strikeGlow),
                                        hierarchy.secondary.primary.copy(alpha = strikeGlow * 0.5f)
                                    )
                                )
                            } else {
                                Brush.linearGradient(
                                    colors = listOf(
                                        hierarchy.secondary.secondary.copy(alpha = borderAlpha),
                                        hierarchy.secondary.primary.copy(alpha = borderAlpha * 0.5f)
                                    )
                                )
                            },
                            CircleShape
                        )
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(colors.glassBorderHighlight)
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
                                color = hierarchy.secondary.primary
                            )
                        }
                    }
                }

                if (isOnStrike) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .graphicsLayer { alpha = strikeGlow }
                            .background(hierarchy.secondary.primary, CircleShape)
                            .border(1.5.dp, colors.glassSurface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "*",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.backgroundBase
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isCaptain) {
                        RoleBadge("C", hierarchy.secondary.primary, colors.backgroundBase)
                    }

                    if (isKeeper) {
                        RoleBadge("WK", colors.statusInfo, colors.textPrimary)
                    }
                }

                Text(
                    text = role,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )
            }

            if (primaryStat.isNotEmpty()) {
                StatColumn(
                    value = primaryStat,
                    label = primaryStatLabel,
                    valueSize = 22,
                    valueColor = if (isOnStrike) hierarchy.secondary.primary else colors.textPrimary,
                    labelSize = 11
                )
            }

            if (secondaryStat.isNotEmpty()) {
                StatColumn(
                    value = secondaryStat,
                    label = secondaryStatLabel,
                    valueSize = 16,
                    valueColor = colors.textSecondary,
                    labelSize = 10,
                    valueWeight = FontWeight.Bold,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(start = 8.dp)
                )
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
    val hierarchy = BouncerDesignSystem.hierarchy
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

    val colors = BouncerDesignSystem.colors

    PremiumGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )

                if (isChasing && target != null) {
                    Box(
                        modifier = Modifier
                            .background(
                                colors.statusInfo.copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Target: $target",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.statusInfo
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = score,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = hierarchy.secondary.secondary,
                    style = LocalTextStyle.current.copy(
                        shadow = Shadow(
                            color = hierarchy.secondary.primary.copy(alpha = 0.3f),
                            offset = Offset(0f, 2f),
                            blurRadius = 8f
                        )
                    )
                )

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Overs: $overs",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary
                    )

                    if (runRate.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "CRR: $runRate",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textTertiary
                            )

                            if (isChasing && requiredRate.isNotEmpty()) {
                                Text(
                                    text = "RRR: $requiredRate",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (requiredRate.toFloatOrNull() ?: 0f > 12f)
                                        colors.statusLive else colors.statusSuccess
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
    val colors = BouncerDesignSystem.colors

    PremiumGlassCard(
        modifier = modifier,
        accentColor = colors.statusLive
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.statusLive.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SportsCricket,
                    contentDescription = null,
                    tint = colors.statusLive,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LAST WICKET",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.statusLive,
                    letterSpacing = 1.sp
                )

                Text(
                    text = playerName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )

                Text(
                    text = howOut,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            StatColumn(
                value = score,
                label = "$balls balls",
                valueSize = 20,
                valueColor = colors.textPrimary,
                labelSize = 11
            )
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
    val hierarchy = BouncerDesignSystem.hierarchy
    val player1Contribution = if (runs > 0) player1Runs.toFloat() / runs else 0.5f

    val animatedContribution by animateFloatAsState(
        targetValue = player1Contribution,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "contribution"
    )

    val colors = BouncerDesignSystem.colors

    PremiumGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PARTNERSHIP",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary,
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
                        color = hierarchy.secondary.primary
                    )
                    Text(
                        text = "($balls)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                }
            }

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
                        .background(colors.statusSuccess)
                )
                Box(
                    modifier = Modifier
                        .weight((1f - animatedContribution).coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(colors.statusInfo)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PlayerContribution(
                    name = player1Name,
                    runs = player1Runs,
                    balls = player1Balls,
                    runsColor = colors.statusSuccess,
                    horizontalAlignment = Alignment.Start
                )

                PlayerContribution(
                    name = player2Name,
                    runs = player2Runs,
                    balls = player2Balls,
                    runsColor = colors.statusInfo,
                    horizontalAlignment = Alignment.End
                )
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
    val colors = BouncerDesignSystem.colors

    PremiumGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "OVER $overNumber",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary,
                    letterSpacing = 1.sp
                )

                if (bowlerName.isNotEmpty()) {
                    Text(
                        text = bowlerName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textTertiary
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                balls.forEach { ball ->
                    BallDisplay(ball = ball)
                }

                repeat((6 - balls.size).coerceAtLeast(0)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                colors.glassBorder.copy(alpha = 0.2f),
                                CircleShape
                            )
                            .border(
                                1.dp,
                                colors.glassBorder.copy(alpha = 0.3f),
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
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors

    val backgroundColor = when {
        ball == "W" -> colors.statusLive
        ball == "6" -> hierarchy.secondary.primary
        ball == "4" -> colors.statusInfo
        ball.contains("WD") || ball.contains("NB") -> colors.statusWarning
        ball == "0" -> colors.ballDot
        else -> colors.glassBorderHighlight
    }

    val textColor = when {
        ball == "W" || ball == "6" || ball == "4" -> colors.backgroundBase
        ball.contains("WD") || ball.contains("NB") -> colors.backgroundBase
        else -> colors.textPrimary
    }

    val isHighlight = ball in listOf("W", "6", "4")

    Box(
        modifier = Modifier
            .size(36.dp)
            .shadow(
                elevation = if (isHighlight) 8.dp else 0.dp,
                shape = CircleShape,
                spotColor = backgroundColor.copy(alpha = 0.5f)
            )
            .background(backgroundColor, CircleShape)
            .border(
                1.dp,
                if (isHighlight) colors.textPrimary.copy(alpha = 0.3f)
                else colors.glassBorder.copy(alpha = 0.3f),
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

    val colors = BouncerDesignSystem.colors

    PremiumGlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "RUN RATE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
                letterSpacing = 1.sp
            )

            RateRow(
                label = "CRR",
                fraction = animatedCurrent,
                value = currentRate,
                barColor = colors.statusSuccess,
                textColor = colors.statusSuccess
            )

            if (requiredRate != null) {
                val rrrColor = when {
                    requiredRate > 12f -> colors.statusLive
                    requiredRate > 8f -> colors.statusWarning
                    else -> colors.statusInfo
                }
                RateRow(
                    label = "RRR",
                    fraction = animatedRequired,
                    value = requiredRate,
                    barColor = rrrColor,
                    textColor = rrrColor
                )
            }
        }
    }
}
