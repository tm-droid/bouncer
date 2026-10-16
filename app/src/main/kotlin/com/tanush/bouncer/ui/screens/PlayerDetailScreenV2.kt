package com.tanush.bouncer.ui.screens

import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import android.content.Context
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

private val PROFILE_FORMATS = listOf("Test", "ODI", "T20I", "IPL")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailScreenV2(
    playerName: String,
    playerRole: String,
    playerImageUrl: String?,
    isCaptain: Boolean = false,
    isWicketKeeper: Boolean = false,
    onBackClick: () -> Unit,
    onCompareClick: (() -> Unit)? = null,
    /** Cricbuzz profile id when the caller has one (search does); opens that exact player. */
    cricbuzzId: String? = null,
) {
    val colors = BouncerThemeV2.colors
    val context = LocalContext.current

    var selectedFormat by rememberSaveable { mutableStateOf("Test") }
    var profile by remember { mutableStateOf<PlayerProfile?>(null) }
    var loadState by remember { mutableStateOf(LoadState.Loading) }
    var retryCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(playerName, cricbuzzId, retryCount) {
        loadState = LoadState.Loading
        profile = null
        loadPlayerProfile(context, playerName, cricbuzzId) { loaded ->
            profile = loaded
            // Open on the first format that has stats.
            PROFILE_FORMATS.firstOrNull { loaded.stats.containsKey(it) }?.let { selectedFormat = it }
            loadState = LoadState.Success
        }
        if (profile == null) loadState = LoadState.Error
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
        val loaded = profile
        when {
            loadState == LoadState.Loading -> PlayerLoading(padding)
            loaded == null -> PlayerLoadError(padding, playerName) { retryCount++ }
            else -> PlayerProfileList(
                padding = padding,
                profile = loaded,
                header = PlayerHeaderInfo(playerRole, playerImageUrl, isCaptain, isWicketKeeper),
                selectedFormat = selectedFormat,
                onFormatSelected = { selectedFormat = it },
            )
        }
    }
}

/**
 * Room, then the shared Appwrite table, then Cricbuzz — the order `PlayerCacheWorker` already uses.
 * This screen used to scrape unconditionally and read neither cache, so it disagreed with search
 * (which answers from Room) about the same player and spent a page load on every open of a profile
 * some other device had already scraped.
 *
 * [onLoaded] runs as soon as a profile exists; a scrape then warms the global player-profiles cache
 * (Room + Appwrite) rather than leaving it to a worker that would search and scrape the same player
 * again. A cache failure must not fail the screen that already has its profile.
 */
private suspend fun loadPlayerProfile(
    context: Context,
    playerName: String,
    cricbuzzId: String?,
    onLoaded: (PlayerProfile) -> Unit,
) {
    PlayerCacheWorker.cachedProfile(context, playerName, cricbuzzId)?.let { return onLoaded(it) }
    val scraped = PlayerProfileScraper.scrapeProfile(playerName, cricbuzzId) ?: return
    onLoaded(scraped)
    try {
        PlayerCacheWorker.storeProfile(
            context,
            PlayerCacheWorker.cacheKeyFor(playerName, cricbuzzId),
            scraped,
            teamName = scraped.country,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Sentry.captureException(e)
    }
}

@Composable
private fun PlayerLoading(padding: PaddingValues) {
    val spacing = BouncerThemeV2.spacing
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

/** Honest failure state — no placeholder stats masquerading as real data. */
@Composable
private fun PlayerLoadError(padding: PaddingValues, playerName: String, onRetry: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing
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
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ),
        ) {
            Icon(Icons.Default.Refresh, "Retry", Modifier.size(16.dp))
            Spacer(Modifier.width(spacing.xs))
            Text(stringResource(R.string.player_retry), style = BouncerThemeV2.typography.labelMedium)
        }
    }
}

