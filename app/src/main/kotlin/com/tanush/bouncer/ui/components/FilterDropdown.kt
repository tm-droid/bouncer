package com.tanush.bouncer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.rememberHapticFeedback

/**
 * Multi-Select Filter Chips (iOS Parity)
 * Allows selection of multiple filter options simultaneously
 */
@Composable
fun MultiSelectFilterChips(
    options: List<String>,
    selectedOptions: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    allLabel: String = "All",
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()
    
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // "All" chip
        item {
            MultiSelectChip(
                text = allLabel,
                isSelected = selectedOptions.isEmpty() || selectedOptions.contains(allLabel),
                onClick = {
                    haptic.click()
                    onSelectionChanged(emptySet()) // Empty means all
                }
            )
        }
        
        // Individual option chips
        items(options.filter { it != allLabel }, key = { it }) { option ->
            MultiSelectChip(
                text = option,
                isSelected = selectedOptions.contains(option),
                onClick = {
                    haptic.click()
                    val newSelection = if (selectedOptions.contains(option)) {
                        selectedOptions - option
                    } else {
                        // Remove "All" if selecting specific option
                        (selectedOptions - allLabel) + option
                    }
                    onSelectionChanged(newSelection)
                }
            )
        }
    }
}

@Composable
private fun MultiSelectChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) colors.goldPrimary.copy(alpha = 0.15f) else GlassSurface,
        animationSpec = tween(200),
        label = "chipBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colors.goldPrimary.copy(alpha = 0.5f) else GlassBorder,
        animationSpec = tween(200),
        label = "chipBorder"
    )
    
    val textColor by animateColorAsState(
        targetValue = if (isSelected) colors.goldPrimary else colors.textSecondary,
        animationSpec = tween(200),
        label = "chipText"
    )
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.shadow(
            elevation = if (isSelected) 4.dp else 0.dp,
            shape = RoundedCornerShape(20.dp),
            spotColor = hierarchy.secondary.primary.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(hierarchy.secondary.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
            
            Text(
                text = text,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

/**
 * Category filter chips with predefined cricket categories (iOS Parity)
 */
@Composable
fun CategoryFilterChips(
    selectedCategories: Set<String>,
    onCategoriesChanged: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf("All", "International", "League", "Domestic", "Women", "T20", "ODI", "Test")
    
    MultiSelectFilterChips(
        options = categories,
        selectedOptions = selectedCategories,
        onSelectionChanged = onCategoriesChanged,
        modifier = modifier
    )
}

@Composable
fun FilterDropdown(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    var expanded by remember { mutableStateOf(false) }
    val isFiltered = selectedOption != "All"
    val haptic = rememberHapticFeedback()
    
    val backgroundBrush = if (isFiltered) {
        Brush.horizontalGradient(
            colors = listOf(
                hierarchy.secondary.primary.copy(alpha = 0.2f),
                hierarchy.secondary.primary.copy(alpha = 0.1f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                GlassSurface,
                GlassSurface
            )
        )
    }

    Box {
        Row(
            modifier = Modifier
                .shadow(
                    elevation = if (isFiltered) 8.dp else 2.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = if (isFiltered) hierarchy.secondary.primary.copy(alpha = 0.4f) else Color.Transparent
                )
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundBrush)
                .border(
                    width = 1.dp,
                    brush = if (isFiltered) {
                        Brush.linearGradient(listOf(hierarchy.secondary.primary.copy(alpha = 0.5f), Color.Transparent))
                    } else {
                        Brush.linearGradient(listOf(colors.glassBorder.copy(alpha = 0.4f), Color.Transparent))
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable { 
                    haptic.click()
                    expanded = true 
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = null,
                tint = if (isFiltered) hierarchy.secondary.primary else colors.textMuted,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (selectedOption == "All") label else selectedOption,
                color = if (isFiltered) hierarchy.secondary.secondary else colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = if (isFiltered) FontWeight.Black else FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = if (isFiltered) hierarchy.secondary.primary else colors.textTertiary,
                modifier = Modifier.size(18.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(GlassSurface, GlassSurface)
                    ),
                    RoundedCornerShape(16.dp)
                )
                .border(1.dp, colors.glassBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(vertical = 8.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = if (isSelected) colors.goldLight else colors.textPrimary,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                            fontSize = 14.sp,
                            letterSpacing = 0.5.sp
                        )
                    },
                    modifier = Modifier.background(
                        if (isSelected) colors.goldPrimary.copy(alpha = 0.1f) else Color.Transparent
                    ),
                    onClick = {
                        haptic.click()
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
