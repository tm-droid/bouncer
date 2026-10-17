package com.tanush.bouncer.ui.screens

import com.tanush.bouncer.ui.components.v2.TextTabBarV2
import com.tanush.bouncer.ui.screens.player.formatsWithStats
import com.tanush.bouncer.ui.screens.player.formatStatItems
import com.tanush.bouncer.ui.screens.player.playerFactItems
import com.tanush.bouncer.ui.screens.player.PlayerHeader
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
    val formats = formatsWithStats(profile, PROFILE_FORMATS)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = spacing.xl),
    ) {
        item(key = "header") {
            Column(Modifier.padding(spacing.lg)) {
                PlayerHeader(
                    name = profile.name,
                    country = profile.country,
                    role = profile.role.ifEmpty { header.role },
                    imageUrl = profile.imageUrl ?: header.imageUrl,
                    isCaptain = header.isCaptain,
                    isKeeper = header.isWicketKeeper,
                )
            }
        }
        playerFactItems(profile)
        // Only the formats this player has figures for; a tab that leads to "no stats" is a dead end.
        if (formats.size > 1) {
            item(key = "format_selector") {
                TextTabBarV2(
                    tabs = formats,
                    selectedIndex = formats.indexOf(selectedFormat).coerceAtLeast(0),
                    onSelect = { onFormatSelected(formats[it]) },
                )
            }
        }
        formatStatItems(profile.stats[selectedFormat], selectedFormat)

        // Recent form card — only shown when batting form data was scraped
        buildBatterForm(profile)?.let { batterForm ->
            item(key = "form_card") {
                Column(Modifier.padding(spacing.lg)) {
                    PlayerFormCard(batters = listOf(batterForm), bowlers = emptyList())
                }
            }
        }
    }
}
