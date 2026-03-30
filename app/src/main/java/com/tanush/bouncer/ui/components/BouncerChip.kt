package com.tanush.bouncer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.ui.theme.BouncerDimens
import com.tanush.bouncer.ui.theme.BouncerTheme
import com.tanush.bouncer.ui.theme.ChipShape

/**
 * Styled Material 3 [FilterChip] with animated colour transitions.
 *
 * - **Selected** — primary background, onPrimary text
 * - **Unselected** — surfaceVariant background, onSurfaceVariant text
 */
@Composable
fun BouncerFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val ds = BouncerDesignSystem.colors
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            ds.goldPrimary
        } else {
            ds.glassSurface
        },
        animationSpec = tween(durationMillis = BouncerDimens.animFast),
        label = "chipContainerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            ds.backgroundBase
        } else {
            ds.textSecondary
        },
        animationSpec = tween(durationMillis = BouncerDimens.animFast),
        label = "chipContentColor"
    )

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        },
        modifier = modifier.height(BouncerDimens.chipHeight),
        shape = ChipShape,
        leadingIcon = leadingIcon,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = ds.glassSurface,
            selectedContainerColor = ds.goldPrimary,
            labelColor = ds.textSecondary,
            selectedLabelColor = ds.backgroundBase,
            iconColor = ds.textSecondary,
            selectedLeadingIconColor = ds.backgroundBase
        ),
        border = null
    )
}

/**
 * Horizontally-scrollable row of [BouncerFilterChip]s with single-selection behaviour.
 *
 * Automatically scrolls to keep the selected chip visible.
 */
@Composable
fun BouncerChipRow(
    items: List<String>,
    selectedItem: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Scroll to keep selected chip visible whenever it changes.
    LaunchedEffect(selectedItem) {
        val index = items.indexOf(selectedItem)
        if (index >= 0) {
            // Approximate 80dp per chip; scroll so the chip is near the start.
            val targetPx = (index * 80 * scrollState.maxValue)
                .takeIf { items.isNotEmpty() }
                ?.div(items.size.coerceAtLeast(1))
                ?: 0
            scrollState.animateScrollTo(targetPx.coerceIn(0, scrollState.maxValue))
        }
    }

    Row(
        modifier = modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(BouncerDimens.sm)
    ) {
        items.forEach { item ->
            BouncerFilterChip(
                label = item,
                selected = item == selectedItem,
                onClick = { onItemSelected(item) }
            )
        }
    }
}

// ── Previews ────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun BouncerFilterChipPreview() {
    BouncerTheme(dynamicColor = false) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(BouncerDesignSystem.dimens.sm),
            modifier = Modifier.padding(BouncerDesignSystem.dimens.md)
        ) {
            BouncerFilterChip(label = "All", selected = true, onClick = {})
            BouncerFilterChip(label = "Live", selected = false, onClick = {})
            BouncerFilterChip(label = "Upcoming", selected = false, onClick = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BouncerChipRowPreview() {
    BouncerTheme(dynamicColor = false) {
        BouncerChipRow(
            items = listOf("All", "Live", "T20I", "ODI", "Test", "Women", "Domestic"),
            selectedItem = "Live",
            onItemSelected = {},
            modifier = Modifier.padding(BouncerDesignSystem.dimens.md)
        )
    }
}
