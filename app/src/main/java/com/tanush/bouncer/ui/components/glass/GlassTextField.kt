package com.tanush.bouncer.ui.components.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ripple
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanush.bouncer.ui.theme.BouncerDesignSystem

/**
 * Glass-styled search bar with search icon and optional clear button.
 *
 * @param value Current text value
 * @param onValueChange Callback when text changes
 * @param modifier Modifier for the search bar
 * @param placeholder Placeholder text
 * @param onSearch Callback when search is submitted
 * @param onClear Callback when clear button is pressed
 * @param enabled Whether the search bar is enabled
 */
@Composable
fun GlassSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    onSearch: ((String) -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) {
            Color.White.copy(alpha = 0.08f)
        } else {
            Color.White.copy(alpha = 0.04f)
        },
        label = "bg_color"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) {
            hierarchy.secondary.primary.copy(alpha = 0.6f)
        } else {
            Color.White.copy(alpha = 0.12f)
        },
        label = "border_color"
    )
    
    val shape = RoundedCornerShape(12.dp)
    
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = dimens.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.sm)
    ) {
        // Search icon
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = if (isFocused) hierarchy.secondary.primary else colors.textTertiary,
            modifier = Modifier.size(dimens.iconSize.md)
        )
        
        // Text field
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            textStyle = typography.bodyMedium.copy(
                color = colors.textPrimary
            ),
            singleLine = true,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(hierarchy.secondary.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearch?.invoke(value)
                    focusManager.clearFocus()
                }
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = typography.bodyMedium,
                            color = colors.textTertiary
                        )
                    }
                    innerTextField()
                }
            }
        )
        
        // Clear button
        if (value.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Clear",
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(dimens.iconSize.md)
                    .clip(CircleShape)
                    .clickable {
                        onValueChange("")
                        onClear?.invoke()
                    }
            )
        }
    }
}

/**
 * Compact search bar variant.
 */
@Composable
fun GlassSearchBarCompact(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    onSearch: ((String) -> Unit)? = null,
    enabled: Boolean = true
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) {
            colors.glassBackgroundHover
        } else {
            colors.glassBackground
        },
        label = "bg_color"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) {
            hierarchy.secondary.primary.copy(alpha = 0.4f)
        } else {
            colors.glassBorder
        },
        label = "border_color"
    )
    
    val shape = RoundedCornerShape(12.dp)
    
    Row(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = colors.textTertiary,
            modifier = Modifier.size(16.dp)
        )
        
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            textStyle = typography.bodySmall.copy(
                color = colors.textPrimary
            ),
            singleLine = true,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(hierarchy.secondary.primary),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearch?.invoke(value)
                    focusManager.clearFocus()
                }
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = typography.bodySmall,
                            color = colors.textTertiary
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

/**
 * Glass-styled dropdown selector.
 *
 * @param selectedValue Currently selected value
 * @param options List of available options
 * @param onOptionSelected Callback when an option is selected
 * @param modifier Modifier for the dropdown
 * @param placeholder Placeholder when no selection
 * @param label Optional label above the dropdown
 * @param enabled Whether the dropdown is enabled
 */
@Composable
fun GlassDropdown(
    selectedValue: String?,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select...",
    label: String? = null,
    enabled: Boolean = true
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    var expanded by remember { mutableStateOf(false) }
    
    val backgroundColor by animateColorAsState(
        targetValue = if (expanded) {
            colors.glassBackgroundHover
        } else {
            colors.glassBackground
        },
        label = "bg_color"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (expanded) {
            hierarchy.secondary.primary.copy(alpha = 0.4f)
        } else {
            colors.glassBorder
        },
        label = "border_color"
    )
    
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "arrow_rotation"
    )
    
    val shape = RoundedCornerShape(12.dp)
    
    Column(modifier = modifier) {
        // Label
        if (label != null) {
            Text(
                text = label.uppercase(),
                style = typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )
        }
        
        // Dropdown trigger
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(backgroundColor)
                    .border(1.dp, borderColor, shape)
                    .then(
                        if (enabled) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = hierarchy.secondary.primary)
                            ) { expanded = true }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedValue ?: placeholder,
                    style = typography.bodyMedium,
                    color = if (selectedValue != null) colors.textPrimary else colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(arrowRotation)
                )
            }
            
            // Dropdown menu
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(colors.surface2)
                    .border(1.dp, colors.glassBorder, shape)
            ) {
                options.forEach { option ->
                    val isSelected = option == selectedValue
                    
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                style = typography.bodyMedium,
                                color = if (isSelected) colors.goldPrimary else colors.textPrimary
                            )
                        },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        },
                        modifier = Modifier
                            .background(
                                if (isSelected) colors.goldPrimary.copy(alpha = 0.1f)
                                else Color.Transparent
                            )
                    )
                }
            }
        }
    }
}

