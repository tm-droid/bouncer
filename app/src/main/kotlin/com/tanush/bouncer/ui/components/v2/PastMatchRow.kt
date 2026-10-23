package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.data.history.PastMatchRow
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DATE_PATTERN = "d MMM yyyy"
private val CREST_SIZE = 28.dp

/**
 * A finished match from Cricsheet or the archive: date, format and event over the two crests,
 * "A v B" and the result. Neutral ink throughout, since it is a record and not an affordance.
 */
@Composable
fun PastMatchRow(row: PastMatchRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val meta = remember(row) {
        val date = row.date?.format(DateTimeFormatter.ofPattern(DATE_PATTERN, Locale.getDefault()))
        listOfNotNull(date, row.format, row.event?.takeIf { it.isNotBlank() })
            .joinToString(" · ")
    }
    val outcome = remember(row) { row.toCricketMatch().currentStatus }
    Column(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md)) {
            Text(
                meta, style = typography.bodySmall, color = colors.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(modifier = Modifier.padding(top = spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                TeamCrest(teamName = row.team1, size = CREST_SIZE)
                Spacer(Modifier.width(spacing.xs))
                TeamCrest(teamName = row.team2, size = CREST_SIZE)
                Spacer(Modifier.width(spacing.md))
                Text(
                    "${row.team1} v ${row.team2}",
                    style = typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                outcome, style = typography.bodySmall, color = colors.textSecondary,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }
        HorizontalDivider(color = colors.divider)
    }
}
