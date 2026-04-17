package com.tanush.bouncer.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.GoldPrimary

/**
 * Premium search bar with glass morphism effect and animations
 */
@Composable
fun PremiumSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search matches, teams...",
    onSearch: (String) -> Unit = {},
    onClear: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    
    val infiniteTransition = rememberInfiniteTransition(label = "searchShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    val focusScale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "focusScale"
    )
    
    val borderAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0.3f,
        animationSpec = tween(300),
        label = "borderAlpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = focusScale
                scaleY = focusScale
            }
            .shadow(
                elevation = if (isFocused) 16.dp else 8.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = if (isFocused) GoldPrimary.copy(alpha = 0.25f) else Color.Transparent
            ),
        shape = RoundedCornerShape(20.dp),
        color = GlassSurface.copy(alpha = 0.9f),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    GoldPrimary.copy(alpha = borderAlpha),
                    GoldPrimary.copy(alpha = borderAlpha * 0.5f),
                    GoldPrimary.copy(alpha = borderAlpha * 0.3f)
                )
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Animated search icon
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        rotationZ = if (isFocused) 10f else 0f
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (isFocused) GoldPrimary else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Text field
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onSearch(query)
                            keyboardController?.hide()
                        }
                    ),
                    interactionSource = interactionSource,
                    cursorBrush = SolidColor(GoldPrimary)
                )
                
                // Placeholder
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = TextTertiary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
            
            // Clear button
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            GlassBorder.copy(alpha = 0.3f),
                            CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, radius = 16.dp),
                            onClick = {
                                onClear()
                                onQueryChange("")
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Expandable search bar that expands from an icon
 */
@Composable
fun ExpandableSearchBar(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search...",
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val transition = updateTransition(targetState = expanded, label = "searchExpand")
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    
    val width by transition.animateDp(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        },
        label = "width"
    ) { isExpanded -> if (isExpanded) 300.dp else 48.dp }
    
    val cornerRadius by transition.animateDp(
        label = "cornerRadius"
    ) { isExpanded -> if (isExpanded) 20.dp else 24.dp }
    
    val iconRotation by transition.animateFloat(
        label = "iconRotation"
    ) { isExpanded -> if (isExpanded) 90f else 0f }

    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
        } else {
            keyboardController?.hide()
        }
    }

    Surface(
        modifier = modifier
            .width(width)
            .height(48.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(cornerRadius),
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(cornerRadius),
        color = GlassSurface,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    hierarchy.secondary.primary.copy(alpha = 0.4f),
                    GlassBorder.copy(alpha = 0.3f)
                )
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (expanded) 16.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search/Close icon
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = iconRotation }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onExpandedChange(!expanded) }
                    )
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (expanded) "Close" else "Search",
                    tint = hierarchy.secondary.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Animated text field
            if (expanded) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(hierarchy.secondary.primary),
                    decorationBox = { innerTextField ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    color = TextTertiary,
                                    fontSize = 15.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }
}

/**
 * Premium filter chip with animation
 */
@Composable
fun PremiumFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "chipScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) hierarchy.secondary.primary.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(200),
        label = "chipBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (selected) hierarchy.secondary.primary else GlassBorder.copy(alpha = 0.4f),
        animationSpec = tween(200),
        label = "chipBorder"
    )
    
    val textColor by animateColorAsState(
        targetValue = if (selected) hierarchy.secondary.secondary else TextSecondary,
        animationSpec = tween(200),
        label = "chipText"
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = hierarchy.secondary.primary.copy(alpha = 0.1f)),
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (leadingIcon != null) {
                Box(modifier = Modifier.size(18.dp)) {
                    leadingIcon()
                }
            }
            
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = textColor,
                letterSpacing = 0.3.sp
            )
            
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = hierarchy.secondary.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Recent searches section
 */
@Composable
fun RecentSearchesSection(
    searches: List<String>,
    onSearchClick: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    if (searches.isEmpty()) return
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RECENT SEARCHES",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.5.sp
            )
            
            Text(
                text = "Clear All",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = hierarchy.secondary.primary,
                modifier = Modifier.clickable(onClick = onClearAll)
            )
        }
        
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            searches.take(5).forEach { search ->
                RecentSearchItem(
                    query = search,
                    onClick = { onSearchClick(search) }
                )
            }
        }
    }
}

@Composable
private fun RecentSearchItem(
    query: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(18.dp)
        )
        
        Text(
            text = query,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        
        Icon(
            imageVector = Icons.Default.NorthWest,
            contentDescription = "Search",
            tint = TextTertiary,
            modifier = Modifier.size(14.dp)
        )
    }
}

/**
 * Search suggestions dropdown
 */
@Composable
fun SearchSuggestions(
    suggestions: List<SearchSuggestion>,
    onSuggestionClick: (SearchSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    if (suggestions.isEmpty()) return
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = hierarchy.secondary.primary.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(16.dp),
        color = GlassSurface,
        border = BorderStroke(1.dp, GlassBorder.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            suggestions.forEachIndexed { index, suggestion ->
                SuggestionItem(
                    suggestion = suggestion,
                    onClick = { onSuggestionClick(suggestion) }
                )
                
                if (index < suggestions.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = GlassBorder.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

data class SearchSuggestion(
    val text: String,
    val type: SuggestionType,
    val imageUrl: String? = null
)

enum class SuggestionType {
    TEAM, MATCH, PLAYER, VENUE
}

@Composable
private fun SuggestionItem(
    suggestion: SearchSuggestion,
    onClick: () -> Unit
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Type icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    when (suggestion.type) {
                        SuggestionType.TEAM -> hierarchy.secondary.primary.copy(alpha = 0.15f)
                        SuggestionType.MATCH -> ChartGreen.copy(alpha = 0.15f)
                        SuggestionType.PLAYER -> StatusInfo.copy(alpha = 0.15f)
                        SuggestionType.VENUE -> ChartPurple.copy(alpha = 0.15f)
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (suggestion.type) {
                    SuggestionType.TEAM -> Icons.Default.Groups
                    SuggestionType.MATCH -> Icons.Default.SportsCricket
                    SuggestionType.PLAYER -> Icons.Default.Person
                    SuggestionType.VENUE -> Icons.Default.Stadium
                },
                contentDescription = null,
                tint = when (suggestion.type) {
                    SuggestionType.TEAM -> hierarchy.secondary.primary
                    SuggestionType.MATCH -> ChartGreen
                    SuggestionType.PLAYER -> StatusInfo
                    SuggestionType.VENUE -> ChartPurple
                },
                modifier = Modifier.size(18.dp)
            )
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = suggestion.type.name.lowercase().replaceFirstChar { it.uppercase() },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextTertiary
            )
        }
        
        Icon(
            imageVector = Icons.Default.NorthEast,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(16.dp)
        )
    }
}
