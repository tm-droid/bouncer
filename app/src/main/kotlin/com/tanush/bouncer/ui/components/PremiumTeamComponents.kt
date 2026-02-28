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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.theme.*

/**
 * Premium team profile card with stats and animations
 */
@Composable
fun PremiumTeamCard(
    teamName: String,
    teamLogo: String?,
    teamCountry: String?,
    teamType: TeamType,
    isFavorite: Boolean = false,
    recentForm: List<MatchResult>? = null,
    stats: TeamQuickStats? = null,
    onFavoriteClick: () -> Unit = {},
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "teamCard")
    
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "teamShimmer"
    )
    
    val favoriteScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "favoriteScale"
    )
    
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "pressScale"
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(
                elevation = if (isFavorite) 20.dp else 12.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = if (isFavorite) ShinyGoldDeep.copy(alpha = 0.25f) else Color.Transparent
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = ShinyGoldDeep.copy(alpha = 0.1f)),
                onClick = onClick
            ),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = if (isFavorite) {
            BorderStroke(
                2.dp,
                Brush.linearGradient(
                    colors = listOf(
                        ShinyGoldDeep.copy(alpha = 0.6f),
                        ShinyGoldMid,
                        ShinyGoldDeep.copy(alpha = 0.6f)
                    )
                )
            )
        } else null
    ) {
        Box {
            // Shimmer overlay
            if (isFavorite) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val start = shimmerOffset * size.width
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.03f),
                                Color.Transparent
                            ),
                            start = Offset(start, 0f),
                            end = Offset(start + size.width * 0.4f, size.height)
                        )
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Team info
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Team logo
                        TeamLogoLarge(
                            logo = teamLogo,
                            name = teamName,
                            isFavorite = isFavorite
                        )
                        
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = teamName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TeamTypeBadge(type = teamType)
                                
                                teamCountry?.let {
                                    Text(
                                        text = it,
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                    
                    // Favorite button
                    IconButton(
                        onClick = onFavoriteClick,
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = favoriteScale
                                scaleY = favoriteScale
                            }
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) ErrorRed else TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                // Recent form
                recentForm?.takeIf { it.isNotEmpty() }?.let { form ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "RECENT FORM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = TextMuted
                        )
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            form.take(5).forEach { result ->
                                FormIndicator(result = result)
                            }
                        }
                    }
                }
                
                // Quick stats
                stats?.let { teamStats ->
                    HorizontalDivider(color = CardBorder.copy(alpha = 0.3f))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        QuickStat(label = "Matches", value = "${teamStats.matchesPlayed}")
                        QuickStat(label = "Won", value = "${teamStats.wins}", valueColor = SuccessGreen)
                        QuickStat(label = "Lost", value = "${teamStats.losses}", valueColor = WicketRed)
                        QuickStat(label = "Win %", value = "${teamStats.winPercentage}%", valueColor = ShinyGoldDeep)
                    }
                }
            }
        }
    }
}

data class TeamQuickStats(
    val matchesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int = 0,
    val winPercentage: Int
)

enum class MatchResult {
    WIN, LOSS, DRAW, NO_RESULT
}

enum class TeamType(val displayName: String, val color: Color) {
    INTERNATIONAL("International", LiveBlue),
    FRANCHISE("Franchise", BoundarySix),
    DOMESTIC("Domestic", SuccessGreen),
    WOMEN("Women", Color(0xFFE91E63))
}

@Composable
private fun TeamLogoLarge(
    logo: String?,
    name: String,
    isFavorite: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoGlow"
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .then(
                if (isFavorite) {
                    Modifier.drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ShinyGoldDeep.copy(alpha = glowPulse * 0.4f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.maxDimension * 0.8f
                        )
                    }
                } else Modifier
            )
            .border(
                width = if (isFavorite) 2.dp else 1.dp,
                brush = if (isFavorite) {
                    Brush.linearGradient(
                        colors = listOf(ShinyGoldDeep, ShinyGoldMid)
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(CardBorder, CardBorder.copy(alpha = 0.5f))
                    )
                },
                CircleShape
            )
            .clip(CircleShape)
            .background(CardDarkBlue),
        contentAlignment = Alignment.Center
    ) {
        if (!logo.isNullOrEmpty()) {
            CoilImage(
                model = logo,
                contentDescription = name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = name.take(3).uppercase(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = ShinyGoldDeep
            )
        }
    }
}

