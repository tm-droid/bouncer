package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.components.AchievementBadges
import com.tanush.bouncer.ui.components.AchievementTier
import com.tanush.bouncer.ui.components.CoilImage
import com.tanush.bouncer.ui.components.FormIndicator
import com.tanush.bouncer.ui.components.PerformanceRadarChart
import com.tanush.bouncer.ui.components.PlayerAchievement
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import java.util.Locale

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
    val battingStats: BattingStats? = null,
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PlayerDetailScreen(
    playerName: String,
    playerRole: String,
    playerImageUrl: String?,
    isCaptain: Boolean = false,
    isWicketKeeper: Boolean = false,
    onBackClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    var isLoading by remember { mutableStateOf(true) }
    var player by remember { mutableStateOf<PlayerDetails?>(null) }

    LaunchedEffect(playerName) {
        delay(800)
        player = PlayerDetails(
            name = playerName,
            role = playerRole,
            imageUrl = playerImageUrl,
            country = "India",
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
            } else {
                null
            }
        )
        isLoading = false
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Player profile",
                        style = typography.headingMedium,
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        GlassBloomBackground(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(dimens.sm)
                    ) {
                        CircularProgressIndicator(color = colors.goldPrimary)
                        Text(
                            text = "Loading player data...",
                            style = typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
            } else {
                val details = player ?: return@GlassBloomBackground
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(
                        start = dimens.screenHorizontal,
                        end = dimens.screenHorizontal,
                        top = dimens.sm,
                        bottom = dimens.xxl
                    ),
                    verticalArrangement = Arrangement.spacedBy(dimens.md)
                ) {
                    item { PlayerHeaderCard(player = details) }
                    item { PlayerQuickInfo(player = details) }

                    if (details.battingStats != null) {
                        item {
                            SectionTitle(
                                title = "Batting statistics",
                                icon = Icons.Default.SportsCricket
                            )
                        }
                        item { BattingStatsCard(stats = details.battingStats) }
                    }

                    item {
                        SectionTitle(
                            title = "Recent form",
                            icon = Icons.AutoMirrored.Filled.TrendingUp
                        )
                    }
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), useGoldBorder = false) {
                            FormIndicator(recentScores = listOf(45, 12, 78, 0, 102), showLabels = true)
                        }
                    }

                    if (details.bowlingStats != null) {
                        item {
                            SectionTitle(
                                title = "Bowling statistics",
                                icon = Icons.Default.Album
                            )
                        }
                        item { BowlingStatsCard(stats = details.bowlingStats) }
                    }

                    item {
                        SectionTitle(
                            title = "Performance overview",
                            icon = Icons.Default.Radar
                        )
                    }
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), useGoldBorder = false) {
                            val battingAvg = details.battingStats?.average?.toFloat() ?: 0f
                            val matches = details.battingStats?.matches ?: 0
                            val bowlingAvg = details.bowlingStats?.average?.toFloat()
                            PerformanceRadarChart(
                                battingSkill = (battingAvg / 60f * 100f).coerceIn(0f, 100f),
                                bowlingSkill = if (bowlingAvg != null) ((60f - bowlingAvg) / 60f * 100f).coerceIn(0f, 100f) else 0f,
                                fieldingSkill = 65f,
                                fitnessSkill = 70f,
                                experienceSkill = (matches / 100f * 100f).coerceIn(0f, 100f),
                                playerName = details.name,
                                primaryColor = colors.goldPrimary
                            )
                        }
                    }

                    item {
                        SectionTitle(
                            title = "Achievements",
                            icon = Icons.Default.EmojiEvents
                        )
                    }
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth(), useGoldBorder = false) {
                            AchievementBadges(
                                achievements = listOf(
                                    PlayerAchievement("Century King", "Scored 8 centuries", Icons.Default.EmojiEvents, true, AchievementTier.GOLD),
                                    PlayerAchievement("Run Machine", "3000+ career runs", Icons.AutoMirrored.Filled.TrendingUp, true, AchievementTier.PLATINUM),
                                    PlayerAchievement("Boundary Master", "300+ fours", Icons.Default.SportsCricket, true, AchievementTier.SILVER)
                                )
                            )
                        }
                    }

                    item { PlayerFooter() }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, icon: ImageVector) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.xs)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.goldPrimary,
            modifier = Modifier.size(dimens.sm + dimens.xs)
        )
        Text(
            text = title,
            style = typography.headingSmall,
            color = colors.textPrimary
        )
    }
}

