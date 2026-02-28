package com.tanush.bouncer.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.TeamPrimaryColors

/**
 * Player role types for badge display.
 */
enum class PlayerRole {
    CAPTAIN,        // C
    WICKETKEEPER,   // WK
    BATSMAN,        // BAT
    BOWLER,         // BOWL
    ALL_ROUNDER,    // AR
    SUBSTITUTE;     // SUB
    
    val shortLabel: String
        get() = when (this) {
            CAPTAIN -> "C"
            WICKETKEEPER -> "WK"
            BATSMAN -> "BAT"
            BOWLER -> "BOWL"
            ALL_ROUNDER -> "AR"
            SUBSTITUTE -> "SUB"
        }
}

/**
 * Circular player avatar with optional team color border.
 *
 * @param imageUrl URL of the player's photo
 * @param playerName Player's name for accessibility
 * @param modifier Modifier for the avatar
 * @param size Size of the avatar
 * @param teamColor Team's primary color for border
 * @param showBorder Whether to show the team color border
 * @param isOnStrike Whether the player is currently batting (shows gold highlight)
 * @param isDismissed Whether the player has been dismissed (shows greyscale)
 */
@Composable
fun PlayerAvatar(
    imageUrl: String?,
    playerName: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    teamColor: Color? = null,
    showBorder: Boolean = true,
    isOnStrike: Boolean = false,
    isDismissed: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    
    val borderColor = when {
        isOnStrike -> colors.goldPrimary
        teamColor != null -> teamColor.copy(alpha = 0.5f)
        else -> Color.White.copy(alpha = 0.1f)
    }
    
    val borderWidth = if (isOnStrike) 2.dp else 1.dp
    
    // Greyscale color matrix for dismissed players
    val greyscaleMatrix = ColorMatrix().apply {
        setToSaturation(0f)
    }
    
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (isOnStrike) {
                    Modifier.drawBehind {
                        // Gold glow for on-strike player
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors.goldPrimary.copy(alpha = 0.3f),
                                    Color.Transparent
                                ),
                                radius = this.size.maxDimension * 0.7f
                            )
                        )
                    }
                } else {
                    Modifier
                }
            )
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.2f))
            .then(
                if (showBorder) {
                    Modifier.border(borderWidth, borderColor, CircleShape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = playerName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(borderWidth)
                    .clip(CircleShape)
                    .then(
                        if (isDismissed) {
                            Modifier.drawWithContent {
                                drawContent()
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.3f),
                                    blendMode = BlendMode.Darken
                                )
                            }
                        } else {
                            Modifier
                        }
                    ),
                contentScale = ContentScale.Crop,
                colorFilter = if (isDismissed) ColorFilter.colorMatrix(greyscaleMatrix) else null
            )
        } else {
            // Placeholder with initials
            val initials = playerName.split(" ")
                .take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .joinToString("")
            
            Text(
                text = initials,
                style = BouncerDesignSystem.typography.labelMedium,
                color = if (isDismissed) colors.textMuted else colors.textSecondary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Small player avatar variant.
 */
@Composable
fun PlayerAvatarSmall(
    imageUrl: String?,
    playerName: String,
    modifier: Modifier = Modifier,
    teamColor: Color? = null,
    showBorder: Boolean = true,
    isDismissed: Boolean = false
) {
    PlayerAvatar(
        imageUrl = imageUrl,
        playerName = playerName,
        modifier = modifier,
        size = 32.dp,
        teamColor = teamColor,
        showBorder = showBorder,
        isDismissed = isDismissed
    )
}

/**
 * Large player avatar variant for feature displays.
 */
@Composable
fun PlayerAvatarLarge(
    imageUrl: String?,
    playerName: String,
    modifier: Modifier = Modifier,
    teamColor: Color? = null,
    showBorder: Boolean = true,
    isOnStrike: Boolean = false,
    isDismissed: Boolean = false
) {
    PlayerAvatar(
        imageUrl = imageUrl,
        playerName = playerName,
        modifier = modifier,
        size = 64.dp,
        teamColor = teamColor,
        showBorder = showBorder,
        isOnStrike = isOnStrike,
        isDismissed = isDismissed
    )
}

/**
 * Horizontal player row with avatar, name, role, and optional stats.
 *
 * @param imageUrl URL of the player's photo
 * @param playerName Player's name
 * @param role Player's role (optional)
 * @param stats Stat value to display (e.g., "45 (32)")
 * @param teamColor Team color for avatar border
 * @param isOnStrike Whether the player is on strike
 * @param isDismissed Whether the player is dismissed
 * @param dismissalInfo How the player was dismissed (e.g., "c Kohli b Bumrah")
 * @param modifier Modifier for the row
 */
@Composable
fun PlayerRow(
    imageUrl: String?,
    playerName: String,
    modifier: Modifier = Modifier,
    role: PlayerRole? = null,
    stats: String? = null,
    teamColor: Color? = null,
    isOnStrike: Boolean = false,
    isDismissed: Boolean = false,
    dismissalInfo: String? = null
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar
        PlayerAvatar(
            imageUrl = imageUrl,
            playerName = playerName,
            size = 40.dp,
            teamColor = teamColor,
            isOnStrike = isOnStrike,
            isDismissed = isDismissed
        )
        
        // Name and info column
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = playerName,
                    style = typography.playerName,
                    color = if (isDismissed) colors.textMuted else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Role badge if provided
                if (role != null) {
                    RoleBadge(role = role)
                }
                
                // On-strike indicator
                if (isOnStrike) {
                    OnStrikeBadge()
                }
            }
            
            // Dismissal info if dismissed
            if (isDismissed && dismissalInfo != null) {
                Text(
                    text = dismissalInfo,
                    style = typography.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        // Stats
        if (stats != null) {
            Text(
                text = stats,
                style = typography.statValue,
                color = if (isDismissed) colors.textMuted else colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Small pill badge showing player role.
 *
 * @param role Player's role to display
 * @param modifier Modifier for the badge
 */
@Composable
fun RoleBadge(
    role: PlayerRole,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    val (backgroundColor, textColor) = when (role) {
        PlayerRole.CAPTAIN -> Pair(
            colors.goldPrimary.copy(alpha = 0.2f),
            colors.goldPrimary
        )
        PlayerRole.WICKETKEEPER -> Pair(
            colors.statusInfo.copy(alpha = 0.15f),
            colors.statusInfo
        )
        PlayerRole.BATSMAN -> Pair(
            colors.statusSuccess.copy(alpha = 0.15f),
            colors.statusSuccess
        )
        PlayerRole.BOWLER -> Pair(
            colors.statusLive.copy(alpha = 0.15f),
            colors.statusLive
        )
        PlayerRole.ALL_ROUNDER -> Pair(
            colors.ballLegBye.copy(alpha = 0.15f),
            colors.ballLegBye
        )
        PlayerRole.SUBSTITUTE -> Pair(
            colors.textMuted.copy(alpha = 0.2f),
            colors.textSecondary
        )
    }
    
    val shape = RoundedCornerShape(4.dp)
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = role.shortLabel,
            style = typography.labelSmall.copy(
                fontSize = typography.labelSmall.fontSize * 0.85f
            ),
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * On-strike indicator badge showing "STR" with gold highlight.
 */
@Composable
fun OnStrikeBadge(
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    val shape = RoundedCornerShape(4.dp)
    
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.goldPrimary)
            .drawBehind {
                // Glow effect
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.goldPrimary.copy(alpha = 0.5f),
                            Color.Transparent
                        ),
                        radius = size.maxDimension
                    )
                )
            }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "STR",
            style = typography.labelSmall.copy(
                fontSize = typography.labelSmall.fontSize * 0.85f
            ),
            color = Color.Black,
            fontWeight = FontWeight.Black
        )
    }
}

/**
 * Dismissed overlay effect for player cards/rows.
 * Applies a greyscale filter and dark overlay.
 */
@Composable
fun DismissedOverlay(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
        
        // Dark overlay
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )
    }
}

