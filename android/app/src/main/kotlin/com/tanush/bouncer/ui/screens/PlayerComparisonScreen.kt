package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.BorderStroke
import com.tanush.bouncer.ui.components.v2.TeamCrest
import com.tanush.bouncer.ui.components.v2.EmptyStateV2
import com.tanush.bouncer.ui.components.v2.PersonAvatar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Color
import com.tanush.bouncer.ui.theme.rememberSegmentColors
import com.tanush.bouncer.ui.components.v2.TextTabBarV2
import com.tanush.bouncer.network.PlayerSearchOutcome
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
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
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.tanush.bouncer.R
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanush.bouncer.models.FormatStats
import com.tanush.bouncer.models.PlayerProfile
import com.tanush.bouncer.network.PlayerSearchResult
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerComparisonScreen(
    onBackClick: () -> Unit,
    firstPlayer: String? = null,
    viewModel: PlayerComparisonViewModel = viewModel(),
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    val formats = remember { listOf("Test", "ODI", "T20I", "IPL") }
    var selectedFormat by rememberSaveable { mutableStateOf("Test") }

    val player1 by viewModel.player1.collectAsStateWithLifecycle()
    val player2 by viewModel.player2.collectAsStateWithLifecycle()
    val loadingPlayer1 by viewModel.loadingPlayer1.collectAsStateWithLifecycle()
    val loadingPlayer2 by viewModel.loadingPlayer2.collectAsStateWithLifecycle()

    // Opened from a player's page: that player is the first of the two, not a blank slot to search.
    LaunchedEffect(firstPlayer) {
        if (!firstPlayer.isNullOrBlank() && viewModel.player1.value == null) {
            viewModel.setPlayer(1, PlayerProfile(id = "", name = firstPlayer, country = "", role = ""))
        }
    }

    // Which picker is active (0 = none, 1 = player1, 2 = player2)
    var activePickerSlot by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            BouncerTopBarV2(
                title = stringResource(R.string.player_compare),
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
                            country = result.country, role = "", imageUrl = result.imageUrl,
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
                label = stringResource(R.string.player_player_1),
                isActive = activePickerSlot == 1,
                onTap = { onSlotChange(if (activePickerSlot == 1) 0 else 1) },
                onClear = { onClearPlayer(1) },
                modifier = Modifier.weight(1f),
            )
            PlayerPickerCard(
                player = player2,
                loading = loadingPlayer2,
                label = stringResource(R.string.player_player_2),
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
        TextTabBarV2(
            tabs = formats,
            selectedIndex = formats.indexOf(selectedFormat).coerceAtLeast(0),
            onSelect = { onFormatSelected(formats[it]) },
        )
    }

    val stats1 = player1.stats[selectedFormat]
    val stats2 = player2.stats[selectedFormat]

    if (stats1 != null && stats2 != null) {
        item(key = "comparison_header") {
            SectionHeaderV2("$selectedFormat comparison")
        }

        val comparisonRows = buildComparisonRows(stats1, stats2)

        // One item: the list spaces its items 16dp apart, and these rows read as one table.
        item(key = "comparison_rows") {
            val (color1, color2) = rememberSegmentColors(
                player1.country,
                player2.country,
                BouncerThemeV2.colors.surface,
                COMPARISON_BAR_CONTRAST,
            )
            Column { comparisonRows.forEach { row -> ComparisonRow(row, color1, color2) } }
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

    // The slot being filled is outlined in the interactive gold; neither slot is raised.
    Card(
        modifier = modifier.clickable { onTap() },
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, if (isActive) colors.primaryText else colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                PersonAvatar(name = player.name, imageUrl = player.imageUrl, size = 56.dp)
                Spacer(Modifier.height(spacing.sm))
                Text(
                    player.name, style = typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = colors.onSurface,
                    textAlign = TextAlign.Center, maxLines = 2,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamCrest(teamName = player.country, size = 16.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(player.country, style = typography.bodySmall, color = colors.textSecondary, maxLines = 1)
                }
                // Clear button
                Spacer(Modifier.height(spacing.xs))
                // 48dp touch target overflowing the 24dp layout slot.
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp).requiredSize(48.dp)) {
                    Icon(Icons.Default.Close, "Clear", Modifier.size(14.dp), tint = colors.textSecondary)
                }
            } else if (player != null) {
                // Player selected but still placeholder (no stats yet / loading)
                Text(player.name, style = typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text(stringResource(R.string.player_tap_to_search), style = typography.labelSmall, color = colors.textSecondary)
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
                Text(stringResource(R.string.player_tap_to_search), style = typography.labelSmall, color = colors.textSecondary)
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

    val keyboardController = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    // Null until a search for the current query has answered.
    var outcome by remember { mutableStateOf<PlayerSearchOutcome?>(null) }
    var searching by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val results = (outcome as? PlayerSearchOutcome.Success)?.results.orEmpty()

    // Debounced search. A query edited during the debounce cancels this effect, so `searching` is
    // reset in finally: it used to stay true when the next query was too short to search, leaving
    // a spinner that never stopped.
    LaunchedEffect(query, attempt) {
        outcome = null
        if (query.length < 2) return@LaunchedEffect
        searching = true
        try {
            delay(400) // debounce
            outcome = PlayerProfileScraper.searchPlayersDetailed(query)
        } finally {
            searching = false
        }
    }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.player_search_player), style = typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.textSecondary) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
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
            shape = BouncerThemeV2.shapes.medium,
        )

        if (searching) {
            Box(Modifier.fillMaxWidth().padding(spacing.lg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colors.primaryText, strokeWidth = 2.dp)
            }
        }

        // A failed lookup and an empty one used to look the same: nothing at all.
        when {
            outcome is PlayerSearchOutcome.Unavailable -> EmptyStateV2(
                title = stringResource(R.string.player_search_unavailable),
                subtitle = stringResource(R.string.state_server_cause),
                icon = Icons.Outlined.CloudOff,
                onRetry = { attempt++ },
            )
            outcome is PlayerSearchOutcome.Success && results.isEmpty() -> EmptyStateV2(
                title = stringResource(R.string.state_search_empty_title, query.trim()),
                subtitle = stringResource(R.string.state_search_empty_cause),
                onRetry = { query = "" },
                actionLabel = stringResource(R.string.state_clear_search),
            )
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
                            // The result's own photo, which the row had and drew a person glyph instead of.
                            PersonAvatar(name = result.name, imageUrl = result.imageUrl, size = 36.dp)
                            Spacer(Modifier.width(spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(result.name, style = typography.bodyMedium, fontWeight = FontWeight.Medium, color = colors.onSurface)
                                if (result.country.isNotBlank()) {
                                    Text(result.country, style = typography.bodySmall, color = colors.textSecondary)
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


/**
 * One stat: both values, the label between them, and under it a bar split by each player's share in
 * their countries' colours — longer is better on every row, lower-is-better stats included. The
 * leader's value is bold ink, the other secondary. Rows on one surface; each was its own card.
 */
@Composable
private fun ComparisonRow(row: ComparisonData, color1: Color, color2: Color) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ComparisonValue(row.value1, leads = row.leader == 1, TextAlign.Start, Modifier.weight(1f))
            Text(
                text = row.label,
                style = typography.labelLarge,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            ComparisonValue(row.value2, leads = row.leader == 2, TextAlign.End, Modifier.weight(1f))
        }
        Spacer(Modifier.height(spacing.xs))
        // Fractions, not weights: a share of exactly 0 or 1 is legitimate and a zero weight throws.
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color2),
        ) {
            Box(Modifier.fillMaxWidth(row.share1.coerceIn(0f, 1f)).fillMaxHeight().background(color1))
        }
    }
    HorizontalDivider(color = colors.divider)
}

@Composable
private fun ComparisonValue(value: String, leads: Boolean, align: TextAlign, modifier: Modifier) {
    val colors = BouncerThemeV2.colors
    Text(
        text = value,
        style = BouncerThemeV2.typography.scoreMedium,
        fontWeight = if (leads) FontWeight.Bold else FontWeight.Normal,
        color = if (leads) colors.onSurface else colors.textSecondary,
        textAlign = align,
        modifier = modifier,
    )
}

private const val COMPARISON_BAR_CONTRAST = 2.2f

