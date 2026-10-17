package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.R
import com.tanush.bouncer.models.Series
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// A series as a list row, and where it is in time.

/**
 * Where a series is in time: "Live now", "Starts in 3 days", "Ended 12 Sep". Null when the dates
 * are not epoch values (Cricbuzz sometimes sends "TBC").
 */
internal fun seriesStateOf(series: Series, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): SeriesState? {
    fun day(raw: String) = raw.trim().toLongOrNull()?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    val start = day(series.startDate) ?: return null
    val end = day(series.endDate) ?: start
    return when {
        today.isBefore(start) -> SeriesState.Upcoming(ChronoUnit.DAYS.between(today, start).toInt())
        today.isAfter(end) -> SeriesState.Ended(end)
        else -> SeriesState.Running
    }
}

internal sealed interface SeriesState {
    data object Running : SeriesState
    data class Upcoming(val days: Int) : SeriesState
    data class Ended(val on: LocalDate) : SeriesState
}

@Composable
private fun seriesStateText(state: SeriesState): String = when (state) {
    SeriesState.Running -> stringResource(R.string.series_state_running)
    is SeriesState.Upcoming ->
        if (state.days <= 1) stringResource(R.string.series_state_tomorrow)
        else pluralStringResource(R.plurals.series_state_starts_in, state.days, state.days)
    is SeriesState.Ended -> stringResource(
        R.string.series_state_ended,
        state.on.format(DateTimeFormatter.ofPattern("d MMM", LocalConfiguration.current.locales[0])),
    )
}

/**
 * One series as a row: a plate naming its format, the name, then dates and where it is in time,
 * then its category as a quiet tag. It was an elevated card with the same gold calendar tile on every
 * series and the category in gold ink — gold is for what can be tapped.
 */
@Composable
internal fun SeriesCard(series: Series, onClick: () -> Unit) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val state = remember(series) { seriesStateOf(series, LocalDate.now()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FormatPlate(series.format)
        Spacer(Modifier.width(spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                series.name,
                style = typography.titleMedium,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val dates = seriesDatesOf(series)
            val line = listOfNotNull(dates.ifEmpty { null }, state?.let { seriesStateText(it) }).joinToString("  ·  ")
            if (line.isNotEmpty()) {
                Text(
                    line,
                    style = typography.bodySmall,
                    color = if (state == SeriesState.Running) colors.onSurface else colors.textSecondary,
                    fontWeight = if (state == SeriesState.Running) FontWeight.SemiBold else null,
                )
            }
            Spacer(Modifier.height(spacing.xs))
            CategoryTag(seriesLabelOf(series))
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(24.dp),
        )
    }
    HorizontalDivider(color = colors.divider)
}

/** "T20", "ODI", "TEST" on a neutral square; a trophy when the series names no single format. */
@Composable
private fun FormatPlate(format: String) {
    val colors = BouncerThemeV2.colors
    val label = format.trim().uppercase().takeIf { it.isNotEmpty() && it.length <= MAX_FORMAT_CHARS }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(label, style = BouncerThemeV2.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
        } else {
            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun CategoryTag(text: String) {
    val colors = BouncerThemeV2.colors
    Text(
        text = text,
        style = BouncerThemeV2.typography.labelMedium,
        color = colors.textSecondary,
        modifier = Modifier
            .border(1.dp, colors.border, BouncerThemeV2.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private const val MAX_FORMAT_CHARS = 5
