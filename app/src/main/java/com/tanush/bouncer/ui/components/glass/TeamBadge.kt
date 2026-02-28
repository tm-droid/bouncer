package com.tanush.bouncer.ui.components.glass

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.TeamPrimaryColors

/**
 * Match status types for displaying badge states.
 */
enum class MatchStatus {
    LIVE,
    UPCOMING,
    COMPLETED,
    DELAYED,
    ABANDONED,
    INNINGS_BREAK,
    STUMPS,
    TEA,
    LUNCH,
    DRINKS
}

/**
 * Team flag background with gradient effect.
 * Creates the flag-like background seen behind team sections in mockups.
 *
 * @param teamColor Primary team color
 * @param modifier Modifier for the component
 * @param alignment Whether to align gradient to start or end
 * @param content Content to display over the flag background
 */
@Composable
fun TeamFlag(
    teamColor: Color,
    modifier: Modifier = Modifier,
    alignment: FlagAlignment = FlagAlignment.START,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val gradient = when (alignment) {
        FlagAlignment.START -> Brush.linearGradient(
            colors = listOf(
                teamColor.copy(alpha = 0.4f),
                teamColor.copy(alpha = 0.2f),
                Color.Transparent
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
        FlagAlignment.END -> Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                teamColor.copy(alpha = 0.2f),
                teamColor.copy(alpha = 0.4f)
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
        FlagAlignment.CENTER -> Brush.radialGradient(
            colors = listOf(
                teamColor.copy(alpha = 0.3f),
                Color.Transparent
            )
        )
    }
    
    Box(
        modifier = modifier
            .background(gradient),
        content = content
    )
}

enum class FlagAlignment {
    START, END, CENTER
}

/**
 * Circular team logo container with team color border.
 *
 * @param teamCode Team code (e.g., "IND", "AUS")
 * @param logoUrl URL of the team logo image
 * @param modifier Modifier for the component
 * @param size Size of the circle
 * @param showBorder Whether to show team color border
 * @param showGlow Whether to show subtle glow effect
 */
@Composable
fun TeamLogoCircle(
    teamCode: String,
    logoUrl: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    showBorder: Boolean = true,
    showGlow: Boolean = true
) {
    val teamColor = TeamPrimaryColors.getTeamColor(teamCode)
    val colors = BouncerDesignSystem.colors
    
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (showGlow) {
                    Modifier.drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    teamColor.copy(alpha = 0.2f),
                                    Color.Transparent
                                ),
                                radius = this.size.maxDimension * 0.8f
                            )
                        )
                    }
                } else {
                    Modifier
                }
            )
            .clip(CircleShape)
            .background(teamColor.copy(alpha = 0.2f))
            .then(
                if (showBorder) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (logoUrl != null) {
            AsyncImage(
                model = logoUrl,
                contentDescription = "$teamCode logo",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            // Fallback: show team code
            Text(
                text = teamCode.take(3).uppercase(),
                style = BouncerDesignSystem.typography.labelMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Compact team logo with flag emoji placeholder.
 * Used when full logo images aren't available.
 *
 * @param flagEmoji Flag emoji for the team
 * @param teamColor Team's primary color
 * @param modifier Modifier for the component
 * @param size Size of the container
 */
@Composable
fun TeamFlagEmoji(
    flagEmoji: String,
    teamColor: Color? = null,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val colors = BouncerDesignSystem.colors
    val bgColor = teamColor?.copy(alpha = 0.2f) ?: colors.glassBackground
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = flagEmoji,
            style = BouncerDesignSystem.typography.headingMedium
        )
    }
}

/**
 * Horizontal team color accent bar.
 * Used to indicate which team is batting/bowling.
 *
 * @param teamColor Team's primary color
 * @param modifier Modifier for the bar
 * @param height Height of the bar
 * @param showGlow Whether to show glow effect
 */
@Composable
fun TeamColorBar(
    teamColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
    showGlow: Boolean = true
) {
    Box(
        modifier = modifier
            .height(height)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        teamColor,
                        teamColor.copy(alpha = 0.6f),
                        Color.Transparent
                    )
                )
            )
            .then(
                if (showGlow) {
                    Modifier.drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    teamColor.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            ),
                            size = this.size.copy(height = this.size.height * 3)
                        )
                    }
                } else {
                    Modifier
                }
            )
    )
}

/**
 * Vertical team color indicator (used on card borders).
 *
 * @param teamColor Team's primary color
 * @param modifier Modifier for the indicator
 * @param width Width of the indicator
 */
@Composable
fun TeamColorIndicator(
    teamColor: Color,
    modifier: Modifier = Modifier,
    width: Dp = 4.dp
) {
    Box(
        modifier = modifier
            .width(width)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        teamColor.copy(alpha = 0.8f),
                        teamColor,
                        teamColor.copy(alpha = 0.8f)
                    )
                )
            )
    )
}

