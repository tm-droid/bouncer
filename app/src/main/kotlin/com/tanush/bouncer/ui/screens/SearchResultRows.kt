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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

@Composable
internal fun NoResultsMessage() {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No results found", style = typography.titleMedium, color = colors.onSurface)
        Spacer(Modifier.height(spacing.xs))
        Text(
            "Try a different search term",
            style = typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
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
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.topBar)
            .padding(horizontal = spacing.sm, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textSecondary)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceVariant)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(spacing.sm))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            "Search matches, players, teams, tournaments…",
                            style = typography.bodyMedium,
                            color = colors.textSecondary,
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
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(Modifier.width(spacing.sm))
    }
}

@Composable
internal fun SearchSuggestionRow(
    text: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
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
        icon()
        Spacer(Modifier.width(spacing.md))
        Text(text, style = typography.bodyMedium, color = colors.onSurface)
    }
}

@Composable
internal fun TeamSearchRow(
    teamName: String,
    acronym: String,
    logoUrl: String?,
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
            Text(teamName, style = typography.bodyMedium, color = colors.onSurface, fontWeight = FontWeight.Medium)
            Text(acronym, style = typography.bodySmall, color = colors.textSecondary)
        }
    }
}

@Composable
internal fun PlayerSearchRow(
    playerName: String,
    teamName: String,
    role: String,
    imageUrl: String? = null,
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
                modifier = Modifier.size(32.dp).clip(CircleShape).background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(spacing.md))
        Column {
            Text(playerName, style = typography.bodyMedium, color = colors.onSurface, fontWeight = FontWeight.Medium)
            val subtitle = listOfNotNull(teamName.takeIf { it.isNotEmpty() }, role.takeIf { it.isNotEmpty() }).joinToString(" · ")
            Text(subtitle.ifEmpty { teamName }, style = typography.bodySmall, color = colors.textSecondary)
        }
    }
}

@Composable
internal fun NewsSearchRow(
    title: String,
    source: String,
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
        Text(title, style = typography.bodyMedium, color = colors.onSurface, maxLines = 2)
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
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                category.take(1),
                style = typography.labelSmall,
                color = colors.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(spacing.md))
        Column {
            Text(name, style = typography.bodyMedium, color = colors.onSurface, fontWeight = FontWeight.Medium, maxLines = 1)
            val subtitle = if (startDate.isNotEmpty() && endDate.isNotEmpty()) "$startDate – $endDate" else category
            Text(subtitle, style = typography.bodySmall, color = colors.textSecondary)
        }
    }
}

