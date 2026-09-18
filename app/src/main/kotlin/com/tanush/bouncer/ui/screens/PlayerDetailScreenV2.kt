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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanush.bouncer.R
import com.tanush.bouncer.models.FormatStats
import com.tanush.bouncer.models.PlayerProfile
import com.tanush.bouncer.network.PlayerProfileScraper
import com.tanush.bouncer.services.PlayerCacheWorker
import com.tanush.bouncer.ui.components.premium.PlayerFormCard
import com.tanush.bouncer.ui.screens.player.buildBatterForm
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.FilterChipRowV2
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.ShimmerPlayerHeader
import com.tanush.bouncer.ui.components.v2.ShimmerStatGrid
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import java.util.Locale
import kotlinx.coroutines.CancellationException
import io.sentry.Sentry

private enum class LoadState { Loading, Success, Error }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerDetailScreenV2(
    playerName: String,
    playerRole: String,
    playerImageUrl: String?,
    isCaptain: Boolean = false,
    isWicketKeeper: Boolean = false,
    onBackClick: () -> Unit,
    onCompareClick: (() -> Unit)? = null,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val context = LocalContext.current

    val formats = remember { listOf("Test", "ODI", "T20I", "IPL") }
    var selectedFormat by remember { mutableStateOf("Test") }

    var profile by remember { mutableStateOf<PlayerProfile?>(null) }
    var loadState by remember { mutableStateOf(LoadState.Loading) }
    var retryCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(playerName, retryCount) {
        loadState = LoadState.Loading
        // Room, then the shared Appwrite table, then Cricbuzz — the order `PlayerCacheWorker`
        // already uses. This screen used to scrape unconditionally and read neither cache, so it
        // disagreed with search (which answers from Room) about the same player and spent a page
        // load on every open of a profile some other device had already scraped.
        val cached = PlayerCacheWorker.cachedProfile(context, playerName)
        if (cached != null) {
            profile = cached
            loadState = LoadState.Success
            formats.firstOrNull { cached.stats.containsKey(it) }?.let { selectedFormat = it }
            return@LaunchedEffect
        }
        val scraped = PlayerProfileScraper.scrapeProfile(playerName)
        if (scraped != null) {
            profile = scraped
            loadState = LoadState.Success
            // Warm the global player-profiles cache (Room + Appwrite) from this scrape rather than a
            // worker that would search and scrape the same player again.
            // A cache failure must not fail the screen that already has its profile.
            try {
                PlayerCacheWorker.storeProfile(
                    context,
                    PlayerCacheWorker.playerIdFor(playerName),
                    scraped,
                    teamName = scraped.country,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Sentry.captureException(e)
            }
            // Auto-select first format that has stats
            val firstAvailable = formats.firstOrNull { scraped.stats.containsKey(it) }
            if (firstAvailable != null) selectedFormat = firstAvailable
        } else {
            loadState = LoadState.Error
        }
    }

    Scaffold(
        topBar = {
            BouncerTopBarV2(
                title = stringResource(R.string.player_player),
                showBack = true,
                onBack = onBackClick,
                actions = {
                    if (onCompareClick != null) {
                        IconButton(onClick = onCompareClick) {
                            Icon(Icons.Default.SwapHoriz, "Compare", tint = colors.textSecondary)
                        }
                    }
                }
            )
        },
        containerColor = colors.background,
    ) { padding ->
        when (loadState) {
            LoadState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.lg),
                ) {
                    ShimmerPlayerHeader()
                    ShimmerStatGrid()
                    ShimmerStatGrid(rows = 3)
                }
            }

            LoadState.Error -> {
                // Honest failure state — no placeholder stats masquerading as real data.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    EmptyStateV2(
                        icon = Icons.Default.Person,
                        title = "Couldn't load $playerName",
                        subtitle = stringResource(R.string.player_the_profile_isn_t_available_right),
                    )
                    Button(
                        onClick = { retryCount++ },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Default.Refresh, "Retry", Modifier.size(16.dp))
                        Spacer(Modifier.width(spacing.xs))
                        Text(stringResource(R.string.player_retry), style = typography.labelMedium)
                    }
                }
            }

            LoadState.Success -> {
                val p = profile ?: return@Scaffold
                val currentStats = p.stats[selectedFormat]

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
                                val imgUrl = p.imageUrl ?: playerImageUrl
                                if (imgUrl != null) {
                                    AsyncImage(
                                        model = imgUrl, contentDescription = p.name,
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
                                Text(p.role.ifEmpty { playerRole }, style = typography.bodyMedium, color = colors.textSecondary)
                                Spacer(Modifier.height(spacing.sm))
                                Row(horizontalArrangement = Arrangement.Center) {
                                    Text(p.country, style = typography.bodySmall, color = colors.textSecondary)
                                    if (isCaptain) {
                                        Spacer(Modifier.width(spacing.sm))
                                        PillBadge("C")
                                    }
                                    if (isWicketKeeper) {
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
                            if (p.dateOfBirth.isNotBlank()) InfoPill("DOB: ${p.dateOfBirth}")
                            if (p.battingStyle.isNotBlank()) InfoPill(p.battingStyle)
                            if (p.bowlingStyle.isNotBlank()) InfoPill(p.bowlingStyle)
                        }
                    }

                    // Format selector
                    item(key = "format_selector") {
                        FilterChipRowV2(
                            items = formats,
                            selectedItem = selectedFormat,
                            onItemSelected = { selectedFormat = it },
                        )
                    }

                    if (currentStats != null) {
                        // Batting stats
                        item(key = "batting") {
                            SectionHeaderV2("Batting — $selectedFormat")
                            Spacer(Modifier.height(spacing.sm))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = colors.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = BouncerThemeV2.elevation.card),
                            ) {
                                Column(Modifier.padding(spacing.lg)) {
                                    StatGrid(
                                        listOf(
                                            "Matches" to "${currentStats.matches}",
                                            "Innings" to "${currentStats.innings}",
                                            "Runs" to "${currentStats.runs}",
                                            "HS" to currentStats.highScore,
                                            "Avg" to String.format(Locale.US, "%.1f", currentStats.average),
                                            "SR" to String.format(Locale.US, "%.1f", currentStats.strikeRate),
                                            "100s" to "${currentStats.hundreds}",
                                            "50s" to "${currentStats.fifties}",
                                            "4s" to "${currentStats.fours}",
                                            "6s" to "${currentStats.sixes}",
                                        ),
                                    )
                                }
                            }
                        }

                        // Bowling stats
                        if (currentStats.wickets > 0 || currentStats.bowlingAverage > 0) {
                            item(key = "bowling") {
                                SectionHeaderV2("Bowling — $selectedFormat")
                                Spacer(Modifier.height(spacing.sm))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = BouncerThemeV2.elevation.card),
                                ) {
                                    Column(Modifier.padding(spacing.lg)) {
                                        StatGrid(
                                            listOf(
                                                "Wickets" to "${currentStats.wickets}",
                                                "Best" to currentStats.bestBowling,
                                                "Avg" to String.format(Locale.US, "%.1f", currentStats.bowlingAverage),
                                                "Econ" to String.format(Locale.US, "%.1f", currentStats.economy),
                                                "5W" to "${currentStats.fiveWickets}",
                                                "Ct" to "${currentStats.catches}",
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        item(key = "no_stats") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(spacing.xl),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "No $selectedFormat stats available",
                                    style = typography.bodyMedium,
                                    color = colors.textSecondary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    // Recent form card — only shown when batting form data was scraped
                    val batterForm = buildBatterForm(p)
                    if (batterForm != null) {
                        item(key = "form_card") {
                            PlayerFormCard(
                                batters = listOf(batterForm),
                                bowlers = emptyList(),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Shared composables ──────────────────────────────────────────────────────────

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
