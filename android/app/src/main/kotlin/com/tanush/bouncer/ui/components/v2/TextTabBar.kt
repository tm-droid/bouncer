package com.tanush.bouncer.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.v2.BouncerThemeV2

/**
 * Text tabs on the header colour with a hairline under them — the shape Home's status tabs use, so
 * a screen that switches between views of one list reads the same everywhere. Gold marks only the
 * selected tab. [trailing] sits at the end of the row, for a menu that narrows the selected view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextTabBarV2(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = BouncerThemeV2.colors
    Column(modifier = modifier.background(colors.topBar)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedIndex,
                modifier = Modifier.weight(1f),
                containerColor = colors.topBar,
                contentColor = colors.onSurface,
                edgePadding = BouncerThemeV2.spacing.xs,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(selectedIndex, matchContentSize = true),
                        width = Dp.Unspecified,
                        color = colors.primaryText,
                    )
                },
                divider = {},
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        text = { Text(tab, style = BouncerThemeV2.typography.labelLarge) },
                        selectedContentColor = colors.primaryText,
                        unselectedContentColor = colors.textSecondary,
                    )
                }
            }
            trailing()
        }
        HorizontalDivider(color = colors.divider)
    }
}

/**
 * A one-of-many picker shown as its current value and a caret: "2025 ▾", "Most runs ▾". The label is
 * gold because tapping it is the affordance; the options are neutral except the selected one.
 */
@Composable
fun <T> TextMenuV2(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** Draws a non-selectable heading above an option, e.g. "Batting". Null draws nothing. */
    sectionOf: ((T) -> String?)? = null,
) {
    val colors = BouncerThemeV2.colors
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(label, style = BouncerThemeV2.typography.labelLarge, color = colors.primaryText)
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = colors.primaryText,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = colors.surface) {
            MenuOptions(options, selected, optionLabel, sectionOf) { option ->
                expanded = false
                onSelect(option)
            }
        }
    }
}

@Composable
private fun <T> MenuOptions(
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    sectionOf: ((T) -> String?)?,
    onSelect: (T) -> Unit,
) {
    val colors = BouncerThemeV2.colors
    options.forEachIndexed { index, option ->
        val section = sectionOf?.invoke(option)
        val previousSection = options.getOrNull(index - 1)?.let { sectionOf?.invoke(it) }
        if (section != null && section != previousSection) {
            DropdownMenuItem(
                text = { Text(section, style = BouncerThemeV2.typography.labelMedium, color = colors.textSecondary) },
                onClick = {},
                enabled = false,
            )
        }
        val isSelected = option == selected
        DropdownMenuItem(
            text = { Text(optionLabel(option), color = if (isSelected) colors.primaryText else colors.onSurface) },
            onClick = { onSelect(option) },
            trailingIcon = {
                if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = colors.primaryText)
            },
        )
    }
}
