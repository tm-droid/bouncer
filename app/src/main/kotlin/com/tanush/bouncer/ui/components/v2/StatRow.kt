package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Label + value row with optional divider. Used in match info, stats, settings.
 *
 * The label is a fixed column and the value takes the rest, end-aligned and free to wrap. With the
 * label weighted instead, a long venue or a pair of umpires squeezed the label to nothing. Values
 * that are figures use tabular digits so a column of them lines up.
 */
@Composable
fun StatRowV2(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm, horizontal = spacing.lg),
    ) {
        Text(
            text = label,
            style = typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.width(LABEL_WIDTH),
        )
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = value,
            style = if (isFigure(value)) typography.numeric else typography.titleSmall,
            color = colors.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
    if (showDivider) {
        HorizontalDivider(color = colors.divider)
    }
}

/** "142", "34.50", "-0.21", "61%", "28°C" — a number with at most a short unit. */
internal fun isFigure(value: String): Boolean = FIGURE.matches(value.trim())

private val FIGURE = Regex("""[-+]?\d[\d,]*(\.\d+)?\s?(%|°C|km/h|\*)?""")
private val LABEL_WIDTH = 112.dp
