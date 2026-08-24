package com.tanush.bouncer.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.tanush.bouncer.ui.components.v2.ShimmerBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanush.bouncer.network.PlayerProfileScraper
import com.tanush.bouncer.viewmodels.PlayerComparisonViewModel
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanush.bouncer.models.FormatStats
import com.tanush.bouncer.models.PlayerProfile
import com.tanush.bouncer.network.PlayerSearchResult
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.FilterChipRowV2
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerComparisonScreen(
    onBackClick: () -> Unit,
    viewModel: PlayerComparisonViewModel = viewModel(),
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val formats = remember { listOf("Test", "ODI", "T20I", "IPL") }
    var selectedFormat by remember { mutableStateOf("Test") }

    val player1 by viewModel.player1.collectAsStateWithLifecycle()
    val player2 by viewModel.player2.collectAsStateWithLifecycle()
    val loadingPlayer1 by viewModel.loadingPlayer1.collectAsStateWithLifecycle()
    val loadingPlayer2 by viewModel.loadingPlayer2.collectAsStateWithLifecycle()

    // Which picker is active (0 = none, 1 = player1, 2 = player2)
    var activePickerSlot by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            BouncerTopBarV2(
                title = "Compare",
                showBack = true,
                onBack = onBackClick,
            )
        },
        containerColor = colors.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            playerPickerSection(
                player1 = player1,
                player2 = player2,
                loadingPlayer1 = loadingPlayer1,
                loadingPlayer2 = loadingPlayer2,
                activePickerSlot = activePickerSlot,
                onSlotChange = { activePickerSlot = it },
                onClearPlayer = { viewModel.clearPlayer(it) },
                onPlayerSelected = { result ->
                    val slot = activePickerSlot
                    activePickerSlot = 0
                    viewModel.setPlayer(
                        slot = slot,
                        stub = PlayerProfile(
                            id = result.id, name = result.name,
                            country = result.country, role = "",
                        ),
                    )
                },
            )

            // Format selector + stat comparison (when both players are loaded)
            val p1 = player1
            val p2 = player2
            if (p1 != null && p2 != null && !loadingPlayer1 && !loadingPlayer2) {
                comparisonStatsSection(p1, p2, formats, selectedFormat) { selectedFormat = it }
            }
        }
    }

}

private fun LazyListScope.playerPickerSection(
    player1: PlayerProfile?,
    player2: PlayerProfile?,
    loadingPlayer1: Boolean,
    loadingPlayer2: Boolean,
    activePickerSlot: Int,
    onSlotChange: (Int) -> Unit,
    onClearPlayer: (Int) -> Unit,
    onPlayerSelected: (PlayerSearchResult) -> Unit,
) {
    item(key = "pickers") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BouncerThemeV2.spacing.md),
        ) {
            PlayerPickerCard(
                player = player1,
                loading = loadingPlayer1,
                label = "Player 1",
                isActive = activePickerSlot == 1,
                onTap = { onSlotChange(if (activePickerSlot == 1) 0 else 1) },
                onClear = { onClearPlayer(1) },
                modifier = Modifier.weight(1f),
            )
            PlayerPickerCard(
                player = player2,
                loading = loadingPlayer2,
                label = "Player 2",
                isActive = activePickerSlot == 2,
                onTap = { onSlotChange(if (activePickerSlot == 2) 0 else 2) },
                onClear = { onClearPlayer(2) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (activePickerSlot != 0) {
        item(key = "search") {
            PlayerSearchBox(onPlayerSelected = onPlayerSelected)
        }
    }
}

private fun LazyListScope.comparisonStatsSection(
    player1: PlayerProfile,
    player2: PlayerProfile,
    formats: List<String>,
    selectedFormat: String,
    onFormatSelected: (String) -> Unit,
) {
    item(key = "format") {
        FilterChipRowV2(
            items = formats,
            selectedItem = selectedFormat,
            onItemSelected = onFormatSelected,
        )
    }

    val stats1 = player1.stats[selectedFormat]
    val stats2 = player2.stats[selectedFormat]

    if (stats1 != null && stats2 != null) {
        item(key = "comparison_header") {
            SectionHeaderV2("$selectedFormat Comparison")
        }

        val comparisonRows = buildComparisonRows(stats1, stats2)

        items(comparisonRows, key = { it.label }) { row ->
            ComparisonRow(
                label = row.label,
                value1 = row.value1,
                value2 = row.value2,
                highlight1 = row.highlight1,
            )
        }
    } else {
        item(key = "no_stats") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(BouncerThemeV2.spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No $selectedFormat stats available for comparison",
                    style = BouncerThemeV2.typography.bodyMedium,
                    color = BouncerThemeV2.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── Player Picker Card ──────────────────────────────────────────────────────────

@Composable
private fun PlayerPickerCard(
    player: PlayerProfile?,
    loading: Boolean,
    label: String,
    isActive: Boolean,
    onTap: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Card(
        modifier = modifier.clickable { onTap() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) colors.primaryContainer else colors.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = BouncerThemeV2.elevation.card),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (loading) {
                ShimmerBox(modifier = Modifier.width(40.dp), height = 40.dp)
                Spacer(Modifier.height(spacing.sm))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f), height = 12.dp)
            } else if (player != null && player.stats.isNotEmpty()) {
                // Player loaded with stats
                val imgUrl = player.imageUrl
                if (imgUrl != null) {
                    AsyncImage(
                        model = imgUrl, contentDescription = player.name,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                    )
                } else {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(colors.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Person, label, Modifier.size(20.dp), tint = colors.textSecondary)
                    }
                }
                Spacer(Modifier.height(spacing.xs))
                Text(
                    player.name, style = typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = colors.onSurface,
                    textAlign = TextAlign.Center, maxLines = 1,
                )
                Text(player.country, style = typography.labelSmall, color = colors.textSecondary)
                // Clear button
                Spacer(Modifier.height(spacing.xs))
                // 48dp touch target overflowing the 24dp layout slot.
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp).requiredSize(48.dp)) {
                    Icon(Icons.Default.Close, "Clear", Modifier.size(14.dp), tint = colors.textSecondary)
                }
            } else if (player != null) {
                // Player selected but still placeholder (no stats yet / loading)
                Text(player.name, style = typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text("Tap to search", style = typography.labelSmall, color = colors.textSecondary)
            } else {
                // Empty slot
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(colors.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Search, label, Modifier.size(20.dp), tint = colors.textSecondary)
                }
                Spacer(Modifier.height(spacing.xs))
                Text(label, style = typography.labelMedium, color = colors.textSecondary)
                Text("Tap to search", style = typography.labelSmall, color = colors.textSecondary)
            }
        }
    }
}