/**
 * Glass-styled text input field.
 *
 * @param value Current text value
 * @param onValueChange Callback when text changes
 * @param modifier Modifier for the field
 * @param placeholder Placeholder text
 * @param label Optional label above the field
 * @param leadingIcon Optional leading icon
 * @param trailingIcon Optional trailing icon
 * @param isError Whether to show error state
 * @param errorMessage Error message to display
 * @param enabled Whether the field is enabled
 * @param singleLine Whether to limit to single line
 * @param keyboardType Keyboard type
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.glassBackground.copy(alpha = 0.5f)
            isError -> colors.statusLive.copy(alpha = 0.05f)
            isFocused -> colors.glassBackgroundHover
            else -> colors.glassBackground
        },
        label = "bg_color"
    )
    
    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> colors.statusLive.copy(alpha = 0.5f)
            isFocused -> hierarchy.secondary.primary.copy(alpha = 0.4f)
            else -> colors.glassBorder
        },
        label = "border_color"
    )
    
    val shape = RoundedCornerShape(12.dp)
    
    Column(modifier = modifier) {
        // Label
        if (label != null) {
            Text(
                text = label.uppercase(),
                style = typography.labelSmall,
                color = if (isError) colors.statusLive else colors.textTertiary,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )
        }
        
        // Input field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(backgroundColor)
                .border(1.dp, borderColor, shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Leading icon
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (isFocused || value.isNotEmpty()) {
                        hierarchy.secondary.primary
                    } else {
                        colors.textMuted
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // Text field
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                textStyle = typography.bodyMedium.copy(
                    color = if (enabled) colors.textPrimary else colors.textMuted
                ),
                singleLine = singleLine,
                interactionSource = interactionSource,
                cursorBrush = SolidColor(hierarchy.secondary.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = typography.bodyMedium,
                                color = colors.textTertiary
                            )
                        }
                        innerTextField()
                    }
                }
            )
            
            // Trailing icon
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .then(
                            if (onTrailingIconClick != null) {
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onTrailingIconClick() }
                            } else {
                                Modifier
                            }
                        )
                )
            }
        }
        
        // Error message
        AnimatedVisibility(
            visible = isError && errorMessage != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = errorMessage ?: "",
                style = typography.labelSmall,
                color = colors.statusLive,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }
    }
}

/**
 * Number input field with increment/decrement buttons.
 *
 * @param value Current numeric value
 * @param onValueChange Callback when value changes
 * @param modifier Modifier for the field
 * @param label Optional label
 * @param minValue Minimum allowed value
 * @param maxValue Maximum allowed value
 * @param step Increment/decrement step size
 */
@Composable
fun GlassNumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    minValue: Int = 0,
    maxValue: Int = Int.MAX_VALUE,
    step: Int = 1
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    
    val shape = RoundedCornerShape(12.dp)
    
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label.uppercase(),
                style = typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )
        }
        
        Row(
            modifier = Modifier
                .clip(shape)
                .background(colors.glassBackground)
                .border(1.dp, colors.glassBorder, shape),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decrement button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        enabled = value > minValue,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = hierarchy.secondary.primary)
                    ) {
                        onValueChange((value - step).coerceAtLeast(minValue))
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "−",
                    style = typography.headingMedium,
                    color = if (value > minValue) colors.textPrimary else colors.textMuted
                )
            }
            
            // Value display
            Text(
                text = value.toString(),
                style = typography.statValue,
                color = colors.textPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            // Increment button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        enabled = value < maxValue,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = hierarchy.secondary.primary)
                    ) {
                        onValueChange((value + step).coerceAtMost(maxValue))
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    style = typography.headingMedium,
                    color = if (value < maxValue) colors.textPrimary else colors.textMuted
                )
            }
        }
    }
}