@Composable
private fun PlayerHeaderCard(player: PlayerDetails) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val context = LocalContext.current

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimens.radius.card),
        useGoldBorder = true,
        contentPadding = PaddingValues(dimens.md)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimens.sm)
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                if (!player.imageUrl.isNullOrEmpty()) {
                    CoilImage(
                        model = ImageRequest.Builder(context)
                            .data(player.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = player.name,
                        modifier = Modifier
                            .size(dimens.xxxl)
                            .clip(CircleShape)
                            .border(1.dp, colors.glassBorder, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(dimens.xxxl)
                            .background(colors.surface1, CircleShape)
                            .border(1.dp, colors.glassBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(dimens.lg)
                        )
                    }
                }

                val badge = when {
                    player.isCaptain -> "C"
                    player.isWicketKeeper -> "WK"
                    else -> null
                }
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .background(colors.goldPrimary, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            style = typography.labelSmall,
                            color = colors.backgroundBase,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = player.name,
                style = typography.headingLarge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = player.role,
                style = typography.bodyMedium,
                color = colors.textSecondary
            )
            player.country?.let {
                Text(
                    text = it,
                    style = typography.bodySmall,
                    color = colors.textMuted
                )
            }
        }
    }
}

@Composable
private fun PlayerQuickInfo(player: PlayerDetails) {
    val dims = BouncerDesignSystem.dimens

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        useGoldBorder = false,
        contentPadding = PaddingValues(dims.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            QuickInfoItem(label = "Age", value = player.age?.toString() ?: "-")
            QuickInfoItem(label = "Batting", value = player.battingStyle ?: "-")
            if (player.bowlingStyle != null) {
                QuickInfoItem(label = "Bowling", value = player.bowlingStyle)
            }
        }
    }
}

@Composable
private fun QuickInfoItem(label: String, value: String) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = typography.bodyMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = typography.bodySmall,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun BattingStatsCard(stats: BattingStats) {
    StatsGridCard(
        rows = listOf(
            listOf("Matches" to stats.matches.toString(), "Runs" to stats.runs.toString(), "HS" to stats.highScore),
            listOf("Average" to format(stats.average), "SR" to format(stats.strikeRate), "100s" to stats.hundreds.toString()),
            listOf("50s" to stats.fifties.toString(), "4s" to stats.fours.toString(), "6s" to stats.sixes.toString())
        )
    )
}

@Composable
private fun BowlingStatsCard(stats: BowlingStats) {
    StatsGridCard(
        rows = listOf(
            listOf("Matches" to stats.matches.toString(), "Wickets" to stats.wickets.toString(), "Best" to stats.bestBowling),
            listOf("Average" to format(stats.average), "Economy" to format(stats.economy), "SR" to format(stats.strikeRate)),
            listOf("Overs" to format(stats.overs), "5W" to stats.fiveWickets.toString(), "10W" to stats.tenWickets.toString())
        )
    )
}

@Composable
private fun StatsGridCard(rows: List<List<Pair<String, String>>>) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        useGoldBorder = false,
        contentPadding = PaddingValues(dimens.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.sm)) {
            rows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    row.forEach { (label, value) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = value,
                                style = typography.bodyMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = label,
                                style = typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
                if (rowIndex < rows.lastIndex) {
                    HorizontalDivider(color = colors.glassBorder, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun PlayerFooter() {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.xxs)
    ) {
        Text(
            text = "Stats powered by Bouncer",
            style = typography.bodySmall,
            color = colors.textMuted
        )
    }
}

private fun format(value: Double): String = String.format(Locale.US, "%.1f", value)
