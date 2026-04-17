package com.tanush.bouncer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanush.bouncer.models.CricketMatch
import com.tanush.bouncer.ui.components.EmptyStateView
import com.tanush.bouncer.ui.components.PremiumMatchListCard
import com.tanush.bouncer.ui.components.core.MatchCardSkeleton
import com.tanush.bouncer.ui.components.glass.GlassCard
import com.tanush.bouncer.ui.components.glass.GlassBloomBackground
import com.tanush.bouncer.ui.theme.*
import com.tanush.bouncer.ui.theme.BouncerDesignSystem
import com.tanush.bouncer.viewmodels.LoadingState
import com.tanush.bouncer.viewmodels.LiveActivityViewModel
import com.tanush.bouncer.ui.theme.GoldPrimary

private val ALL_MATCH_STATUS_FILTERS = listOf("Live", "Upcoming", "Recent")
private val ALL_MATCH_FORMAT_FILTERS = listOf("All", "T20", "ODI", "Test")

// =============================================================================
// Private composables for AllMatchesScreen premium design
// =============================================================================

/**
 * Gold gradient divider between sections — matches the mockup's etched gold line.
 */
@Composable
private fun GoldGradientDivider(
    modifier: Modifier = Modifier
) {
    val colors = BouncerDesignSystem.colors
    val hierarchy = BouncerDesignSystem.hierarchy
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BouncerDesignSystem.dimens.spacing.xl)
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    0.3f to hierarchy.secondary.primary.copy(alpha = 0.4f),
                    0.5f to hierarchy.secondary.primary.copy(alpha = 0.6f),
                    0.7f to hierarchy.secondary.primary.copy(alpha = 0.4f),
                    1.0f to Color.Transparent
                )
            )
    )
}

/**
 * Section header with etched gold gradient text — "Live Now", "Upcoming", "Recent".
 * Matches mockup's Real-Time / Upcoming section headers with gold accent.
 */
@Composable
private fun SectionHeader(
    title: String,
    showLiveDot: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BouncerDesignSystem.dimens.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BouncerDesignSystem.dimens.spacing.sm)
    ) {
        if (showLiveDot) {
            val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.statusLive.copy(alpha = pulseAlpha))
            )
        }
        Text(
            text = title.uppercase(),
            color = hierarchy.secondary.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.etchedText()
        )
    }
}

/**
 * Glass pill filter chip matching the mockup's glass-pill design.
 * Active: gold background with black text.
 * Inactive: glass surface with muted white text.
 */
@Composable
private fun GlassFilterPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = BouncerDesignSystem.colors
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) colors.goldPrimary else colors.glassSurface,
        animationSpec = tween(250),
        label = "pillBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) colors.backgroundBase else colors.textSecondary,
        animationSpec = tween(250),
        label = "pillText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colors.goldPrimary.copy(alpha = 0.6f) else colors.glassBorder,
        animationSpec = tween(250),
        label = "pillBorder"
    )

    Box(
        modifier = Modifier
            .heightIn(min = BouncerDesignSystem.dimens.minTouchTarget)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .semantics {
                role = Role.Button
                selected = isSelected
                contentDescription = "Filter by $text"
            }
            .padding(horizontal = BouncerDesignSystem.dimens.spacing.md),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Glass search bar with gold accent on focus.
 */
@Composable
private fun GlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hierarchy = BouncerDesignSystem.hierarchy
    val colors = BouncerDesignSystem.colors
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) hierarchy.secondary.primary.copy(alpha = 0.6f) else colors.glassBorder,
        animationSpec = tween(250),
        label = "searchBorder"
    )

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = TextStyle(
            color = colors.textPrimary,
            fontSize = 14.sp
        ),
        cursorBrush = SolidColor(hierarchy.secondary.primary),
        modifier = modifier
            .fillMaxWidth()
            .height(BouncerDesignSystem.dimens.minTouchTarget)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.glassSurface)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .onFocusChanged { isFocused = it.isFocused },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BouncerDesignSystem.dimens.spacing.sm)
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (isFocused) hierarchy.secondary.primary else colors.textTertiary,
                    modifier = Modifier.size(20.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search matches...",
                            color = colors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(BouncerDesignSystem.dimens.minTouchTarget)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear search query",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    )
}

// =============================================================================
// AllMatchesScreen — Premium Futuristic Glass Design
// =============================================================================