/**
 * Animated red pulsing dot for live matches.
 *
 * @param modifier Modifier for the dot
 * @param size Size of the dot
 */
@Composable
fun LivePulse(
    modifier: Modifier = Modifier,
    size: Dp = 8.dp
) {
    val colors = BouncerDesignSystem.colors
    
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .drawBehind {
                // Glow effect
                drawCircle(
                    color = colors.statusLive.copy(alpha = alpha * 0.3f),
                    radius = this.size.maxDimension * 0.8f
                )
            }
            .clip(CircleShape)
            .background(colors.statusLive.copy(alpha = alpha))
    )
}

/**
 * Match status badge showing current match state.
 *
 * @param status Current match status
 * @param modifier Modifier for the badge
 */
@Composable
fun MatchStatusBadge(
    status: MatchStatus,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    val (backgroundColor, borderColor, textColor, text) = when (status) {
        MatchStatus.LIVE -> Quadruple(
            colors.statusLive.copy(alpha = 0.2f),
            colors.statusLive.copy(alpha = 0.3f),
            colors.statusLive,
            "LIVE"
        )
        MatchStatus.UPCOMING -> Quadruple(
            colors.goldPrimary.copy(alpha = 0.2f),
            colors.goldPrimary.copy(alpha = 0.3f),
            colors.goldPrimary,
            "UPCOMING"
        )
        MatchStatus.COMPLETED -> Quadruple(
            colors.textMuted.copy(alpha = 0.15f),
            colors.textMuted.copy(alpha = 0.2f),
            colors.textSecondary,
            "COMPLETED"
        )
        MatchStatus.DELAYED -> Quadruple(
            colors.statusWarning.copy(alpha = 0.2f),
            colors.statusWarning.copy(alpha = 0.3f),
            colors.statusWarning,
            "DELAYED"
        )
        MatchStatus.ABANDONED -> Quadruple(
            colors.statusLive.copy(alpha = 0.15f),
            colors.statusLive.copy(alpha = 0.2f),
            colors.statusLive.copy(alpha = 0.7f),
            "ABANDONED"
        )
        MatchStatus.INNINGS_BREAK -> Quadruple(
            colors.goldPrimary.copy(alpha = 0.15f),
            colors.goldPrimary.copy(alpha = 0.2f),
            colors.goldPrimary.copy(alpha = 0.8f),
            "INNINGS BREAK"
        )
        MatchStatus.STUMPS -> Quadruple(
            colors.statusInfo.copy(alpha = 0.15f),
            colors.statusInfo.copy(alpha = 0.2f),
            colors.statusInfo,
            "STUMPS"
        )
        MatchStatus.TEA -> Quadruple(
            colors.statusInfo.copy(alpha = 0.15f),
            colors.statusInfo.copy(alpha = 0.2f),
            colors.statusInfo,
            "TEA"
        )
        MatchStatus.LUNCH -> Quadruple(
            colors.statusInfo.copy(alpha = 0.15f),
            colors.statusInfo.copy(alpha = 0.2f),
            colors.statusInfo,
            "LUNCH"
        )
        MatchStatus.DRINKS -> Quadruple(
            colors.statusInfo.copy(alpha = 0.15f),
            colors.statusInfo.copy(alpha = 0.2f),
            colors.statusInfo,
            "DRINKS"
        )
    }
    
    val shape = RoundedCornerShape(percent = 50)
    
    Row(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (status == MatchStatus.LIVE) {
            LivePulse(size = 6.dp)
        }
        
        Text(
            text = text,
            style = typography.matchStatus,
            color = textColor
        )
    }
}

/**
 * Live indicator with LIVE text and pulsing dot.
 * More prominent version of MatchStatusBadge for LIVE status.
 *
 * @param modifier Modifier for the indicator
 */
@Composable
fun LiveIndicator(
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    val shape = RoundedCornerShape(percent = 50)
    
    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.statusLive.copy(alpha = 0.2f))
            .border(1.dp, colors.statusLive.copy(alpha = 0.3f), shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LivePulse(size = 6.dp)
        
        Text(
            text = "LIVE",
            style = typography.matchStatus,
            color = colors.statusLive
        )
    }
}

/**
 * Team name display with team color accent.
 *
 * @param teamCode Short team code (e.g., "IND")
 * @param teamName Full team name (optional)
 * @param teamColor Team's primary color
 * @param modifier Modifier for the component
 * @param showColorDot Whether to show a colored dot indicator
 */
@Composable
fun TeamNameBadge(
    teamCode: String,
    teamName: String? = null,
    teamColor: Color? = null,
    modifier: Modifier = Modifier,
    showColorDot: Boolean = true
) {
    val color = teamColor ?: TeamPrimaryColors.getTeamColor(teamCode)
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (showColorDot) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        
        Text(
            text = teamName ?: teamCode.uppercase(),
            style = if (teamName != null) typography.teamName else typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Helper data class for badge styling.
 */
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
