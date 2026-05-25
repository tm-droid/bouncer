package com.tanush.bouncer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.GoldPrimary

/**
 * Premium form input and control components
 */

private val ErrorRed = Color(0xFFEF4444)
private val FieldShape = RoundedCornerShape(12.dp)

@Composable
private fun fieldBorderColor(isError: Boolean, isFocused: Boolean): Color {
    val hierarchy = BouncerDesignSystem.hierarchy
    val target = when {
        isError -> ErrorRed
        isFocused -> hierarchy.secondary.primary
        else -> hierarchy.secondary.primary.copy(alpha = 0.3f)
    }
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = tween(200),
        label = "borderColor"
    )
    return color
}

@Composable
private fun FieldLabel(label: String, isError: Boolean) {
    if (label.isEmpty()) return
    Text(
        text = label,
        color = if (isError) ErrorRed else TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun FieldErrorMessage(isError: Boolean, errorMessage: String) {
    if (!isError || errorMessage.isEmpty()) return
    Text(
        text = errorMessage,
        color = ErrorRed,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun BoxScope.TextFieldGlow(isError: Boolean, glowAlpha: Float) {
    if (glowAlpha <= 0f) return
    val hierarchy = BouncerDesignSystem.hierarchy
    Box(
        modifier = Modifier
            .matchParentSize()
            .alpha(glowAlpha)
            .blur(8.dp)
            .background(
                color = if (isError) ErrorRed else hierarchy.secondary.primary,
                shape = FieldShape
            )
    )
}

@Composable
private fun TextFieldTrailing(
    trailingIcon: ImageVector?,
    onTrailingIconClick: (() -> Unit)?,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    when {
        trailingIcon != null -> IconButton(
            onClick = { onTrailingIconClick?.invoke() },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        value.isNotEmpty() && enabled -> IconButton(
            onClick = { onValueChange("") },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Clear",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun TextFieldDecoration(
    value: String,
    placeholder: String,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?,
    onTrailingIconClick: (() -> Unit)?,
    enabled: Boolean,
    isFocused: Boolean,
    onValueChange: (String) -> Unit,
    innerTextField: @Composable () -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (isFocused) hierarchy.secondary.primary else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(text = placeholder, color = TextMuted, fontSize = 16.sp)
            }
            innerTextField()
        }

        TextFieldTrailing(trailingIcon, onTrailingIconClick, value, enabled, onValueChange)
    }
}

@Composable
fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = fieldBorderColor(isError, isFocused)
    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.3f else 0f,
        animationSpec = tween(200),
        label = "glow"
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FieldLabel(label, isError)

        Box {
            TextFieldGlow(isError, glowAlpha)

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FieldShape)
                    .background(GlassSurface)
                    .border(width = 1.dp, color = borderColor, shape = FieldShape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                enabled = enabled,
                singleLine = singleLine,
                textStyle = TextStyle(
                    color = if (enabled) TextPrimary else TextMuted,
                    fontSize = 16.sp
                ),
                cursorBrush = SolidColor(hierarchy.secondary.primary),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    TextFieldDecoration(
                        value = value,
                        placeholder = placeholder,
                        leadingIcon = leadingIcon,
                        trailingIcon = trailingIcon,
                        onTrailingIconClick = onTrailingIconClick,
                        enabled = enabled,
                        isFocused = isFocused,
                        onValueChange = onValueChange,
                        innerTextField = innerTextField
                    )
                }
            )
        }

        FieldErrorMessage(isError, errorMessage)
    }
}

@Composable
private fun PasswordFieldDecoration(
    value: String,
    placeholder: String,
    passwordVisible: Boolean,
    onToggleVisibility: () -> Unit,
    innerTextField: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(text = placeholder, color = TextMuted, fontSize = 16.sp)
            }
            innerTextField()
        }

        IconButton(
            onClick = onToggleVisibility,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun PremiumPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Password",
    placeholder: String = "Enter password",
    isError: Boolean = false,
    errorMessage: String = ""
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    var passwordVisible by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = fieldBorderColor(isError, isFocused)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FieldLabel(label, isError)

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(FieldShape)
                .background(GlassSurface)
                .border(width = 1.dp, color = borderColor, shape = FieldShape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            singleLine = true,
            textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
            cursorBrush = SolidColor(hierarchy.secondary.primary),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                PasswordFieldDecoration(
                    value = value,
                    placeholder = placeholder,
                    passwordVisible = passwordVisible,
                    onToggleVisibility = { passwordVisible = !passwordVisible },
                    innerTextField = innerTextField
                )
            }
        )

        FieldErrorMessage(isError, errorMessage)
    }
}

@Composable
private fun SwitchThumb(checked: Boolean, thumbOffset: Float) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val brush = if (checked) {
        Brush.linearGradient(listOf(hierarchy.secondary.primary, hierarchy.secondary.secondary))
    } else {
        Brush.linearGradient(listOf(TextPrimary, TextSecondary))
    }
    Box(
        modifier = Modifier
            .offset(x = (24 * thumbOffset).dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(brush = brush),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = BackgroundBase,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun PremiumSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "thumb"
    )

    val trackColor by animateColorAsState(
        targetValue = if (checked) hierarchy.secondary.primary else TextMuted,
        animationSpec = tween(200),
        label = "track"
    )

    Box(
        modifier = modifier
            .width(52.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (checked) hierarchy.secondary.primary.copy(alpha = 0.3f) else GlassSurface
            )
            .border(width = 1.dp, color = trackColor, shape = RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        SwitchThumb(checked, thumbOffset)
    }
}

@Composable
fun PremiumCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val scale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (checked) hierarchy.secondary.primary else TextMuted,
        animationSpec = tween(200),
        label = "border"
    )

    Row(
        modifier = modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (checked) hierarchy.secondary.primary.copy(alpha = 0.2f) else Color.Transparent
                )
                .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = hierarchy.secondary.primary,
                modifier = Modifier
                    .size(16.dp)
                    .scale(scale)
            )
        }

        OptionLabel(label, enabled)
    }
}

