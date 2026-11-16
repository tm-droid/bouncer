package com.tanush.bouncer.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.tanush.bouncer.utils.openLink
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanush.bouncer.R
import com.tanush.bouncer.data.history.HistoryCoverage
import com.tanush.bouncer.data.history.PlayerBio
import com.tanush.bouncer.data.history.PlayerCareer
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.v2.BouncerTopBarV2
import com.tanush.bouncer.ui.components.v2.DelayedSkeleton
import com.tanush.bouncer.ui.components.v2.FailedState
import com.tanush.bouncer.ui.components.v2.FailureKind
import com.tanush.bouncer.ui.components.v2.HistoryCoverageNotice
import com.tanush.bouncer.ui.components.v2.PastMatchRow
import com.tanush.bouncer.ui.components.v2.ScreenState
import com.tanush.bouncer.ui.components.v2.SectionHeaderV2
import com.tanush.bouncer.ui.components.v2.ShimmerPlayerHeader
import com.tanush.bouncer.ui.components.v2.TextTabBarV2
import com.tanush.bouncer.ui.components.v2.screenStateOf
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.viewmodels.HistoricalPlayerState
import com.tanush.bouncer.viewmodels.HistoricalPlayerViewModel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Usual order first; any other format Cricsheet has (a league's own) follows.
private val FORMAT_ORDER = listOf("Test", "ODI", "T20I", "T20")

/**
 * A player's Cricsheet career: the figures per format, then the latest matches. It reuses the
 * Cricbuzz profile's header, format tabs and stat rows, minus the boundary and five-wicket rows
 * Cricsheet cannot supply. [displayName] is the full name from the link that led here; the record
 * itself only has "V Kohli".
 */
@Composable
fun HistoricalPlayerScreen(
    playerId: String,
    displayName: String?,
    onBackClick: () -> Unit,
    onMatchClick: (CricketMatch) -> Unit,
    /** Opens the live profile (photo, birth date, styles) for a Cricbuzz id Cricsheet's register names. */
    onLiveProfileClick: ((cricbuzzId: String) -> Unit)? = null,
) {
    val viewModel: HistoricalPlayerViewModel = viewModel(
        key = HistoricalPlayerViewModel.storeKey(playerId),
        factory = HistoricalPlayerViewModel.factory(playerId),
    )
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = { BouncerTopBarV2(title = stringResource(R.string.player_player), showBack = true, onBack = onBackClick) },
        containerColor = BouncerThemeV2.colors.background,
    ) { padding ->
        val loaded = state as? HistoricalPlayerState.Loaded
        val screen = screenStateOf(
            hasContent = loaded != null,
            isLoading = state is HistoricalPlayerState.Loading,
            failure = FailureKind.Server.takeIf { state is HistoricalPlayerState.Failed },
        )
        when {
            loaded != null -> CareerList(padding, loaded, displayName, onMatchClick, onLiveProfileClick)
            screen is ScreenState.Failed -> FailedState(screen.kind, viewModel::retry, Modifier.padding(padding))
            else -> DelayedSkeleton(isLoading = true) {
                Column(Modifier.fillMaxSize().padding(padding).padding(BouncerThemeV2.spacing.lg)) { ShimmerPlayerHeader() }
            }
        }
    }
}

