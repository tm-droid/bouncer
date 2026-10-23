package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/** What Cricsheet leaves out (`HistoryCoverage`), as one neutral line: a gap in the data, not an action. */
@Composable
fun HistoryCoverageNotice(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = BouncerThemeV2.typography.bodySmall,
        color = BouncerThemeV2.colors.textSecondary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BouncerThemeV2.spacing.lg, vertical = BouncerThemeV2.spacing.sm),
    )
}
