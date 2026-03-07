package com.tanush.bouncer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.utils.rememberHapticFeedback

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search matches...",
    enabled: Boolean = true
) {
    val haptic = rememberHapticFeedback()
    var isFocused by remember { mutableStateOf(false) }
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) GoldPrimary else GlassBorder,
        animationSpec = tween(300),
        label = "searchBorder"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "searchShimmer")
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "searchShimmer"
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isFocused) 8.dp else 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = if (isFocused) GoldPrimary.copy(alpha = 0.3f) else Color.Black.copy(
                    alpha = 0.1f
                )
            )
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        GlassSurface,
                        Surface1
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = if (isFocused) GoldPrimary.copy(alpha = shimmer) else TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    color = TextMuted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal
                )
            }
            
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                singleLine = true,
                enabled = enabled,
                cursorBrush = SolidColor(GoldPrimary),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        haptic.click()
                        onSearch()
                    }
                )
            )
        }
        
        AnimatedVisibility(
            visible = query.isNotEmpty(),
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            IconButton(
                onClick = {
                    haptic.click()
                    onQueryChange("")
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterChipGroup(
    filters: List<String>,
    selectedFilters: Set<String>,
    onFilterToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()
    
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { filter ->
            val isSelected = selectedFilters.contains(filter)
            FilterChip(
                label = filter,
                isSelected = isSelected,
                onClick = {
                    haptic.click()
                    onFilterToggle(filter)
                }
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "chipScale"
    )
    
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isSelected) 6.dp else 0.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = if (isSelected) GoldPrimary.copy(alpha = 0.4f) else Color.Transparent
            )
            .background(
                brush = if (isSelected) {
                    Brush.linearGradient(
                        colors = listOf(
                            GoldPrimary,
                            GoldPrimary
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            GlassSurface,
                            GlassSurface
                        )
                    )
                },
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface.copy(
                alpha = 0.7f
            ),
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
fun SortButton(
    currentSort: String,
    sortOptions: List<String>,
    onSortChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = rememberHapticFeedback()
    
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            GoldPrimary.copy(alpha = 0.2f),
                            GoldPrimary.copy(alpha = 0.15f)
                        )
                    ),
                    RoundedCornerShape(12.dp)
                )
                .clickable {
                    haptic.click()
                    expanded = !expanded
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Sort",
                tint = GoldPrimary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = currentSort,
                color = GoldPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(
                    GlassSurface,
                    RoundedCornerShape(12.dp)
                )
        ) {
            sortOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = if (option == currentSort) GoldPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (option == currentSort) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        haptic.click()
                        onSortChange(option)
                        expanded = false
                    },
                    modifier = Modifier.background(
                        if (option == currentSort)
                            GoldPrimary.copy(alpha = 0.1f)
                        else
                            Color.Transparent
                    )
                )
            }
        }
    }
}