@Composable
private fun CareerList(
    padding: PaddingValues,
    loaded: HistoricalPlayerState.Loaded,
    displayName: String?,
    onMatchClick: (CricketMatch) -> Unit,
    onLiveProfileClick: ((String) -> Unit)?,
) {
    val spacing = BouncerThemeV2.spacing
    val career = loaded.career
    val formats = remember(career) { orderedFormats(career) }
    var selected by rememberSaveable(career.name) { mutableStateOf(formats.firstOrNull().orEmpty()) }
    val notice = HistoryCoverage.afghanistanNotice(
        career.teams.map { it.first },
        stringResource(R.string.history_career_missing_afghanistan),
    )
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = spacing.xl)) {
        item(key = "header") {
            Column(Modifier.padding(spacing.lg)) {
                PlayerHeader(
                    name = displayName?.takeIf { it.isNotBlank() } ?: career.name,
                    country = career.teams.firstOrNull()?.first.orEmpty(), role = "", imageUrl = career.bio?.photoUrl,
                    isCaptain = false, isKeeper = false,
                )
                Text(
                    stringResource(R.string.history_career_caption),
                    style = BouncerThemeV2.typography.bodySmall,
                    color = BouncerThemeV2.colors.textSecondary,
                    modifier = Modifier.padding(top = spacing.sm),
                )
            }
        }
        career.bio?.let { bio -> item(key = "bio") { BioBlock(bio) } }
        val liveId = career.cricbuzzId
        if (liveId != null && onLiveProfileClick != null) {
            item(key = "live_profile") {
                Text(
                    stringResource(R.string.history_live_profile_link),
                    style = BouncerThemeV2.typography.bodyMedium,
                    color = BouncerThemeV2.colors.primaryText,
                    modifier = Modifier.fillMaxWidth().clickable { onLiveProfileClick(liveId) }
                        .padding(horizontal = spacing.lg, vertical = spacing.md),
                )
            }
        }
        notice?.let { item(key = "coverage_notice") { HistoryCoverageNotice(it) } }
        if (formats.size > 1) {
            item(key = "format_selector") {
                TextTabBarV2(
                    tabs = formats,
                    selectedIndex = formats.indexOf(selected).coerceAtLeast(0),
                    onSelect = { selected = formats[it] },
                )
            }
        }
        formatStatItems(career.formats[selected], selected, hasBoundaries = career.hasBoundaries)
        if (loaded.recent.isNotEmpty()) {
            item(key = "recent_header") { SectionHeaderV2(title = stringResource(R.string.history_career_recent)) }
            items(loaded.recent, key = { "match_${it.id}" }) { row ->
                PastMatchRow(row = row, onClick = { onMatchClick(row.toCricketMatch()) })
            }
        }
    }
}

/** Birth line, Wikipedia link and photo credit; each is left out when Wikidata or Commons had nothing. */
@Composable
private fun BioBlock(bio: PlayerBio) {
    val spacing = BouncerThemeV2.spacing
    val context = LocalContext.current
    // openLink toasts when the device has nothing to open the link in.
    val open = { url: String -> context.openLink(url) }
    Column(Modifier.fillMaxWidth().padding(horizontal = spacing.lg)) {
        bornText(bio)?.let { Text(it, style = BouncerThemeV2.typography.bodyMedium, color = BouncerThemeV2.colors.textSecondary) }
        bio.wikipediaUrl?.let { url ->
            Text(
                stringResource(R.string.history_bio_wikipedia),
                style = BouncerThemeV2.typography.bodyMedium,
                color = BouncerThemeV2.colors.primaryText,
                modifier = Modifier.clickable { open(url) }.padding(vertical = spacing.sm),
            )
        }
        PhotoCredit(bio, open)
    }
}

@Composable
private fun bornText(bio: PlayerBio): String? {
    val date = bio.birthDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) ?: bio.birthYear
    val place = bio.birthplace
    return when {
        date != null && place != null -> stringResource(R.string.history_bio_born_place, date, place)
        date != null -> stringResource(R.string.history_bio_born, date)
        place != null -> stringResource(R.string.history_bio_born_in, place)
        else -> null
    }
}

@Composable
private fun PhotoCredit(bio: PlayerBio, open: (String) -> Unit) {
    val licence = bio.photoLicense
    if (bio.photoUrl == null || licence == null) return
    val artist = bio.photoArtist
    val credit = if (artist != null) stringResource(R.string.history_bio_photo_credit, artist, licence)
    else stringResource(R.string.history_bio_photo_credit_licence, licence)
    val link = bio.photoCreditUrl
    Text(
        credit,
        style = BouncerThemeV2.typography.bodySmall,
        color = BouncerThemeV2.colors.textSecondary,
        textDecoration = TextDecoration.Underline.takeIf { link != null },
        modifier = if (link != null) Modifier.clickable { open(link) } else Modifier,
    )
}

private fun orderedFormats(career: PlayerCareer): List<String> =
    FORMAT_ORDER.filter { it in career.formats } + career.formats.keys.filter { it !in FORMAT_ORDER }.sorted()