/**
 * Player card with full stats display.
 * Used in scorecard views.
 *
 * @param imageUrl URL of the player's photo
 * @param playerName Player's name
 * @param runs Runs scored
 * @param balls Balls faced
 * @param fours Number of fours
 * @param sixes Number of sixes
 * @param strikeRate Strike rate
 * @param dismissalInfo How player was dismissed
 * @param role Player's role
 * @param teamColor Team color
 * @param isOnStrike Whether player is on strike
 * @param isDismissed Whether player is dismissed
 * @param modifier Modifier for the card
 */
@Composable
fun PlayerScoreRow(
    imageUrl: String?,
    playerName: String,
    runs: Int,
    balls: Int,
    fours: Int,
    sixes: Int,
    strikeRate: Float,
    dismissalInfo: String? = null,
    role: PlayerRole? = null,
    teamColor: Color? = null,
    isOnStrike: Boolean = false,
    isDismissed: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    // On-strike highlighting
    val rowModifier = if (isOnStrike) {
        modifier
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        teamColor?.copy(alpha = 0.1f) ?: colors.statusSuccess.copy(alpha = 0.1f),
                        Color.Transparent
                    )
                )
            )
            .drawBehind {
                // Left border indicator
                drawRect(
                    color = colors.goldPrimary,
                    size = size.copy(width = 3.dp.toPx())
                )
            }
    } else {
        modifier
    }
    
    Row(
        modifier = rowModifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar and name
        Row(
            modifier = Modifier.weight(0.45f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlayerAvatar(
                imageUrl = imageUrl,
                playerName = playerName,
                size = 32.dp,
                teamColor = teamColor,
                isOnStrike = isOnStrike,
                isDismissed = isDismissed
            )
            
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = playerName,
                        style = typography.playerName,
                        color = if (isDismissed) colors.textMuted else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (role != null) {
                        RoleBadge(role = role)
                    }
                    
                    if (isOnStrike) {
                        OnStrikeBadge()
                    }
                }
                
                if (dismissalInfo != null && isDismissed) {
                    Text(
                        text = dismissalInfo,
                        style = typography.labelSmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        
        // Stats columns
        StatCell(
            value = runs.toString(),
            modifier = Modifier.weight(0.11f),
            isBold = true,
            isDimmed = isDismissed
        )
        StatCell(
            value = balls.toString(),
            modifier = Modifier.weight(0.11f),
            isDimmed = isDismissed
        )
        StatCell(
            value = fours.toString(),
            modifier = Modifier.weight(0.11f),
            isDimmed = isDismissed
        )
        StatCell(
            value = sixes.toString(),
            modifier = Modifier.weight(0.11f),
            isDimmed = isDismissed || sixes == 0
        )
        StatCell(
            value = String.format("%.1f", strikeRate),
            modifier = Modifier.weight(0.11f),
            isDimmed = isDismissed
        )
    }
}

@Composable
private fun StatCell(
    value: String,
    modifier: Modifier = Modifier,
    isBold: Boolean = false,
    isDimmed: Boolean = false
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    Text(
        text = value,
        style = typography.tabularNumbers,
        color = if (isDimmed) colors.textMuted else colors.textSecondary,
        fontWeight = if (isBold && !isDimmed) FontWeight.Bold else FontWeight.Normal,
        modifier = modifier,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}
