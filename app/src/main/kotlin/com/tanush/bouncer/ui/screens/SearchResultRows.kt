package com.tanush.bouncer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanush.bouncer.R
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.matchRanges

/**
 * [text] with the parts that match [query] set in a heavier weight.
 *
 * Weight, not colour: gold is reserved for interactive affordances, and every name and number is
 * neutral ink. The rest of the title is Normal so the matched part reads against it.
 */
@Composable
internal fun rememberHighlighted(text: String, query: String): AnnotatedString =
    remember(text, query) {
        val ranges = matchRanges(text, query)
        buildAnnotatedString {
            append(text)
            ranges.forEach { addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), it.first, it.last + 1) }
        }
    }

@Composable
internal fun NoResultsMessage(suggestion: String?, onSuggestion: (String) -> Unit) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.search_no_results_title),
            style = typography.titleMedium,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            stringResource(R.string.search_no_results_hint),
            style = typography.bodyMedium,
            color = colors.textSecondary,
        )
        if (suggestion != null) {
            Spacer(Modifier.height(spacing.md))
            Row(modifier = Modifier.clickable { onSuggestion(suggestion) }.padding(spacing.sm)) {
                Text(
                    stringResource(R.string.search_did_you_mean),
                    style = typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Text(
                    suggestion,
                    style = typography.bodyMedium,
                    color = colors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** The online player lookup's state, inline in the Players section so other results never hide it. */
@Composable
internal fun PlayerLookupRow(text: String) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Text(
        text,
        style = typography.bodySmall,
        color = colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
    )
}

@Composable
internal fun SearchBarHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
) {
    val colors = BouncerThemeV2.colors
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.topBar)
            .padding(horizontal = spacing.sm, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = colors.textSecondary,
            )
        }

        SearchQueryField(
            query = query,
            onQueryChange = onQueryChange,
            onClear = onClear,
            onSearch = onSearch,
            focusRequester = focusRequester,
            modifier = Modifier.weight(1f),
        )

        Spacer(Modifier.width(spacing.sm))
    }
}

@Composable
private fun SearchQueryField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(spacing.sm))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.search_placeholder),
                        style = typography.bodyMedium,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = typography.bodyMedium.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
            AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                // 48dp touch target overflowing the 24dp layout slot (keeps bar height).
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp).requiredSize(48.dp)) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.cd_clear),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** A recent or live suggestion. [onRemove] adds a trailing remove button (recents only). */
@Composable
internal fun SearchSuggestionRow(
    text: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = spacing.lg, end = spacing.sm)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(spacing.md))
        Text(
            text,
            style = typography.bodyMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = stringResource(R.string.cd_remove_recent, text),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun TeamSearchRow(
    teamName: String,
    acronym: String,
    logoUrl: String?,
    fixtureCount: Int,
    query: String,
    onClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (logoUrl != null) {
            AsyncImage(model = logoUrl, contentDescription = teamName, modifier = Modifier.size(32.dp).clip(CircleShape))
        } else {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(acronym.take(2), style = typography.labelSmall, color = colors.textSecondary, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(spacing.md))
        Column {
            Text(rememberHighlighted(teamName, query), style = typography.bodyMedium, color = colors.onSurface)
            val subtitle = if (fixtureCount > 0) {
                pluralStringResource(R.plurals.search_team_fixtures, fixtureCount, acronym, fixtureCount)
            } else acronym
            Text(rememberHighlighted(subtitle, query), style = typography.bodySmall, color = colors.textSecondary)
        }
    }
}

@Composable
internal fun PlayerSearchRow(
    playerName: String,
    teamName: String,
    role: String,
    imageUrl: String?,
    query: String,
    onClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = playerName,
                modifier = Modifier.size(32.dp).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(spacing.md))
        Column {
            Text(rememberHighlighted(playerName, query), style = typography.bodyMedium, color = colors.onSurface)
            val subtitle = listOf(teamName, role).filter { it.isNotEmpty() }.joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = typography.bodySmall, color = colors.textSecondary)
            }
        }
    }
}

@Composable
internal fun NewsSearchRow(
    title: String,
    source: String,
    query: String,
    onClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
    ) {
        Text(
            rememberHighlighted(title, query),
            style = typography.bodyMedium,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(source, style = typography.bodySmall, color = colors.textSecondary)
        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(top = spacing.sm))
    }
}

@Composable
internal fun SeriesSearchRow(
    name: String,
    category: String,
    startDate: String,
    endDate: String,
    query: String,
    onClick: () -> Unit,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                category.take(1),
                style = typography.labelSmall,
                color = colors.textSecondary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(spacing.md))
        Column {
            Text(
                rememberHighlighted(name, query),
                style = typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = if (startDate.isNotEmpty() && endDate.isNotEmpty()) "$startDate – $endDate" else category
            Text(subtitle, style = typography.bodySmall, color = colors.textSecondary)
        }
    }
}
