package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

private data class PlayerDetailsV2(
    val name: String, val role: String, val imageUrl: String?, val country: String,
    val age: Int, val battingStyle: String, val bowlingStyle: String,
    val isCaptain: Boolean, val isWicketKeeper: Boolean,
    val battingStats: BattingStatsV2, val bowlingStats: BowlingStatsV2?,
)

private data class BattingStatsV2(
    val matches: Int, val innings: Int, val runs: Int, val highScore: String,
    val average: Double, val strikeRate: Double, val hundreds: Int, val fifties: Int,
    val fours: Int, val sixes: Int,
)

private data class BowlingStatsV2(
    val matches: Int, val wickets: Int, val bestBowling: String,
    val average: Double, val economy: Double, val fiveWickets: Int,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerDetailScreenV2(
    playerName: String,
    playerRole: String,
    playerImageUrl: String?,
    isCaptain: Boolean = false,
    isWicketKeeper: Boolean = false,
    onBackClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    var player by remember { mutableStateOf<PlayerDetailsV2?>(null) }
    LaunchedEffect(playerName) {
        player = generateMockPlayer(playerName, playerRole, playerImageUrl, isCaptain, isWicketKeeper)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Player", style = typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.surface,
                    titleContentColor = colors.onSurface,
                    navigationIconContentColor = colors.onSurface,
                ),
            )
        },
        containerColor = colors.background,
    ) { padding ->
        val p = player
        if (p == null) return@Scaffold

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            // Profile header
            item(key = "header") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = BouncerThemeV2.elevation.card),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (p.imageUrl != null) {
                            AsyncImage(
                                model = p.imageUrl, contentDescription = p.name,
                                modifier = Modifier.size(72.dp).clip(CircleShape),
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(72.dp).clip(CircleShape).background(colors.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Person, "Player", Modifier.size(36.dp), tint = colors.textSecondary)
                            }
                        }
                        Spacer(Modifier.height(spacing.md))
                        Text(p.name, style = typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
                        Text(p.role, style = typography.bodyMedium, color = colors.textSecondary)
                        Spacer(Modifier.height(spacing.sm))
                        Row(horizontalArrangement = Arrangement.Center) {
                            Text(p.country, style = typography.bodySmall, color = colors.textSecondary)
                            if (p.isCaptain) {
                                Spacer(Modifier.width(spacing.sm))
                                PillBadge("C")
                            }
                            if (p.isWicketKeeper) {
                                Spacer(Modifier.width(spacing.sm))
                                PillBadge("WK")
                            }
                        }
                    }
                }
            }

            // Quick info
            item(key = "quick_info") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    InfoPill("Age: ${p.age}")
                    InfoPill(p.battingStyle)
                    if (p.bowlingStyle.isNotBlank()) InfoPill(p.bowlingStyle)
                }
            }

            // Batting stats
            item(key = "batting") {
                SectionHeaderV2("Batting Career")
                Spacer(Modifier.height(spacing.sm))
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = BouncerThemeV2.elevation.card),
                ) {
                    Column(Modifier.padding(spacing.lg)) {
                        StatGrid(
                            listOf(
                                "Matches" to "${p.battingStats.matches}",
                                "Innings" to "${p.battingStats.innings}",
                                "Runs" to "${p.battingStats.runs}",
                                "HS" to p.battingStats.highScore,
                                "Avg" to "%.1f".format(p.battingStats.average),
                                "SR" to "%.1f".format(p.battingStats.strikeRate),
                                "100s" to "${p.battingStats.hundreds}",
                                "50s" to "${p.battingStats.fifties}",
                                "4s" to "${p.battingStats.fours}",
                                "6s" to "${p.battingStats.sixes}",
                            ),
                        )
                    }
                }
            }

            // Bowling stats
            val bs = p.bowlingStats
            if (bs != null) {
                item(key = "bowling") {
                    SectionHeaderV2("Bowling Career")
                    Spacer(Modifier.height(spacing.sm))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = BouncerThemeV2.elevation.card),
                    ) {
                        Column(Modifier.padding(spacing.lg)) {
                            StatGrid(
                                listOf(
                                    "Matches" to "${bs.matches}",
                                    "Wickets" to "${bs.wickets}",
                                    "Best" to bs.bestBowling,
                                    "Avg" to "%.1f".format(bs.average),
                                    "Econ" to "%.1f".format(bs.economy),
                                    "5W" to "${bs.fiveWickets}",
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatGrid(items: List<Pair<String, String>>) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val rows = items.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (label, value) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(value, style = typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
                        Text(label, style = typography.labelSmall, color = colors.textSecondary)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PillBadge(text: String) {
    val colors = BouncerThemeV2.colors
    Text(
        text = text,
        style = BouncerThemeV2.typography.labelSmall,
        color = colors.onPrimary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(colors.primary, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun InfoPill(text: String) {
    val colors = BouncerThemeV2.colors
    Text(
        text = text,
        style = BouncerThemeV2.typography.labelMedium,
        color = colors.onSurface,
        modifier = Modifier
            .background(colors.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private fun generateMockPlayer(
    name: String, role: String, imageUrl: String?,
    isCaptain: Boolean, isWicketKeeper: Boolean,
): PlayerDetailsV2 {
    val hash = name.hashCode().toLong().let { kotlin.math.abs(it) }
    return PlayerDetailsV2(
        name = name, role = role, imageUrl = imageUrl,
        country = "India", age = (25 + (hash % 15)).toInt(),
        battingStyle = if (hash % 2 == 0L) "Right Hand Bat" else "Left Hand Bat",
        bowlingStyle = when {
            role.contains("Bowl", true) -> if (hash % 2 == 0L) "Right Arm Fast" else "Left Arm Spin"
            role.contains("All", true) -> "Right Arm Medium"
            else -> ""
        },
        isCaptain = isCaptain, isWicketKeeper = isWicketKeeper,
        battingStats = BattingStatsV2(
            matches = (50 + (hash % 200)).toInt(), innings = (40 + (hash % 180)).toInt(),
            runs = (1000 + (hash % 10000)).toInt(), highScore = "${100 + (hash % 150)}",
            average = 30.0 + (hash % 30), strikeRate = 60.0 + (hash % 80),
            hundreds = (hash % 30).toInt(), fifties = (hash % 50).toInt(),
            fours = (100 + hash % 500).toInt(), sixes = (20 + hash % 100).toInt(),
        ),
        bowlingStats = if (role.contains("Bat", true) && !role.contains("All", true)) null
        else BowlingStatsV2(
            matches = (50 + (hash % 200)).toInt(), wickets = (10 + (hash % 200)).toInt(),
            bestBowling = "${3 + (hash % 5)}/${20 + (hash % 40)}",
            average = 25.0 + (hash % 20), economy = 3.0 + (hash % 5),
            fiveWickets = (hash % 8).toInt(),
        ),
    )
}