@Composable
private fun OptionLabel(label: String, enabled: Boolean) {
    if (label.isEmpty()) return
    Text(
        text = label,
        color = if (enabled) TextPrimary else TextMuted,
        fontSize = 14.sp
    )
}

@Composable
fun PremiumRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val innerScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "inner"
    )

    val borderColor by animateColorAsState(
        targetValue = if (selected) hierarchy.secondary.primary else TextMuted,
        animationSpec = tween(200),
        label = "border"
    )

    Row(
        modifier = modifier.clickable(enabled = enabled) { onClick() },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .border(width = 2.dp, color = borderColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .scale(innerScale)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(hierarchy.secondary.secondary, hierarchy.secondary.primary)
                        )
                    )
            )
        }

        OptionLabel(label, enabled)
    }
}

@Composable
private fun SliderThumb(normalizedValue: Float) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Box(
        modifier = Modifier
            .offset(x = ((normalizedValue * 100).toInt() - 12).dp)
            .size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .blur(8.dp)
                .alpha(0.5f)
                .background(hierarchy.secondary.primary, CircleShape)
        )

        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(hierarchy.secondary.secondary, hierarchy.secondary.primary)
                    )
                )
                .border(width = 2.dp, color = hierarchy.secondary.primary, shape = CircleShape)
        )
    }
}

@Composable
private fun SliderTrack(normalizedValue: Float) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(hierarchy.secondary.primary.copy(alpha = 0.2f))
    )

    Box(
        modifier = Modifier
            .fillMaxWidth(normalizedValue)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(hierarchy.secondary.primary, hierarchy.secondary.primary)
                )
            )
    )
}

@Composable
fun PremiumSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true
) {
    val normalizedValue = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            SliderTrack(normalizedValue)
            SliderThumb(normalizedValue)
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(0f)
                .height(24.dp)
        )
    }
}

@Composable
private fun <T> DropdownAnchor(
    selectedItem: T,
    itemLabel: (T) -> String,
    enabled: Boolean,
    expanded: Boolean,
    rotation: Float,
    onExpand: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FieldShape)
            .background(GlassSurface)
            .border(
                width = 1.dp,
                color = if (expanded) GoldPrimary else GoldPrimary.copy(alpha = 0.3f),
                shape = FieldShape
            )
            .clickable(enabled = enabled) { onExpand() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = itemLabel(selectedItem),
            color = if (enabled) TextPrimary else TextMuted,
            fontSize = 16.sp
        )

        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = GoldPrimary,
            modifier = Modifier.rotate(rotation)
        )
    }
}

@Composable
private fun <T> DropdownItems(
    items: List<T>,
    selectedItem: T,
    itemLabel: (T) -> String,
    expanded: Boolean,
    onItemSelected: (T) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .background(GlassSurface)
            .border(width = 1.dp, color = GoldPrimary.copy(alpha = 0.3f), shape = FieldShape)
    ) {
        items.forEach { item ->
            val isSelected = item == selectedItem
            DropdownMenuItem(
                text = {
                    Text(
                        text = itemLabel(item),
                        color = if (isSelected) GoldPrimary else TextPrimary
                    )
                },
                onClick = {
                    onItemSelected(item)
                    onDismiss()
                },
                modifier = Modifier.background(
                    if (isSelected) GoldPrimary.copy(alpha = 0.1f) else Color.Transparent
                )
            )
        }
    }
}

@Composable
fun <T> PremiumDropdown(
    selectedItem: T,
    items: List<T>,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    itemLabel: (T) -> String = { it.toString() },
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "arrow"
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FieldLabel(label, isError = false)

        Box {
            DropdownAnchor(
                selectedItem = selectedItem,
                itemLabel = itemLabel,
                enabled = enabled,
                expanded = expanded,
                rotation = rotation,
                onExpand = { expanded = true }
            )

            DropdownItems(
                items = items,
                selectedItem = selectedItem,
                itemLabel = itemLabel,
                expanded = expanded,
                onItemSelected = onItemSelected,
                onDismiss = { expanded = false }
            )
        }
    }
}

@Composable
fun InputSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Row(
        modifier = modifier
            .clip(FieldShape)
            .background(GlassSurface)
            .border(
                width = 1.dp,
                color = hierarchy.secondary.primary.copy(alpha = 0.3f),
                shape = FieldShape
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, option ->
            SegmentedOption(
                option = option,
                isSelected = index == selectedIndex,
                onClick = { onOptionSelected(index) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SegmentedOption(
    option: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) GoldPrimary.copy(alpha = 0.3f) else Color.Transparent,
        animationSpec = tween(200),
        label = "bg"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = option,
            color = if (isSelected) GoldPrimary else TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
