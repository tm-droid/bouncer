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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.components.CoilImage
import coil.request.ImageRequest
import com.tanush.bouncer.managers.TeamLogoManager
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.network.SquadScraper
import com.tanush.bouncer.ui.theme.*
import kotlinx.coroutines.launch

data class Player(
    val name: String,
    val role: PlayerRole,
    val isCaptain: Boolean = false,
    val isWicketKeeper: Boolean = false,
    val imageUrl: String? = null
)

enum class PlayerRole(val displayName: String, val icon: @Composable () -> Unit) {
    BATSMAN("Batsman", { Text("BAT", fontSize = 8.sp, color = BoundarySix, fontWeight = FontWeight.Bold) }),
    BOWLER("Bowler", { Text("BWL", fontSize = 8.sp, color = WicketBlue, fontWeight = FontWeight.Bold) }),
    ALL_ROUNDER("All-Rounder", { Text("AR", fontSize = 8.sp, color = SuccessGreen, fontWeight = FontWeight.Bold) }),
    WICKET_KEEPER("Wicket-Keeper", { Text("WK", fontSize = 8.sp, color = ChartPurple, fontWeight = FontWeight.Bold) })
}

/**
 * Convert a role string from the scraper to a PlayerRole enum
 */
private fun roleFromString(role: String): PlayerRole {
    val lowerRole = role.lowercase()
    return when {
        lowerRole.contains("bowl") || lowerRole.contains("fast") || lowerRole.contains("spin") || lowerRole.contains("medium") -> PlayerRole.BOWLER
        lowerRole.contains("all") || lowerRole.contains("ar") -> PlayerRole.ALL_ROUNDER
        lowerRole.contains("keeper") || lowerRole.contains("wk") -> PlayerRole.WICKET_KEEPER
        else -> PlayerRole.BATSMAN
    }
}

/**
 * Convert SquadScraper.Player to screen Player
 */