/** What the caller already knew about the player, used where the profile is silent. */
private class PlayerHeaderInfo(
    val role: String,
    val imageUrl: String?,
    val isCaptain: Boolean,
    val isWicketKeeper: Boolean,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerProfileList(
    padding: PaddingValues,
    profile: PlayerProfile,
    header: PlayerHeaderInfo,
    selectedFormat: String,
    onFormatSelected: (String) -> Unit,
) {
    val spacing = BouncerThemeV2.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item(key = "header") { PlayerHeaderCard(profile, header) }

        item(key = "quick_info") {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                if (profile.dateOfBirth.isNotBlank()) InfoPill("DOB: ${profile.dateOfBirth}")
                if (profile.battingStyle.isNotBlank()) InfoPill(profile.battingStyle)
                if (profile.bowlingStyle.isNotBlank()) InfoPill(profile.bowlingStyle)
            }
        }

        item(key = "format_selector") {
            FilterChipRowV2(
                items = PROFILE_FORMATS,
                selectedItem = selectedFormat,
                onItemSelected = onFormatSelected,
            )
        }

        formatStatsItems(profile.stats[selectedFormat], selectedFormat)

        // Recent form card — only shown when batting form data was scraped
        buildBatterForm(profile)?.let { batterForm ->
            item(key = "form_card") {
                PlayerFormCard(batters = listOf(batterForm), bowlers = emptyList())
            }
        }
    }
}

@Composable
private fun PlayerHeaderCard(p: PlayerProfile, header: PlayerHeaderInfo) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = BouncerThemeV2.elevation.card),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val imgUrl = p.imageUrl ?: header.imageUrl
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
            Text(p.role.ifEmpty { header.role }, style = typography.bodyMedium, color = colors.textSecondary)
            Spacer(Modifier.height(spacing.sm))
            Row(horizontalArrangement = Arrangement.Center) {
                Text(p.country, style = typography.bodySmall, color = colors.textSecondary)
                if (header.isCaptain) {
                    Spacer(Modifier.width(spacing.sm))
                    PillBadge("C")
                }
                if (header.isWicketKeeper) {
                    Spacer(Modifier.width(spacing.sm))
                    PillBadge("WK")
                }
            }
        }
    }
}

private fun LazyListScope.formatStatsItems(stats: FormatStats?, format: String) {
    if (stats == null) {
        item(key = "no_stats") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(BouncerThemeV2.spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No $format stats available",
                    style = BouncerThemeV2.typography.bodyMedium,
                    color = BouncerThemeV2.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }
    item(key = "batting") {
        StatsCard(
            "Batting — $format",
            listOf(
                "Matches" to "${stats.matches}",
                "Innings" to "${stats.innings}",
                "Runs" to "${stats.runs}",
                "HS" to stats.highScore,
                "Avg" to String.format(Locale.US, "%.1f", stats.average),
                "SR" to String.format(Locale.US, "%.1f", stats.strikeRate),
                "100s" to "${stats.hundreds}",
                "50s" to "${stats.fifties}",
                "4s" to "${stats.fours}",
                "6s" to "${stats.sixes}",
            ),
        )
    }
    if (stats.wickets > 0 || stats.bowlingAverage > 0) {
        item(key = "bowling") {
            StatsCard(
                "Bowling — $format",
                listOf(
                    "Wickets" to "${stats.wickets}",
                    "Best" to stats.bestBowling,
                    "Avg" to String.format(Locale.US, "%.1f", stats.bowlingAverage),
                    "Econ" to String.format(Locale.US, "%.1f", stats.economy),
                    "5W" to "${stats.fiveWickets}",
                    "Ct" to "${stats.catches}",
                ),
            )
        }
    }
}

@Composable
private fun StatsCard(title: String, items: List<Pair<String, String>>) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    SectionHeaderV2(title)
    Spacer(Modifier.height(spacing.sm))
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = BouncerThemeV2.elevation.card),
    ) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            items.chunked(4).forEach { row ->
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
}

// ── Shared composables ──────────────────────────────────────────────────────────

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