@Composable
private fun TeamTypeBadge(type: TeamType) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = type.color.copy(alpha = 0.12f)
    ) {
        Text(
            text = type.displayName,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = type.color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun FormIndicator(result: MatchResult) {
    val colorAndText: Pair<Color, String> = when (result) {
        MatchResult.WIN -> SuccessGreen to "W"
        MatchResult.LOSS -> WicketRed to "L"
        MatchResult.DRAW -> WarningYellow to "D"
        MatchResult.NO_RESULT -> TextMuted to "NR"
    }
    val color = colorAndText.first
    val text = colorAndText.second
    
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun QuickStat(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = TextMuted
        )
    }
}

/**
 * Favorite team compact card
 */
@Composable
fun FavoriteTeamChip(
    teamName: String,
    teamLogo: String?,
    isSelected: Boolean = false,
    onRemove: () -> Unit = {},
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "chip")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chipGlow"
    )

    Surface(
        modifier = modifier
            .then(
                if (isSelected) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ShinyGoldDeep.copy(alpha = glowPulse * 0.3f),
                                    Color.Transparent
                                )
                            ),
                            cornerRadius = CornerRadius(24.dp.toPx())
                        )
                    }
                } else Modifier
            )
            .shadow(
                elevation = if (isSelected) 12.dp else 6.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = if (isSelected) ShinyGoldDeep.copy(alpha = 0.2f) else Color.Transparent
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (isSelected) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.05f),
        border = if (isSelected) {
            BorderStroke(1.5.dp, ShinyGoldDeep.copy(alpha = 0.6f))
        } else {
            BorderStroke(1.dp, CardBorder.copy(alpha = 0.3f))
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Team logo
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .border(
                        1.dp,
                        if (isSelected) ShinyGoldDeep.copy(alpha = 0.5f) else CardBorder,
                        CircleShape
                    )
                    .clip(CircleShape)
                    .background(CardDarkBlue),
                contentAlignment = Alignment.Center
            ) {
                if (!teamLogo.isNullOrEmpty()) {
                    CoilImage(
                        model = teamLogo,
                        contentDescription = teamName,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = teamName.take(2).uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = ShinyGoldDeep
                    )
                }
            }
            
            // Team name
            Text(
                text = teamName,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) ShinyGoldDeep else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Remove button
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(16.dp),
                    tint = TextMuted
                )
            }
        }
    }
}

/**
 * Team selector horizontal list
 */
@Composable
fun TeamSelectorRow(
    teams: List<TeamInfo>,
    selectedTeam: String?,
    onTeamSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(teams) { team ->
            TeamSelectorChip(
                team = team,
                isSelected = team.id == selectedTeam,
                onClick = { onTeamSelect(team.id) }
            )
        }
    }
}

data class TeamInfo(
    val id: String,
    val name: String,
    val logo: String?,
    val shortName: String? = null
)

@Composable
private fun TeamSelectorChip(
    team: TeamInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "chipScale"
    )

    Surface(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isSelected) 10.dp else 4.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = if (isSelected) ShinyGoldDeep.copy(alpha = 0.2f) else Color.Transparent
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.05f),
        border = if (isSelected) {
            BorderStroke(1.5.dp, ShinyGoldDeep)
        } else null
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CardDarkBlue)
                    .border(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) ShinyGoldDeep else CardBorder,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!team.logo.isNullOrEmpty()) {
                    CoilImage(
                        model = team.logo,
                        contentDescription = team.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = (team.shortName ?: team.name).take(3).uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isSelected) ShinyGoldDeep else TextSecondary
                    )
                }
            }
            
            // Name
            Text(
                text = team.shortName ?: team.name,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) ShinyGoldDeep else TextSecondary,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Team comparison card
 */
@Composable
fun TeamVsTeamCard(
    team1: TeamInfo,
    team2: TeamInfo,
    team1Stats: VersusStats,
    team2Stats: VersusStats,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = ShinyGoldDeep.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Team logos and VS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team 1
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TeamLogoLarge(logo = team1.logo, name = team1.name, isFavorite = false)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = team1.shortName ?: team1.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ShinyGoldDeep
                    )
                }
                
                // VS
                Text(
                    text = "VS",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = TextMuted
                )
                
                // Team 2
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TeamLogoLarge(logo = team2.logo, name = team2.name, isFavorite = false)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = team2.shortName ?: team2.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                }
            }
            
            HorizontalDivider(color = CardBorder.copy(alpha = 0.3f))
            
            // Stats comparison
            VersusStatRow(label = "Matches Won", value1 = team1Stats.wins, value2 = team2Stats.wins)
            VersusStatRow(label = "Highest Score", value1 = team1Stats.highestScore, value2 = team2Stats.highestScore)
            VersusStatRow(label = "Avg. Score", value1 = team1Stats.avgScore, value2 = team2Stats.avgScore)
        }
    }
}

data class VersusStats(
    val wins: Int,
    val highestScore: Int,
    val avgScore: Int
)

@Composable
private fun VersusStatRow(
    label: String,
    value1: Int,
    value2: Int
) {
    val total = value1 + value2
    val ratio1 = if (total > 0) value1.toFloat() / total else 0.5f
    
    val animatedRatio by animateFloatAsState(
        targetValue = ratio1,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "vsRatio"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$value1",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (value1 > value2) ShinyGoldDeep else TextSecondary
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = TextMuted
            )
            Text(
                text = "$value2",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (value2 > value1) ShinyGoldDeep else TextSecondary
            )
        }
        
        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(CardBorder)
        ) {
            Row {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedRatio)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(ShinyGoldDeep, ShinyGoldMid)
                            )
                        )
                )
            }
        }
    }
}