private fun SquadScraper.Player.toScreenPlayer(): Player {
    return Player(
        name = this.name,
        role = if (this.isWicketKeeper) PlayerRole.WICKET_KEEPER else roleFromString(this.role),
        isCaptain = this.isCaptain,
        isWicketKeeper = this.isWicketKeeper,
        imageUrl = this.imageUrl.takeIf { it.isNotEmpty() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadScreen(
    match: CricketMatch,
    onBackClick: () -> Unit,
    onPlayerClick: ((Player) -> Unit)? = null
) {
    val team1 = match.teams.firstOrNull()
    val team2 = match.teams.getOrNull(1)
    var selectedTeamIndex by remember { mutableIntStateOf(0) }
    
    val team1LogoUrl = TeamLogoManager.getBestLogoUrl(team1?.name, team1?.logoUrl, 256, 256)
    val team2LogoUrl = TeamLogoManager.getBestLogoUrl(team2?.name, team2?.logoUrl, 256, 256)
    
    // State for fetched squads
    var team1Squad by remember { mutableStateOf<List<Player>>(emptyList()) }
    var team2Squad by remember { mutableStateOf<List<Player>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    // Fetch squads from Cricbuzz
    LaunchedEffect(match.id) {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val (scraped1, scraped2) = SquadScraper.fetchSquads(match.id, match.matchTitle)
                if (scraped1.isEmpty() && scraped2.isEmpty()) {
                    // Fallback to placeholder squads if no data found
                    team1Squad = generatePlaceholderSquad(team1?.name ?: "Team 1")
                    team2Squad = generatePlaceholderSquad(team2?.name ?: "Team 2")
                    errorMessage = "Squads not yet announced"
                } else {
                    team1Squad = scraped1.map { it.toScreenPlayer() }
                    team2Squad = scraped2.map { it.toScreenPlayer() }
                }
            } catch (e: Exception) {
                // Fallback to placeholder squads on error
                team1Squad = generatePlaceholderSquad(team1?.name ?: "Team 1")
                team2Squad = generatePlaceholderSquad(team2?.name ?: "Team 2")
                errorMessage = "Could not load squads"
            }
            isLoading = false
        }
    }
    
    val currentSquad = if (selectedTeamIndex == 0) team1Squad else team2Squad
    val currentTeam = if (selectedTeamIndex == 0) team1 else team2

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "SQUADS",
                        fontSize = 18.sp,
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
            // Premium Background
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GoldDark.copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.2f, size.height * 0.1f),
                        radius = size.width * 0.5f
                    ),
                    radius = size.width * 0.5f,
                    center = Offset(size.width * 0.2f, size.height * 0.1f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            WicketBlue.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.9f, size.height * 0.6f),
                        radius = size.width * 0.6f
                    ),
                    radius = size.width * 0.6f,
                    center = Offset(size.width * 0.9f, size.height * 0.6f)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Team Selector
                item {
                    TeamSelectorCard(
                        team1Name = team1?.name ?: "Team 1",
                        team2Name = team2?.name ?: "Team 2",
                        team1LogoUrl = team1LogoUrl,
                        team2LogoUrl = team2LogoUrl,
                        selectedIndex = selectedTeamIndex,
                        onTeamSelected = { selectedTeamIndex = it }
                    )
                }
                
                // Loading indicator
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = GoldDark,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
                
                // Error message (if any)
                if (!isLoading && errorMessage != null) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.5.dp, CardBorder.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = TextTertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = errorMessage ?: "",
                                    fontSize = 14.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                // Playing XI Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = GoldDark,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "PLAYING XI",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 1.5.sp
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            CardBorder.copy(alpha = 0.4f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Surface(
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${currentSquad.size} Players",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextTertiary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Squad Grid
                items(currentSquad.chunked(2)) { rowPlayers ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowPlayers.forEach { player ->
                            PlayerCard(
                                player = player,
                                onClick = { onPlayerClick?.invoke(player) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowPlayers.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Legend
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SquadLegend()
                }
            }
        }
    }
}

@Composable
private fun TeamSelectorCard(
    team1Name: String,
    team2Name: String,
    team1LogoUrl: String?,
    team2LogoUrl: String?,
    selectedIndex: Int,
    onTeamSelected: (Int) -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = GoldDark.copy(alpha = 0.15f)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(0.5.dp, CardBorder.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Team 1
            TeamSelectorItem(
                teamName = team1Name,
                logoUrl = team1LogoUrl,
                isSelected = selectedIndex == 0,
                onClick = { onTeamSelected(0) },
                modifier = Modifier.weight(1f)
            )

            // VS Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(80.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                CardBorder.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Team 2
            TeamSelectorItem(
                teamName = team2Name,
                logoUrl = team2LogoUrl,
                isSelected = selectedIndex == 1,
                onClick = { onTeamSelected(1) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TeamSelectorItem(
    teamName: String,
    logoUrl: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "teamScale"
    )

    Surface(
        onClick = onClick,
        color = if (isSelected) GoldDark.copy(alpha = 0.12f) else Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) BorderStroke(1.dp, GoldDark.copy(alpha = 0.4f)) else null,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isSelected) GoldDark.copy(alpha = 0.15f) else CardDarkBlue,
                        CircleShape
                    )
                    .border(
                        1.dp,
                        if (isSelected) GoldDark.copy(alpha = 0.4f) else CardBorder.copy(alpha = 0.3f),
                        CircleShape
                    )
            ) {
                CoilImage(
                    model = if (logoUrl == null) null else ImageRequest.Builder(context)
                        .data(logoUrl)
                        .crossfade(true)
                        .size(64)
                        .build(),
                    contentDescription = teamName,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Fit
                )
            }

            Text(
                text = teamName,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) GoldDark else TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlayerCard(
    player: Player,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "playerGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(0.85f)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = if (player.isCaptain) GoldDark.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.3f)
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(18.dp),
        border = if (player.isCaptain) {
            BorderStroke(
                1.dp,
                Brush.linearGradient(
                    colors = listOf(
                        GoldLight.copy(alpha = 0.5f),
                        GoldDark.copy(alpha = 0.3f)
                    )
                )
            )
        } else {
            BorderStroke(0.5.dp, CardBorder.copy(alpha = 0.3f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Player Photo with badges
            Box(
                contentAlignment = Alignment.Center
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.08f),
                                    CardDarkBlue
                                )
                            ),
                            CircleShape
                        )
                        .border(1.dp, CardBorder.copy(alpha = 0.3f), CircleShape)
                ) {
                    // Placeholder icon for player
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Captain badge
                if (player.isCaptain) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(20.dp)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(ShinyGoldLight, ShinyGoldDeep)
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
                            color = BlackBackground
                        )
                    }
                }

                // Wicket keeper badge
                if (player.isWicketKeeper) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-4).dp, y = (-4).dp)
                            .size(20.dp)
                            .background(ChartPurple, CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "WK",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }

            // Player Name
            Text(
                text = player.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )

            // Role Badge
            Surface(
                color = when (player.role) {
                    PlayerRole.BATSMAN -> BoundarySix.copy(alpha = 0.15f)
                    PlayerRole.BOWLER -> WicketBlue.copy(alpha = 0.15f)
                    PlayerRole.ALL_ROUNDER -> SuccessGreen.copy(alpha = 0.15f)
                    PlayerRole.WICKET_KEEPER -> ChartPurple.copy(alpha = 0.15f)
                },
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = player.role.displayName,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (player.role) {
                        PlayerRole.BATSMAN -> BoundarySix
                        PlayerRole.BOWLER -> WicketBlue
                        PlayerRole.ALL_ROUNDER -> SuccessGreen
                        PlayerRole.WICKET_KEEPER -> ChartPurple
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun SquadLegend() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDarkBlue.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.5.dp, CardBorder.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LegendItem("C", "Captain", ShinyGoldDeep)
            LegendItem("WK", "Keeper", ChartPurple)
            LegendItem("BAT", "Batsman", BoundarySix)
            LegendItem("BWL", "Bowler", WicketBlue)
        }
    }
}

@Composable
private fun LegendItem(
    badge: String,
    label: String,
    color: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(color.copy(alpha = 0.15f), CircleShape)
                .border(0.5.dp, color.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badge,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = TextTertiary
        )
    }
}

private fun generatePlaceholderSquad(teamName: String): List<Player> {
    val batsmen = listOf("Player 1", "Player 2", "Player 3", "Player 4")
    val allRounders = listOf("Player 5", "Player 6", "Player 7")
    val bowlers = listOf("Player 8", "Player 9", "Player 10", "Player 11")
    
    return listOf(
        Player(batsmen[0], PlayerRole.BATSMAN, isCaptain = true),
        Player(batsmen[1], PlayerRole.WICKET_KEEPER, isWicketKeeper = true),
        Player(batsmen[2], PlayerRole.BATSMAN),
        Player(batsmen[3], PlayerRole.BATSMAN),
        Player(allRounders[0], PlayerRole.ALL_ROUNDER),
        Player(allRounders[1], PlayerRole.ALL_ROUNDER),
        Player(allRounders[2], PlayerRole.ALL_ROUNDER),
        Player(bowlers[0], PlayerRole.BOWLER),
        Player(bowlers[1], PlayerRole.BOWLER),
        Player(bowlers[2], PlayerRole.BOWLER),
        Player(bowlers[3], PlayerRole.BOWLER)
    )
}
