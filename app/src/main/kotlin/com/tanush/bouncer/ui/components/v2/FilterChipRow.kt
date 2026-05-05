package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2
import com.tanush.bouncer.utils.PreferencesManager

/**
 * Horizontal scrollable row of selectable filter chips.
 */
@Composable
fun FilterChipRowV2(
    items: List<String>,
    selectedItem: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    counts: Map<String, Int>? = null,
) {
    val colors = BouncerThemeV2.colors
    val typography = BouncerThemeV2.typography
    val spacing = BouncerThemeV2.spacing
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val hapticEnabled = remember { PreferencesManager.getInstance(context).hapticFeedbackEnabled }

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        items.forEach { item ->
            val isSelected = item == selectedItem
            val label = if (counts != null && counts.containsKey(item)) {
                "$item ${counts[item]}"
            } else {
                item
            }

            FilterChip(
                selected = isSelected,
                onClick = {
                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onItemSelected(item)
                },
                label = {
                    Text(
                        text = label,
                        style = typography.labelLarge,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.primary,
                    selectedLabelColor = colors.onPrimary,
                    containerColor = colors.surfaceVariant,
                    labelColor = colors.onSurface,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = colors.border,
                    selectedBorderColor = colors.primary,
                    enabled = true,
                    selected = isSelected,
                ),
            )
        }
    }
}