// ── Player Search Box ───────────────────────────────────────────────────────────

@Composable
private fun PlayerSearchBox(
    onPlayerSelected: (PlayerSearchResult) -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlayerSearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    // Debounced search
    LaunchedEffect(query) {
        if (query.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        searching = true
        delay(400) // debounce
        results = PlayerProfileScraper.searchPlayers(query)
        searching = false
    }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search player…", style = typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Default.Search, "Search", tint = colors.textSecondary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.border,
                cursorColor = colors.primary,
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface,
                focusedPlaceholderColor = colors.textSecondary,
                unfocusedPlaceholderColor = colors.textSecondary,
            ),
            shape = RoundedCornerShape(12.dp),
        )

        if (searching) {
            Box(Modifier.fillMaxWidth().padding(spacing.lg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colors.primary, strokeWidth = 2.dp)
            }
        }

        if (results.isNotEmpty()) {
            Spacer(Modifier.height(spacing.sm))
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = BouncerThemeV2.elevation.card),
            ) {
                Column {
                    results.forEachIndexed { index, result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayerSelected(result) }
                                .padding(horizontal = spacing.lg, vertical = spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(colors.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Person, result.name, Modifier.size(16.dp), tint = colors.textSecondary)
                            }
                            Spacer(Modifier.width(spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(result.name, style = typography.bodyMedium, fontWeight = FontWeight.Medium, color = colors.onSurface)
                                if (result.country.isNotBlank()) {
                                    Text(result.country, style = typography.labelSmall, color = colors.textSecondary)
                                }
                            }
                        }
                        if (index < results.lastIndex) {
                            HorizontalDivider(color = colors.divider)
                        }
                    }
                }
            }
        }
    }
}

// ── Comparison Row ──────────────────────────────────────────────────────────────

private data class ComparisonData(
    val label: String,
    val value1: String,
    val value2: String,
    val highlight1: Boolean, // true = player1 is better, false = player2 is better
)

@Composable
private fun ComparisonRow(
    label: String,
    value1: String,
    value2: String,
    highlight1: Boolean,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = BouncerThemeV2.elevation.card),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Player 1 value
            Text(
                text = value1,
                style = typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlight1) colors.primary else colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )

            // Stat label
            Text(
                text = label,
                style = typography.labelMedium,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )

            // Player 2 value
            Text(
                text = value2,
                style = typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (!highlight1) colors.primary else colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ── Build comparison data ───────────────────────────────────────────────────────

private fun buildComparisonRows(s1: FormatStats, s2: FormatStats): List<ComparisonData> {
    fun higherIsBetter(label: String, v1: Number, v2: Number): ComparisonData {
        val d1 = v1.toDouble()
        val d2 = v2.toDouble()
        val str1 = formatStat(v1)
        val str2 = formatStat(v2)
        return ComparisonData(label, str1, str2, highlight1 = d1 >= d2)
    }

    fun lowerIsBetter(label: String, v1: Number, v2: Number): ComparisonData {
        val d1 = v1.toDouble()
        val d2 = v2.toDouble()
        val str1 = formatStat(v1)
        val str2 = formatStat(v2)
        // lower is better, but 0 means no data so treat as worse
        val better1 = if (d1 == 0.0 && d2 > 0.0) false
        else if (d2 == 0.0 && d1 > 0.0) true
        else d1 <= d2
        return ComparisonData(label, str1, str2, highlight1 = better1)
    }

    return listOf(
        higherIsBetter("Matches", s1.matches, s2.matches),
        higherIsBetter("Innings", s1.innings, s2.innings),
        higherIsBetter("Runs", s1.runs, s2.runs),
        higherIsBetter("Average", s1.average, s2.average),
        higherIsBetter("Strike Rate", s1.strikeRate, s2.strikeRate),
        higherIsBetter("100s", s1.hundreds, s2.hundreds),
        higherIsBetter("50s", s1.fifties, s2.fifties),
        higherIsBetter("Wickets", s1.wickets, s2.wickets),
        lowerIsBetter("Bowl Avg", s1.bowlingAverage, s2.bowlingAverage),
        lowerIsBetter("Economy", s1.economy, s2.economy),
    )
}

private fun formatStat(value: Number): String {
    return when (value) {
        is Int -> value.toString()
        is Double -> if (value == 0.0) "0" else String.format(Locale.US, "%.1f", value)
        else -> value.toString()
    }
}