/**
 * AllMatchesScreen - Full list of all cricket matches with filtering.
 * Premium futuristic glass design with gold accents, stagger animations,
 * and section-based layout matching Analyst Feed mockup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllMatchesScreen(
    viewModel: LiveActivityViewModel = viewModel(),
    onBackClick: () -> Unit,
    onMatchClick: (CricketMatch) -> Unit = {}
) {
    val colors = BouncerDesignSystem.colors
    val typography = BouncerDesignSystem.typography
    val dimens = BouncerDesignSystem.dimens
    val selectedStatus by viewModel.selectedStatus.collectAsState()
    val currentMatches by viewModel.currentMatches.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadingState by viewModel.loadingState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf("All") }
    val normalizedQuery by remember(searchQuery) { derivedStateOf { searchQuery.trim() } }

    // Filter matches by search query and format
    val filteredMatches = remember(currentMatches, normalizedQuery, selectedFormat) {
        currentMatches.filter { match ->
            val matchesSearch = normalizedQuery.isBlank() ||
                match.matchTitle.contains(normalizedQuery, ignoreCase = true) ||
                match.teams.any { it.name.contains(normalizedQuery, ignoreCase = true) }
            val matchesFormat = selectedFormat == "All" ||
                match.matchTitle.contains(selectedFormat, ignoreCase = true) ||
                match.matchType.contains(selectedFormat, ignoreCase = true)
            matchesSearch && matchesFormat
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(dimens.xxs)) {
                        Text(
                            text = "All Matches",
                            color = GoldPrimary,
                            style = typography.headingLarge
                        )
                        Text(
                            text = "${filteredMatches.size} results • ${selectedStatus.lowercase().replaceFirstChar { it.uppercase() }}",
                            color = colors.textSecondary,
                            style = typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(start = dimens.sm)
                            .clip(CircleShape)
                            .background(colors.glassSurface)
                            .border(1.dp, GoldPrimary.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = GoldPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshMatches() },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.glassSurface)
                            .border(1.dp, GoldPrimary.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = GoldPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.backgroundBase.copy(alpha = 0.95f)
                )
            )
        }
    ) { padding ->
        GlassBloomBackground(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .drawBehind {
                    // Gold bloom effect at the top — matches mockup's bg-bloom
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                GoldPrimary.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(size.width / 2f, 0f),
                            radius = size.width * 0.8f
                        ),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.width * 0.8f
                    )
                },
            contentPadding = PaddingValues(vertical = dimens.md),
            verticalArrangement = Arrangement.spacedBy(dimens.cardGap)
        ) {
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.screenHorizontalPadding),
                    contentPadding = PaddingValues(dimens.md)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(dimens.md)) {
                        GlassSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it }
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.sm)) {
                            items(ALL_MATCH_STATUS_FILTERS, key = { it }) { status ->
                                GlassFilterPill(
                                    text = status,
                                    isSelected = selectedStatus == status,
                                    onClick = { viewModel.setAndFetchMatches(status) }
                                )
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(dimens.sm)) {
                            items(ALL_MATCH_FORMAT_FILTERS, key = { it }) { format ->
                                GlassFilterPill(
                                    text = format,
                                    isSelected = selectedFormat == format,
                                    onClick = { selectedFormat = format }
                                )
                            }
                        }

                        GoldGradientDivider()
                        SectionHeader(
                            title = when (selectedStatus) {
                                "Live" -> "Live Now"
                                "Upcoming" -> "Upcoming"
                                else -> "Recent"
                            },
                            showLiveDot = selectedStatus == "Live"
                        )
                    }
                }
            }

            // Match list with stagger animation
            if (isLoading && filteredMatches.isEmpty()) {
                items(5) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = dimens.screenHorizontalPadding)
                            .staggeredFadeIn(index = index)
                    ) {
                        MatchCardSkeleton()
                    }
                }
            } else if (loadingState is LoadingState.Error && filteredMatches.isEmpty()) {
                item {
                    EmptyStateView(
                        title = "Couldn't load matches",
                        message = (loadingState as LoadingState.Error).message,
                        actionLabel = "Retry",
                        onAction = { viewModel.refreshMatches() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.screenHorizontalPadding, vertical = dimens.xxl)
                    )
                }
            } else if (filteredMatches.isEmpty()) {
                item {
                    EmptyStateView(
                        title = "No matches found",
                        message = if (searchQuery.isNotBlank())
                            "No matches match \"$searchQuery\". Try a different search."
                        else
                            "No $selectedStatus ${if (selectedFormat != "All") selectedFormat else ""} matches available. Pull down to refresh or try a different filter.",
                        actionLabel = "Refresh",
                        onAction = { viewModel.refreshMatches() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.screenHorizontalPadding, vertical = dimens.xxl)
                    )
                }
            } else {
                itemsIndexed(filteredMatches, key = { _, match -> match.id }) { index, match ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = dimens.screenHorizontalPadding)
                            .staggeredFadeIn(index = index)
                    ) {
                        PremiumMatchListCard(
                            match = match,
                            onClick = { onMatchClick(match) },
                            modifier = Modifier
                        )
                    }
                }
            }

            // Gold gradient divider at bottom
            item {
                GoldGradientDivider(modifier = Modifier.padding(vertical = dimens.sm))
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
        }
    }
}
